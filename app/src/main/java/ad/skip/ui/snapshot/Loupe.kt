package ad.skip.ui.snapshot

import ad.skip.db.SnapshotNode
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SweepGradient
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.min

fun drawAnimatedHighlightBorder(
    canvas: Canvas,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    strokeWidth: Float,
    rotationDegrees: Float
) {
    if (right <= left || bottom <= top) return
    val centerX = (left + right) / 2f
    val centerY = (top + bottom) / 2f
    val shader = SweepGradient(
        centerX,
        centerY,
        animatedHighlightColors(),
        null
    ).apply {
        val matrix = Matrix().apply {
            postRotate(rotationDegrees, centerX, centerY)
        }
        setLocalMatrix(matrix)
    }
    val paint = Paint().apply {
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
        isAntiAlias = true
        this.shader = shader
    }
    canvas.drawRect(left, top, right, bottom, paint)
}

private fun drawAnimatedTouchCrosshair(
    canvas: Canvas,
    centerX: Float,
    centerY: Float,
    rotationDegrees: Float
) {
    val hue = ((rotationDegrees % 360f) + 360f) % 360f
    val outlinePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        color = android.graphics.Color.WHITE
    }
    val crosshairPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeCap = Paint.Cap.ROUND
        color = android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.9f, 1f))
    }
    val armLength = 11f
    val centerGap = 3.5f

    fun drawCrosshairLine(startX: Float, startY: Float, endX: Float, endY: Float) {
        canvas.drawLine(startX, startY, endX, endY, outlinePaint)
        canvas.drawLine(startX, startY, endX, endY, crosshairPaint)
    }

    drawCrosshairLine(
        centerX - armLength,
        centerY,
        centerX - centerGap,
        centerY
    )
    drawCrosshairLine(
        centerX + centerGap,
        centerY,
        centerX + armLength,
        centerY
    )
    drawCrosshairLine(
        centerX,
        centerY - armLength,
        centerX,
        centerY - centerGap
    )
    drawCrosshairLine(
        centerX,
        centerY + centerGap,
        centerX,
        centerY + armLength
    )
}

@Composable
fun SnapshotLoupe(
    modifier: Modifier = Modifier,
    bitmap: Bitmap,
    selectedNode: SnapshotNode?,
    loupeTouch: Offset?,
    loupeSource: Offset?,
    sourceW: Float,
    sourceH: Float,
    loupeRadiusPx: Float,
    loupeSpacingPx: Float,
    loupeZoom: Float,
    rotationDegrees: Float
) {
    if (loupeTouch == null || loupeSource == null) return

    Canvas(modifier = modifier) {
        val imgW = bitmap.width.toFloat()
        val imgH = bitmap.height.toFloat()
        val scale = min(size.width / imgW, size.height / imgH)
        val bitmapX = (loupeSource.x / sourceW) * imgW
        val bitmapY = (loupeSource.y / sourceH) * imgH
        val sourceHalfSpan = min(loupeRadiusPx / (scale * loupeZoom), min(imgW, imgH) / 2f)
        val sourceRectLeft = (bitmapX - sourceHalfSpan).coerceIn(0f, imgW - (sourceHalfSpan * 2f))
        val sourceRectTop = (bitmapY - sourceHalfSpan).coerceIn(0f, imgH - (sourceHalfSpan * 2f))
        val sourceRectRight = sourceRectLeft + (sourceHalfSpan * 2f)
        val sourceRectBottom = sourceRectTop + (sourceHalfSpan * 2f)

        // The loupe stays above-right of the touch point. Near the top edge we keep that
        // horizontal placement and only clamp the vertical position enough to stay on-screen.
        val preferredAboveCenter = Offset(
            x = loupeTouch.x + loupeRadiusPx + loupeSpacingPx,
            y = loupeTouch.y - loupeRadiusPx - loupeSpacingPx
        )
        val loupeCenter = preferredAboveCenter.copy(
            y = preferredAboveCenter.y.coerceAtLeast(loupeRadiusPx)
        )
        val destRect = RectF(
            loupeCenter.x - loupeRadiusPx,
            loupeCenter.y - loupeRadiusPx,
            loupeCenter.x + loupeRadiusPx,
            loupeCenter.y + loupeRadiusPx
        )
        val loupePath = Path().apply {
            addCircle(
                loupeCenter.x,
                loupeCenter.y,
                loupeRadiusPx,
                Path.Direction.CW
            )
        }
        val loupePaint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }

        drawContext.canvas.nativeCanvas.save()
        drawContext.canvas.nativeCanvas.clipPath(loupePath)
        drawContext.canvas.nativeCanvas.drawColor(android.graphics.Color.WHITE)
        drawContext.canvas.nativeCanvas.drawBitmap(
            bitmap,
            Rect(
                sourceRectLeft.toInt(),
                sourceRectTop.toInt(),
                sourceRectRight.toInt(),
                sourceRectBottom.toInt()
            ),
            destRect,
            loupePaint
        )
        val sourceRectWidth = sourceRectRight - sourceRectLeft
        val sourceRectHeight = sourceRectBottom - sourceRectTop
        selectedNode?.let { node ->
            val b = node.bounds
            val nodeBitmapLeft = (b.left / sourceW) * imgW
            val nodeBitmapTop = (b.top / sourceH) * imgH
            val nodeBitmapRight = (b.right / sourceW) * imgW
            val nodeBitmapBottom = (b.bottom / sourceH) * imgH
            if (
                nodeBitmapRight > sourceRectLeft &&
                nodeBitmapLeft < sourceRectRight &&
                nodeBitmapBottom > sourceRectTop &&
                nodeBitmapTop < sourceRectBottom &&
                sourceRectWidth > 0f &&
                sourceRectHeight > 0f
            ) {
                val highlightLeft = destRect.left +
                    ((nodeBitmapLeft - sourceRectLeft) / sourceRectWidth) * destRect.width()
                val highlightTop = destRect.top +
                    ((nodeBitmapTop - sourceRectTop) / sourceRectHeight) * destRect.height()
                val highlightRight = destRect.left +
                    ((nodeBitmapRight - sourceRectLeft) / sourceRectWidth) * destRect.width()
                val highlightBottom = destRect.top +
                    ((nodeBitmapBottom - sourceRectTop) / sourceRectHeight) * destRect.height()
                drawAnimatedHighlightBorder(
                    canvas = drawContext.canvas.nativeCanvas,
                    left = highlightLeft,
                    top = highlightTop,
                    right = highlightRight,
                    bottom = highlightBottom,
                    strokeWidth = 5f,
                    rotationDegrees = rotationDegrees
                )
            }
        }
        if (sourceRectWidth > 0f && sourceRectHeight > 0f) {
            val touchCrosshairX = destRect.left +
                ((bitmapX - sourceRectLeft) / sourceRectWidth) * destRect.width()
            val touchCrosshairY = destRect.top +
                ((bitmapY - sourceRectTop) / sourceRectHeight) * destRect.height()
            drawAnimatedTouchCrosshair(
                canvas = drawContext.canvas.nativeCanvas,
                centerX = touchCrosshairX,
                centerY = touchCrosshairY,
                rotationDegrees = rotationDegrees
            )
        }
        drawContext.canvas.nativeCanvas.restore()

        drawCircle(
            color = Color.White.copy(alpha = 0.95f),
            radius = loupeRadiusPx,
            center = loupeCenter,
            style = Stroke(width = 6f)
        )
        drawCircle(
            color = Color.Red.copy(alpha = 0.9f),
            radius = 8f,
            center = loupeTouch,
            style = Stroke(width = 2f)
        )
    }
}

fun animatedHighlightColors(): IntArray {
    return IntArray(7) { index ->
        val hue = (index * 60f) % 360f
        android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.9f, 1f))
    }.also { colors ->
        colors[colors.lastIndex] = colors[0]
    }
}
