package ad.skip.util

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.Serializable

enum class ActionType {
    Click,
    Swipe
}
interface IAction {
    fun exec(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean
}

fun IAction.serialize(): String {
    return json.encodeToString(PolymorphicSerializer(IAction::class), this)
}

// Generate a *concrete* IAction from json string.
fun String.parseActon(): IAction {
    return json.decodeFromString(PolymorphicSerializer(IAction::class), this)
}


@Serializable
class Click: IAction {
    override fun exec(service: AccessibilityService, node: AccessibilityNodeInfo) : Boolean {
        return performClick(node)
                || performTap(service, node)
    }
    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        val clickableTarget = findClickableTarget(node)
        return clickableTarget != null && clickableTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
    private fun performTap(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        val targetNode = findClickableTarget(node) ?: node
        val bounds = Rect()
        targetNode.getBoundsInScreen(bounds)
        if (bounds.isEmpty) return false
        val x = bounds.exactCenterX()
        val y = bounds.exactCenterY()
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 80L))
            .build()
        return service.dispatchGesture(gesture, null, null)
    }
    private fun findClickableTarget(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isVisibleToUser && current.isEnabled && current.isClickable) {
                return current
            }
            current = current.parent
        }
        return null
    }
}

@Serializable
class Swipe(
    val startXRatio: Float,
    val startYRatio: Float,
    val endXRatio: Float,
    val endYRatio: Float,
    val durationMs: Long
): IAction {
    override fun exec(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        val width = service.resources.displayMetrics.widthPixels.toFloat()
        val height = service.resources.displayMetrics.heightPixels.toFloat()

        val startX = (startXRatio * width).coerceIn(0f, width)
        val startY = (startYRatio * height).coerceIn(0f, height)
        val endX = (endXRatio * width).coerceIn(0f, width)
        val endY = (endYRatio * height).coerceIn(0f, height)

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
            .build()
        return service.dispatchGesture(gesture, null, null)
    }
}


