package ad.skip.db

import ad.skip.util.json
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.database.getStringOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID


val snapshotJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

@Serializable
data class SnapshotBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

@Serializable
data class SnapshotRangeInfo(
    val type: Int,
    val min: Float,
    val max: Float,
    val current: Float
)

@Serializable
data class SnapshotCollectionInfo(
    val rowCount: Int,
    val columnCount: Int,
    val isHierarchical: Boolean,
    val selectionMode: Int
)

@Serializable
data class SnapshotCollectionItemInfo(
    val rowIndex: Int,
    val rowSpan: Int,
    val columnIndex: Int,
    val columnSpan: Int,
    val isSelected: Boolean
)

@Serializable
data class SnapshotNode(
    val bounds: SnapshotBounds,
    val viewId: String? = null,
    val className: String? = null,
    val text: String? = null,
    val description: String? = null,
    val clickable: Boolean = false,
    val focusable: Boolean = false,
    val checkable: Boolean = false,
    val checked: Boolean = false,
    val visibleToUser: Boolean = false,
    val enabled: Boolean = false,
    val packageName: String? = null,
    val paneTitle: String? = null,
    val hintText: String? = null,
    val error: String? = null,
    val stateDescription: String? = null,
    val tooltipText: String? = null,
    val uniqueId: String? = null,
    val isSelected: Boolean = false,
    val isFocused: Boolean = false,
    val isAccessibilityFocused: Boolean = false,
    val isEditable: Boolean = false,
    val isPassword: Boolean = false,
    val isScrollable: Boolean = false,
    val isLongClickable: Boolean = false,
    val isDismissable: Boolean = false,
    val isMultiLine: Boolean = false,
    val isContentInvalid: Boolean = false,
    val isShowingHintText: Boolean = false,
    val isImportantForAccessibility: Boolean = false,
    val isScreenReaderFocusable: Boolean = false,
    val isHeading: Boolean = false,
    val isTextEntryKey: Boolean = false,
    val isContextClickable: Boolean = false,
    val drawingOrder: Int = 0,
    val liveRegion: Int = 0,
    val maxTextLength: Int = -1,
    val movementGranularities: Int = 0,
    val textSelectionStart: Int = -1,
    val textSelectionEnd: Int = -1,
    val rangeInfo: SnapshotRangeInfo? = null,
    val collectionInfo: SnapshotCollectionInfo? = null,
    val collectionItemInfo: SnapshotCollectionItemInfo? = null,
    val attributes: Map<String, String> = emptyMap(),
    val extras: Map<String, String> = emptyMap(),
    val children: List<SnapshotNode> = emptyList()
) {
    companion object {
        // convert AccessibilityNodeInfo -> SnapshotNode
        fun from(node: AccessibilityNodeInfo?) : SnapshotNode? {
            if (node == null) return null
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val children = buildList {
                for (index in 0 until node.childCount) {
                    from(node.getChild(index))?.let(::add)
                }
            }
            val extras = linkedMapOf<String, String>()

            val extrasBundle = node.extras
            for (key in extrasBundle.keySet()) {
                val value = extrasBundle.get(key) ?: continue
                extras[key] = value.toString()
            }
            return SnapshotNode(
                bounds = SnapshotBounds(
                    left = bounds.left,
                    top = bounds.top,
                    right = bounds.right,
                    bottom = bounds.bottom
                ),
                viewId = node.viewIdResourceName,
                className = node.className?.toString(),
                text = node.text?.toString(),
                description = node.contentDescription?.toString(),
                clickable = node.isClickable,
                focusable = node.isFocusable,
                checkable = node.isCheckable,
                checked = node.isChecked,
                visibleToUser = node.isVisibleToUser,
                enabled = node.isEnabled,
                packageName = node.packageName?.toString(),
                paneTitle = node.paneTitle?.toString(),
                hintText = node.hintText?.toString(),
                error = node.error?.toString(),
                stateDescription = node.stateDescription?.toString(),
                tooltipText = node.tooltipText?.toString(),
                uniqueId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) node.uniqueId else null,
                isSelected = node.isSelected,
                isFocused = node.isFocused,
                isAccessibilityFocused = node.isAccessibilityFocused,
                isEditable = node.isEditable,
                isPassword = node.isPassword,
                isScrollable = node.isScrollable,
                isLongClickable = node.isLongClickable,
                isDismissable = node.isDismissable,
                isMultiLine = node.isMultiLine,
                isContentInvalid = node.isContentInvalid,
                isShowingHintText = node.isShowingHintText,
                isImportantForAccessibility = node.isImportantForAccessibility,
                isScreenReaderFocusable = node.isScreenReaderFocusable,
                isHeading = node.isHeading,
                isTextEntryKey = node.isTextEntryKey,
                isContextClickable = node.isContextClickable,
                drawingOrder = node.drawingOrder,
                liveRegion = node.liveRegion,
                maxTextLength = node.maxTextLength,
                movementGranularities = node.movementGranularities,
                textSelectionStart = node.textSelectionStart,
                textSelectionEnd = node.textSelectionEnd,
                rangeInfo = node.rangeInfo?.let {
                    SnapshotRangeInfo(
                        type = it.type,
                        min = it.min,
                        max = it.max,
                        current = it.current
                    )
                },
                collectionInfo = node.collectionInfo?.let {
                    SnapshotCollectionInfo(
                        rowCount = it.rowCount,
                        columnCount = it.columnCount,
                        isHierarchical = it.isHierarchical,
                        selectionMode = it.selectionMode
                    )
                },
                collectionItemInfo = node.collectionItemInfo?.let {
                    SnapshotCollectionItemInfo(
                        rowIndex = it.rowIndex,
                        rowSpan = it.rowSpan,
                        columnIndex = it.columnIndex,
                        columnSpan = it.columnSpan,
                        isSelected = it.isSelected
                    )
                },
                extras = extras,
                children = children
            )
        }
    }
}

@Serializable
data class Snapshot(
    val id: Long = 0L,
    val desc: String,
    val packageName: String,
    val activityName: String? = null,
    val screenWidth: Int,
    val screenHeight: Int,
    val screenshotFileName: String,
    val root: SnapshotNode
)

fun SnapshotNode.flatten(): List<SnapshotNode> =
    listOf(this) + children.flatMap { it.flatten() }

fun Snapshot.flattenedNodes(): List<SnapshotNode> =
    root.flatten()

//fun Snapshot.pathToNode(target: SnapshotNode): List<SnapshotNode> =
//    root.pathToNode(target).orEmpty()

private fun SnapshotNode.pathToNode(target: SnapshotNode): List<SnapshotNode>? {
    if (this === target) return listOf(this)
    for (child in children) {
        val childPath = child.pathToNode(target)
        if (childPath != null) {
            return listOf(this) + childPath
        }
    }
    return null
}

fun SnapshotRangeInfo.toQueryValue(): String =
    listOf(
        "type=$type",
        "min=$min",
        "max=$max",
        "current=$current"
    ).joinToString(", ")

fun SnapshotCollectionInfo.toQueryValue(): String =
    listOf(
        "rowCount=$rowCount",
        "columnCount=$columnCount",
        "isHierarchical=$isHierarchical",
        "selectionMode=$selectionMode"
    ).joinToString(", ")

fun SnapshotCollectionItemInfo.toQueryValue(): String =
    listOf(
        "rowIndex=$rowIndex",
        "rowSpan=$rowSpan",
        "columnIndex=$columnIndex",
        "columnSpan=$columnSpan",
        "isSelected=$isSelected"
    ).joinToString(", ")


object SnapshotTable : BasicTable<Snapshot>(Db.TABLE_SNAPSHOT) {
    @SuppressLint("Range")
    override fun fromCursor(cursor: Cursor): Snapshot {
        return Snapshot(
            id = cursor.getLong(cursor.getColumnIndex(Db.COLUMN_ID)),
            desc = cursor.getString(cursor.getColumnIndex(Db.COLUMN_DESC)),
            packageName = cursor.getString(cursor.getColumnIndex(Db.COLUMN_PKG_NAME)),
            activityName = cursor.getStringOrNull(cursor.getColumnIndex(Db.COLUMN_ACTIVITY)),
            screenWidth = cursor.getInt(cursor.getColumnIndex(Db.COLUMN_SCREEN_WIDTH)),
            screenHeight = cursor.getInt(cursor.getColumnIndex(Db.COLUMN_SCREEN_HEIGHT)),
            screenshotFileName = cursor.getString(cursor.getColumnIndex(Db.COLUMN_SCREENSHOT)),
            root = json.decodeFromString<SnapshotNode>(cursor.getString(cursor.getColumnIndex(Db.COLUMN_ROOT))),
        )
    }

    override fun toContentValues(
        item: Snapshot,
        includeId: Boolean
    ) = ContentValues().apply {
        if (includeId) put(Db.COLUMN_ID, item.id)
        put(Db.COLUMN_DESC, item.desc)
        put(Db.COLUMN_PKG_NAME, item.packageName)
        put(Db.COLUMN_ACTIVITY, item.activityName)
        put(Db.COLUMN_SCREEN_WIDTH, item.screenWidth)
        put(Db.COLUMN_SCREEN_HEIGHT, item.screenHeight)
        put(Db.COLUMN_SCREENSHOT, item.screenshotFileName)
        put(Db.COLUMN_ROOT, Json.encodeToString(item.root))
    }

    override fun deleteById(ctx: Context, id: Long): Int {
        val snapshot = findById(ctx, id)
        val deletedRows = super.deleteById(ctx, id)
        if (deletedRows > 0 && snapshot != null) {
            ScreenshotStore.delete(ctx, snapshot.screenshotFileName)
        }
        return deletedRows
    }
}

object ScreenshotStore {
    private const val DIR_NAME = "screenshots"

    fun save(ctx: Context, bytes: ByteArray): String {
        val fileName = "${UUID.randomUUID()}"
        screenshotDir(ctx).let { dir ->
            if (!dir.exists()) dir.mkdirs()
            File(dir, fileName).writeBytes(bytes)
        }
        return fileName
    }

    fun file(ctx: Context, fileName: String): File =
        File(screenshotDir(ctx), fileName)

    fun delete(ctx: Context, fileName: String) {
        file(ctx, fileName).delete()
    }

    private fun screenshotDir(ctx: Context): File = File(ctx.filesDir, DIR_NAME)
}
