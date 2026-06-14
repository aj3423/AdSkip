package ad.skip.service

import ad.skip.G
import ad.skip.db.History
import ad.skip.db.HistoryTable
import ad.skip.db.Rule
import ad.skip.query.NodeQuery
import ad.skip.util.hasFlag
import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
import android.view.accessibility.AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT
import android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch


class MyAccessibilityService : AccessibilityService() {
    private data class WindowContext(
        val packageName: String,
        val activityName: String?
    )

    private val windowContexts = mutableMapOf<Int, WindowContext>()

    override fun onServiceConnected() {
        super.onServiceConnected()
    }

    private var isVolumeUpPressed = false
    private var isVolumeDownPressed = false
    override fun onKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action

        // log Volume Up+Down status
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                isVolumeUpPressed = (action == KeyEvent.ACTION_DOWN)
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                isVolumeDownPressed = (action == KeyEvent.ACTION_DOWN)
            }
        }

        // Check if BOTH are down
        if (isVolumeUpPressed && isVolumeDownPressed) {
            takeSnapshot()
            return true // Consumes the key event so the volume slider doesn't pop up
        }

        return super.onKeyEvent(event)
    }


    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventPackageName = event.packageName?.toString() ?: return

        // Ignore this app
        if (eventPackageName == packageName) {
            return
        }

        var isInterestedEvent: Boolean

        if (event.eventType == TYPE_WINDOW_STATE_CHANGED) {
            cacheWindowContext(event)
            isInterestedEvent = true
        } else {
            val changes = event.contentChangeTypes
            isInterestedEvent = changes.hasFlag(CONTENT_CHANGE_TYPE_TEXT) || changes.hasFlag(CONTENT_CHANGE_TYPE_SUBTREE)
        }

        if (!isInterestedEvent)
            return

        if (!G.rules.containsKey(eventPackageName)) {
            return
        }

        event.source?.let { source ->
            val sourcePackageName = source.packageName?.toString() ?: return@let
            if (sourcePackageName != eventPackageName) {
                return@let
            }

            CoroutineScope(IO).launch {
                checkRules(
                    packageName = sourcePackageName,
                    activityName = resolveActivityName(event.windowId, sourcePackageName),
                    nodeInfo = source
                )
            }
        }
    }

    private fun cacheWindowContext(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        windowContexts[event.windowId] = WindowContext(
            packageName = packageName,
            activityName = event.className?.toString()?.takeIf(String::isNotBlank)
        )
    }

    private fun resolveActivityName(windowId: Int, packageName: String): String? =
        windowContexts[windowId]
            ?.takeIf { it.packageName == packageName }
            ?.activityName


    private fun checkRules(
        packageName: String,
        activityName: String?,
        nodeInfo: AccessibilityNodeInfo
    ) {

        // filter by package
        G.rules[packageName]
            // filter by activity name
            ?.filter {
//                logd("${it.activityName} <-> $activityName")
                it.activityName == activityName
            }

            // check each rule
            ?.forEach { rule ->
//                logi("check rule")

                val node = rule.parsedQuery?.let { parsedQuery ->
                    NodeQuery.findMatchingNode(nodeInfo, parsedQuery)
                }

                if (node != null) {
                    val succeeded = rule.action.exec(this, node)
//                    logi("----------- rule ${rule.desc} hits")
                    logToHistory(
                        packageName = packageName,
                        rule = rule,
                        succeeded = succeeded,
                    )
                }
            }
    }

    private fun logToHistory(rule: Rule, packageName: String, succeeded: Boolean) {
        HistoryTable.addNew(
            this,
            History(
                packageName = packageName,
                ruleId = rule.id,
                succeeded = succeeded,
                time = System.currentTimeMillis(),
            )
        )
    }

    private fun takeSnapshot() {
        val snapshotContext = resolveCurrentWindowContext()
        if (snapshotContext != null) {
            Dumper.performDump(
                service = this@MyAccessibilityService,
                foregroundPackage = snapshotContext.packageName,
                activityName = snapshotContext.activityName
            )
        }
    }

    private fun resolveCurrentWindowContext(): WindowContext? {
        val activeAppWindow = windows
            .asSequence()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedByDescending { window ->
                when {
                    window.isActive -> 2
                    window.isFocused -> 1
                    else -> 0
                }
            }
            .firstOrNull()

        val root = activeAppWindow?.root ?: rootInActiveWindow ?: return null
        val packageName = root.packageName?.toString() ?: return null

        return windowContexts[activeAppWindow?.id ?: -1]
            ?.takeIf { it.packageName == packageName }
            ?: WindowContext(packageName = packageName, activityName = null)
    }


    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        windowContexts.clear()
        super.onDestroy()
    }
}
