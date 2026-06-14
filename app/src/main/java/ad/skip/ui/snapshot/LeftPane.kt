package ad.skip.ui.snapshot

import ad.skip.db.SnapshotNode
import ad.skip.util.ActionType
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun LeftPane(
    modifier: Modifier = Modifier,
    bitmap: Bitmap,
    flatNodes: List<SnapshotNode>,
    selectedNode: MutableState<SnapshotNode?>,
    selectedActionType: MutableState<ActionType>,
    saveRuleDialogVisible: MutableState<Boolean>,
    swipePickInProgress: MutableState<Boolean>,
    swipeStartDraft: MutableState<String>,
    swipeEndDraft: MutableState<String>,
    snapshotCoordinateSpace: SnapshotCoordinateSpace?,
    magnifierCanvasOffset: MutableState<Offset?>,
    magnifierSourceOffset: MutableState<Offset?>,
    highlightRotation: Float
) {
    val loupeRadiusPx = with(LocalDensity.current) { 72.dp.toPx() }
    val loupeSpacingPx = with(LocalDensity.current) { 20.dp.toPx() }
    val loupeZoom = 2.5f

    Box(modifier = modifier) {
        val imgW = bitmap.width.toFloat()
        val imgH = bitmap.height.toFloat()
        val sourceW = snapshotCoordinateSpace?.width?.toFloat() ?: imgW
        val sourceH = snapshotCoordinateSpace?.height?.toFloat() ?: imgH

        fun mapCanvasToSource(
            canvasSize: Size,
            offset: Offset,
            clampToImage: Boolean = false
        ): Offset? {
            // The screenshot may be letterboxed inside the canvas. Convert the visible canvas
            // position back into the snapshot's original coordinate space before hit-testing.
            val scale = min(canvasSize.width / imgW, canvasSize.height / imgH)
            val drawnWidth = imgW * scale
            val drawnHeight = imgH * scale
            val offsetX = (canvasSize.width - drawnWidth) / 2f
            val offsetY = (canvasSize.height - drawnHeight) / 2f
            val imageLeft = offsetX
            val imageTop = offsetY
            val imageRight = offsetX + drawnWidth
            val imageBottom = offsetY + drawnHeight
            val mappedOffset = if (clampToImage) {
                Offset(
                    x = offset.x.coerceIn(imageLeft, imageRight),
                    y = offset.y.coerceIn(imageTop, imageBottom)
                )
            } else {
                if (
                    offset.x !in imageLeft..imageRight ||
                    offset.y < imageTop ||
                    offset.y > imageBottom
                ) {
                    return null
                }
                offset
            }

            val x = ((mappedOffset.x - offsetX) / drawnWidth) * sourceW
            val y = ((mappedOffset.y - offsetY) / drawnHeight) * sourceH
            return Offset(x, y)
        }

        fun findBestNode(sourceOffset: Offset?): SnapshotNode? {
            if (sourceOffset == null || flatNodes.isEmpty()) return null
            val x = sourceOffset.x
            val y = sourceOffset.y
            var bestNode: SnapshotNode? = null
            var bestLayer = Int.MIN_VALUE
            var bestArea = Float.MAX_VALUE
            // Prefer the smallest matching node so deep children win over broad container bounds.
            for (node in flatNodes) {
                val b = node.bounds
                val left = b.left.toFloat()
                val top = b.top.toFloat()
                val right = b.right.toFloat()
                val bottom = b.bottom.toFloat()
                if ((x in left..right) && (y in top..bottom)) {
                    val area = (right - left) * (bottom - top)
                    val layer = node.attributes["windowLayer"]?.toIntOrNull() ?: Int.MIN_VALUE
                    if (
                        area >= 1f &&
                        (layer > bestLayer || (layer == bestLayer && area < bestArea))
                    ) {
                        bestLayer = layer
                        bestArea = area
                        bestNode = node
                    }
                }
            }
            return bestNode
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(bitmap, flatNodes) {
                    detectTapGestures(
                        onTap = { offset ->
                            magnifierCanvasOffset.value = null
                            magnifierSourceOffset.value = null
                            if (swipePickInProgress.value) {
                                return@detectTapGestures
                            }
                            val sourceOffset = mapCanvasToSource(
                                canvasSize = Size(size.width.toFloat(), size.height.toFloat()),
                                offset = offset
                            )
                            selectedNode.value = findBestNode(sourceOffset)
                        }
                    )
                }
                .pointerInput(bitmap, flatNodes, swipePickInProgress.value) {
                    if (swipePickInProgress.value) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val sourceOffset = mapCanvasToSource(
                                    canvasSize = Size(size.width.toFloat(), size.height.toFloat()),
                                    offset = offset,
                                    clampToImage = true
                                ) ?: return@detectDragGestures
                                val pickedX = sourceOffset.x.roundToInt()
                                val pickedY = sourceOffset.y.roundToInt()
                                swipeStartDraft.value = formatSwipePointDraft(pickedX, pickedY)
                                swipeEndDraft.value = formatSwipePointDraft(pickedX, pickedY)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val sourceOffset = mapCanvasToSource(
                                    canvasSize = Size(size.width.toFloat(), size.height.toFloat()),
                                    offset = change.position,
                                    clampToImage = true
                                ) ?: return@detectDragGestures
                                swipeEndDraft.value =
                                    formatSwipePointDraft(
                                        sourceOffset.x.roundToInt(),
                                        sourceOffset.y.roundToInt()
                                    )
                            },
                            onDragEnd = {
                                swipePickInProgress.value = false
                                saveRuleDialogVisible.value = true
                            },
                            onDragCancel = {
                                swipePickInProgress.value = false
                                saveRuleDialogVisible.value = true
                            }
                        )
                    }
                }
                .pointerInput(bitmap, flatNodes) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            val sourceOffset = mapCanvasToSource(
                                canvasSize = Size(size.width.toFloat(), size.height.toFloat()),
                                offset = offset,
                                clampToImage = true
                            )
                            magnifierCanvasOffset.value = offset
                            magnifierSourceOffset.value = sourceOffset
                            selectedNode.value = findBestNode(sourceOffset)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val sourceOffset = mapCanvasToSource(
                                canvasSize = Size(size.width.toFloat(), size.height.toFloat()),
                                offset = change.position,
                                clampToImage = true
                            )
                            magnifierCanvasOffset.value = change.position
                            magnifierSourceOffset.value = sourceOffset
                            selectedNode.value = findBestNode(sourceOffset)
                        },
                        onDragEnd = {
                            magnifierCanvasOffset.value = null
                            magnifierSourceOffset.value = null
                        },
                        onDragCancel = {
                            magnifierCanvasOffset.value = null
                            magnifierSourceOffset.value = null
                        }
                    )
                }
        ) {
            val scale = min(size.width / imgW, size.height / imgH)
            val drawnWidth = imgW * scale
            val drawnHeight = imgH * scale
            val offsetX = (size.width - drawnWidth) / 2f
            val offsetY = (size.height - drawnHeight) / 2f
            drawContext.canvas.nativeCanvas.drawBitmap(
                bitmap,
                null,
                RectF(offsetX, offsetY, offsetX + drawnWidth, offsetY + drawnHeight),
                null
            )

            selectedNode.value?.let { node ->
                val b = node.bounds
                val left = offsetX + ((b.left / sourceW) * drawnWidth)
                val top = offsetY + ((b.top / sourceH) * drawnHeight)
                val right = offsetX + ((b.right / sourceW) * drawnWidth)
                val bottom = offsetY + ((b.bottom / sourceH) * drawnHeight)
                drawAnimatedHighlightBorder(
                    canvas = drawContext.canvas.nativeCanvas,
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom,
                    strokeWidth = 5f,
                    rotationDegrees = highlightRotation
                )
            }

            val swipeStart = parseSwipePointDraft(
                pointText = swipeStartDraft.value,
                coordinateSpace = snapshotCoordinateSpace
            )
            val swipeEnd = parseSwipePointDraft(
                pointText = swipeEndDraft.value,
                coordinateSpace = snapshotCoordinateSpace
            )
            if (
                selectedActionType.value == ActionType.Swipe &&
                (saveRuleDialogVisible.value || swipePickInProgress.value) &&
                (swipeStart != null || swipeEnd != null)
            ) {
                fun sourceToCanvas(point: Offset): Offset =
                    Offset(
                        x = offsetX + ((point.x / sourceW) * drawnWidth),
                        y = offsetY + ((point.y / sourceH) * drawnHeight)
                    )

                val startCanvas = swipeStart?.let(::sourceToCanvas)
                val endCanvas = swipeEnd?.let(::sourceToCanvas)
                if (startCanvas != null && endCanvas != null) {
                    drawSwipeArrow(
                        start = startCanvas,
                        end = endCanvas,
                        strokeWidth = 4.dp.toPx(),
                        arrowHeadLength = 18.dp.toPx(),
                        arrowHeadHalfWidth = 8.dp.toPx(),
                        rotationDegrees = highlightRotation
                    )
                }
                if (startCanvas != null && endCanvas == null) {
                    drawCircle(
                        color = Color(0xFF66BB6A),
                        radius = 8.dp.toPx(),
                        center = startCanvas
                    )
                }
                if (startCanvas == null && endCanvas != null) {
                    drawCircle(
                        color = Color(0xFFEF5350),
                        radius = 8.dp.toPx(),
                        center = endCanvas
                    )
                }
            }
        }

        SnapshotLoupe(
            modifier = Modifier.fillMaxSize(),
            bitmap = bitmap,
            selectedNode = selectedNode.value,
            loupeTouch = magnifierCanvasOffset.value,
            loupeSource = magnifierSourceOffset.value,
            sourceW = sourceW,
            sourceH = sourceH,
            loupeRadiusPx = loupeRadiusPx,
            loupeSpacingPx = loupeSpacingPx,
            loupeZoom = loupeZoom,
            rotationDegrees = highlightRotation
        )
    }
}
