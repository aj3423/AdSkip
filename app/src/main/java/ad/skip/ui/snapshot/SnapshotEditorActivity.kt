package ad.skip.ui.snapshot

import ad.skip.db.Snapshot
import ad.skip.db.SnapshotNode
import ad.skip.db.ScreenshotStore
import ad.skip.db.SnapshotTable
import ad.skip.util.ActionType
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

class SnapshotEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val snapshotId = intent?.getLongExtra("snapshot_id", 0L)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(),
            ) {
                if (snapshotId != null) {
                    val snapshot = SnapshotTable.findById(this, snapshotId)
                    if (snapshot != null) {
                        val bitmap = try {
                            BitmapFactory.decodeFile(
                                ScreenshotStore.file(
                                    this,
                                    snapshot.screenshotFileName
                                ).absolutePath
                            )
                        } catch (e: Exception) {
                            null
                        }
                        if (bitmap != null) {
                            SnapshotEditorScreen(snapshot = snapshot, bmp = bitmap)
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Failed to decode screenshot")
                            }
                        }
                    } else {
                        Text("Snapshot not found")
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No snapshot selected")
                    }
                }
            }
        }
    }
}


@Composable
fun RuleActionSelector(
    selectedActionType: MutableState<ActionType>
) {
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionType.entries.forEach { actionType ->
            Row(
                modifier = Modifier.selectable(
                    selected = selectedActionType.value == actionType,
                    onClick = { selectedActionType.value = actionType },
                    role = Role.RadioButton
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedActionType.value == actionType,
                    onClick = null
                )
                Text(
                    actionType.name,
                    color = onSurfaceColor
                )
            }
        }
    }
}

@Composable
fun NumberInputField(
    label: String,
    state: MutableState<String>,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = state.value,
        onValueChange = { changed -> state.value = changed.filter(Char::isDigit) },
        modifier = modifier,
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
fun CoordinateInputField(
    label: String,
    state: MutableState<String>,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = state.value,
        onValueChange = { changed -> state.value = changed.filter { it.isDigit() || it in "(), " } },
        modifier = modifier,
        singleLine = true,
        label = { Text(label) },
        placeholder = { Text("(111,222)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
    )
}

fun DrawScope.drawSwipeArrow(
    start: Offset,
    end: Offset,
    strokeWidth: Float,
    arrowHeadLength: Float,
    arrowHeadHalfWidth: Float,
    rotationDegrees: Float
) {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = sqrt((dx * dx) + (dy * dy))
    if (length <= 0.5f) {
        val hue = ((rotationDegrees % 360f) + 360f) % 360f
        drawCircle(
            color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.9f, 1f))),
            radius = strokeWidth * 1.5f,
            center = end
        )
        return
    }

    val unitX = dx / length
    val unitY = dy / length
    val shaftEnd = Offset(
        x = end.x - (unitX * arrowHeadLength * 0.75f),
        y = end.y - (unitY * arrowHeadLength * 0.75f)
    )
    val perpX = -unitY
    val perpY = unitX
    val arrowBase = Offset(
        x = end.x - (unitX * arrowHeadLength),
        y = end.y - (unitY * arrowHeadLength)
    )
    val leftHead = Offset(
        x = arrowBase.x + (perpX * arrowHeadHalfWidth),
        y = arrowBase.y + (perpY * arrowHeadHalfWidth)
    )
    val rightHead = Offset(
        x = arrowBase.x - (perpX * arrowHeadHalfWidth),
        y = arrowBase.y - (perpY * arrowHeadHalfWidth)
    )

    val shader = LinearGradient(
        start.x,
        start.y,
        end.x,
        end.y,
        animatedHighlightColors(),
        null,
        Shader.TileMode.REPEAT
    ).apply {
        val shift = (rotationDegrees / 360f) * length
        val matrix = Matrix().apply {
            postTranslate(unitX * shift, unitY * shift)
        }
        setLocalMatrix(matrix)
    }
    val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.shader = shader
    }
    drawContext.canvas.nativeCanvas.apply {
        drawLine(start.x, start.y, shaftEnd.x, shaftEnd.y, paint)
        drawLine(end.x, end.y, leftHead.x, leftHead.y, paint)
        drawLine(end.x, end.y, rightHead.x, rightHead.y, paint)
    }
}

data class SnapshotCoordinateSpace(
    val width: Int,
    val height: Int
)

data class ParsedSwipeRuleDraft(
    val startXRatio: Float,
    val startYRatio: Float,
    val endXRatio: Float,
    val endYRatio: Float,
    val durationMs: Long
)

fun resolveSnapshotCoordinateSpace(
    snapshot: Snapshot,
    bitmap: Bitmap?,
    flatNodes: List<SnapshotNode>
): SnapshotCoordinateSpace? {
    // Snapshots can come from rotated or cropped captures, so prefer the explicit screen size
    // but fall back to the bitmap or node bounds when older data is incomplete.
    val width = snapshot.screenWidth.takeIf { it > 0 }
        ?: bitmap?.width?.takeIf { it > 0 }
        ?: flatNodes.maxOfOrNull { it.bounds.right }?.takeIf { it > 0 }
    val height = snapshot.screenHeight.takeIf { it > 0 }
        ?: bitmap?.height?.takeIf { it > 0 }
        ?: flatNodes.maxOfOrNull { it.bounds.bottom }?.takeIf { it > 0 }
    return if (width != null && height != null) {
        SnapshotCoordinateSpace(width = width, height = height)
    } else {
        null
    }
}

fun parseSwipePointDraft(
    pointText: String,
    coordinateSpace: SnapshotCoordinateSpace?
): Offset? {
    val space = coordinateSpace ?: return null
    val parts = pointText
        .trim()
        .removePrefix("(")
        .removeSuffix(")")
        .split(",")
        .map { it.trim() }
    if (parts.size != 2) return null
    val x = parts[0].toIntOrNull() ?: return null
    val y = parts[1].toIntOrNull() ?: return null
    if (x !in 0..space.width || y !in 0..space.height) return null
    return Offset(x.toFloat(), y.toFloat())
}

fun parseSwipeRuleDraft(
    coordinateSpace: SnapshotCoordinateSpace?,
    startText: String,
    endText: String,
    durationText: String
): ParsedSwipeRuleDraft? {
    val space = coordinateSpace ?: return null
    val start = parseSwipePointDraft(startText, space) ?: return null
    val end = parseSwipePointDraft(endText, space) ?: return null
    val durationMs = durationText.toLongOrNull()?.takeIf { it > 0L } ?: return null
    return ParsedSwipeRuleDraft(
        startXRatio = (start.x / space.width.toFloat()).coerceIn(0f, 1f),
        startYRatio = (start.y / space.height.toFloat()).coerceIn(0f, 1f),
        endXRatio = (end.x / space.width.toFloat()).coerceIn(0f, 1f),
        endYRatio = (end.y / space.height.toFloat()).coerceIn(0f, 1f),
        durationMs = durationMs
    )
}

fun formatSwipePointDraft(x: Int, y: Int): String = "($x,$y)"
