package ad.skip.service

import ad.skip.db.Snapshot
import ad.skip.db.SnapshotNode
import ad.skip.db.ScreenshotStore
import ad.skip.db.SnapshotTable
import ad.skip.util.Util
import ad.skip.util.loge
import ad.skip.util.logi
import ad.skip.util.toLosslessWebpByteArray
import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.view.Display
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast

object Dumper {
    private data class CaptureContext(
        val windowRoots: List<WindowRoot>,
        val packageName: String,
        val activityName: String?
    )

    private data class WindowRoot(
        val windowId: Int,
        val layer: Int,
        val type: Int,
        val isActive: Boolean,
        val isFocused: Boolean,
        val root: AccessibilityNodeInfo
    )

    fun performDump(
        service: AccessibilityService,
        foregroundPackage: String? = null,
        activityName: String? = null,
        onFinished: (() -> Unit)? = null
    ) {
        val captureContext = resolveCaptureContext(
            service = service,
            foregroundPackage = foregroundPackage,
            activityName = activityName
        )
        if (captureContext == null) {
            Toast.makeText(service, "No active app found for snapshot", Toast.LENGTH_LONG).show()
            onFinished?.invoke()
            return
        }
        val time = Util.formatTime(System.currentTimeMillis())
        val desc = "${captureContext.packageName}_$time"


        logi("Taking snapshot ...")

        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        var hardwareBitmap: Bitmap? = null
                        var bitmap: Bitmap? = null
                        try {
                            hardwareBitmap = Bitmap.wrapHardwareBuffer(
                                screenshot.hardwareBuffer,
                                screenshot.colorSpace
                            )
                            bitmap = hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            if (bitmap != null) {
                                val screenshotBytes = bitmap.toLosslessWebpByteArray() ?: ByteArray(0)
                                val screenshotFileName = ScreenshotStore.save(service, screenshotBytes)

                                SnapshotTable.addNew(service, Snapshot(
                                    desc = desc,
                                    packageName = captureContext.packageName,
                                    activityName = captureContext.activityName,
                                    root = captureContext.toSnapshotRoot(
                                        screenWidth = bitmap.width,
                                        screenHeight = bitmap.height
                                    ),
                                    screenWidth = bitmap.width,
                                    screenHeight = bitmap.height,
                                    screenshotFileName = screenshotFileName
                                ))

                                return
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            bitmap?.recycle()
                            hardwareBitmap?.recycle()
                            screenshot.hardwareBuffer.close()
                        }
                        Toast.makeText(service, "Screenshot capture failed", Toast.LENGTH_LONG).show()
                    }

                    override fun onFailure(errorCode: Int) {
                        Toast.makeText(service, "Screenshot capture failed ($errorCode)", Toast.LENGTH_LONG).show()
                    }
                }
            )
        } catch (e: Exception) {
            loge("$e")
            Toast.makeText(service, "Screenshot capture failed: $e", Toast.LENGTH_LONG).show()
        } finally {
            onFinished?.invoke()
        }
    }

    private fun resolveCaptureContext(
        service: AccessibilityService,
        foregroundPackage: String?,
        activityName: String?
    ): CaptureContext? {
        val windowRoots = findApplicationWindowRoots(service)
        val primaryRoot = windowRoots
            .firstOrNull { it.root.packageName?.toString() == foregroundPackage }
            ?: windowRoots.firstOrNull()
            ?: service.rootInActiveWindow?.let { root ->
                WindowRoot(
                    windowId = -1,
                    layer = 0,
                    type = AccessibilityWindowInfo.TYPE_APPLICATION,
                    isActive = true,
                    isFocused = true,
                    root = root
                )
            }
            ?: return null
        val packageName = foregroundPackage
            ?.takeIf(String::isNotBlank)
            ?: primaryRoot.root.packageName?.toString()
            ?: return null

        // TYPE_WINDOW_STATE_CHANGED is still useful for activity names, but only
        // when it agrees with the package currently exposed by the active window.
        val safeActivityName = activityName
            ?.takeIf(String::isNotBlank)
            ?.takeIf { foregroundPackage == packageName }

        return CaptureContext(
            windowRoots = windowRoots.ifEmpty { listOf(primaryRoot) },
            packageName = packageName,
            activityName = safeActivityName
        )
    }

    private fun findApplicationWindowRoots(service: AccessibilityService): List<WindowRoot> =
        service.windows
            .asSequence()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { window ->
                val root = window.root ?: return@mapNotNull null
                WindowRoot(
                    windowId = window.id,
                    layer = window.layer,
                    type = window.type,
                    isActive = window.isActive,
                    isFocused = window.isFocused,
                    root = root
                )
            }
            .sortedBy { it.layer }
            .toList()

    private fun CaptureContext.toSnapshotRoot(
        screenWidth: Int,
        screenHeight: Int
    ): SnapshotNode =
        SnapshotNode(
            bounds = ad.skip.db.SnapshotBounds(
                left = 0,
                top = 0,
                right = screenWidth,
                bottom = screenHeight
            ),
            className = "ad.skip.snapshot.Windows",
            packageName = packageName,
            children = windowRoots.mapNotNull { windowRoot ->
                SnapshotNode.from(windowRoot.root)?.let { rootNode ->
                    val windowAttributes = mapOf(
                        "windowId" to windowRoot.windowId.toString(),
                        "windowLayer" to windowRoot.layer.toString(),
                        "windowType" to windowRoot.type.toString(),
                        "windowActive" to windowRoot.isActive.toString(),
                        "windowFocused" to windowRoot.isFocused.toString()
                    )
                    SnapshotNode(
                        bounds = rootNode.bounds,
                        className = "ad.skip.snapshot.Window",
                        packageName = rootNode.packageName,
                        isFocused = windowRoot.isFocused,
                        attributes = windowAttributes,
                        children = listOf(rootNode.withWindowAttributes(windowAttributes))
                    )
                }
            }
        )

    private fun SnapshotNode.withWindowAttributes(windowAttributes: Map<String, String>): SnapshotNode =
        copy(
            attributes = windowAttributes + attributes,
            children = children.map { it.withWindowAttributes(windowAttributes) }
        )
}
