package ad.skip.ui.snapshot

import ad.skip.R
import ad.skip.db.Snapshot
import ad.skip.db.SnapshotNode
import ad.skip.db.toQueryValue
import ad.skip.ui.widgets.ResIcon
import ad.skip.util.spf
import ad.skip.util.truncate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp


@Composable
fun RightPane(
    modifier: Modifier = Modifier,
    snapshot: Snapshot,
    selectedNode: SnapshotNode?,
    hasMatchedUniqueXPath: Boolean,
    bannedQueryFields: MutableState<Set<String>>,
    configurableQueryFields: List<String>,
    queryPathDraft: MutableState<String>,
    queryPathError: String?,
    queryPathMatchesSelectedUniquely: Boolean,
    onSaveRule: () -> Unit,
    onTogglePaneFocus: () -> Unit
) {
    Box(
        modifier = modifier.clickable { onTogglePaneFocus() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            SelectedNodeDetails(
                snapshot = snapshot,
                selectedNode = selectedNode,
                hasMatchedUniqueXPath = hasMatchedUniqueXPath,
                bannedQueryFields = bannedQueryFields,
                configurableQueryFields = configurableQueryFields,
                queryPathDraft = queryPathDraft,
                queryPathError = queryPathError,
                queryPathMatchesSelectedUniquely = queryPathMatchesSelectedUniquely,
                onSaveRule = onSaveRule
            )
        }
    }
}

@Composable
fun SelectedNodeDetails(
    snapshot: Snapshot,
    selectedNode: SnapshotNode?,
    hasMatchedUniqueXPath: Boolean,
    bannedQueryFields: MutableState<Set<String>>,
    configurableQueryFields: List<String>,
    queryPathDraft: MutableState<String>,
    queryPathError: String?,
    queryPathMatchesSelectedUniquely: Boolean,
    onSaveRule: () -> Unit
) {
    val ctx = LocalContext.current
    val queryFieldBanPrefs = remember(ctx) { spf.QueryFieldBan(ctx) }
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val successBorderColor = Color(0xFF4CAF50)
    val sectionSpacing = 4.dp
    selectedNode?.let { node ->
        var showAdvancedFields by remember(node) { mutableStateOf(false) }
        val commonDetailRows = buildList {
            fun addOptionalStringField(label: String, value: String?) {
                val text = value.orEmpty()
                if (text.isNotBlank()) {
                    add(label to text.truncate(60))
                }
            }
            addOptionalStringField("viewId", node.viewId)
            addOptionalStringField("className", node.className)
            addOptionalStringField("desc", node.description)
            addOptionalStringField("text", node.text)
            addOptionalStringField("paneTitle", node.paneTitle)
            addOptionalStringField("hintText", node.hintText)
            addOptionalStringField("error", node.error)
            addOptionalStringField("stateDescription", node.stateDescription)
            addOptionalStringField("tooltipText", node.tooltipText)
            addOptionalStringField("uniqueId", node.uniqueId)
        }
        val availableConfigurableQueryFields = configurableQueryFields.filter { field ->
            commonDetailRows.any { it.first == field }
        }
        fun setQueryFieldEnabled(field: String, enabled: Boolean) {
            val updatedFields = bannedQueryFields.value
                .toMutableSet()
                .apply {
                    if (enabled) remove(field) else add(field)
                }
                .toSet()
            bannedQueryFields.value = updatedFields
            queryFieldBanPrefs.set(snapshot.packageName, updatedFields)
        }
        val advancedDetailRows = buildList {
            add("checkable" to node.checkable.toString())
            add("checked" to node.checked.toString())
            add("enabled" to node.enabled.toString())
            add("clickable" to node.clickable.toString())
            add("focusable" to node.focusable.toString())
            add("visibleToUser" to node.visibleToUser.toString())
            add("isSelected" to node.isSelected.toString())
            add("isFocused" to node.isFocused.toString())
            add("isAccessibilityFocused" to node.isAccessibilityFocused.toString())
            add("isEditable" to node.isEditable.toString())
            add("isPassword" to node.isPassword.toString())
            add("isScrollable" to node.isScrollable.toString())
            add("isLongClickable" to node.isLongClickable.toString())
            add("isDismissable" to node.isDismissable.toString())
            add("isMultiLine" to node.isMultiLine.toString())
            add("isContentInvalid" to node.isContentInvalid.toString())
            add("isShowingHintText" to node.isShowingHintText.toString())
            add("isImportantForAccessibility" to node.isImportantForAccessibility.toString())
            add("isScreenReaderFocusable" to node.isScreenReaderFocusable.toString())
            add("isHeading" to node.isHeading.toString())
            add("isTextEntryKey" to node.isTextEntryKey.toString())
            add("isContextClickable" to node.isContextClickable.toString())
            add("drawingOrder" to node.drawingOrder.toString())
            add("liveRegion" to node.liveRegion.toString())
            add("maxTextLength" to node.maxTextLength.toString())
            add("movementGranularities" to node.movementGranularities.toString())
            add("textSelectionStart" to node.textSelectionStart.toString())
            add("textSelectionEnd" to node.textSelectionEnd.toString())
            node.rangeInfo?.let { add("rangeInfo" to it.toQueryValue()) }
            node.collectionInfo?.let { add("collectionInfo" to it.toQueryValue()) }
            node.collectionItemInfo?.let { add("collectionItemInfo" to it.toQueryValue()) }
        }
        Text("activity: ${snapshot.activityName.orEmpty()}", color = onSurfaceColor)
        Spacer(modifier = Modifier.height(sectionSpacing))
        commonDetailRows.forEach { (label, value) ->
            Text("$label: $value", color = onSurfaceColor)
        }

        Spacer(modifier = Modifier.height(sectionSpacing))
        TextButton(
            onClick = { showAdvancedFields = !showAdvancedFields },
            contentPadding = PaddingValues(0.dp)
        ) {
            ResIcon(
                if (showAdvancedFields) R.drawable.ic_tree_expand_more else R.drawable.ic_tree_chevron_right,
                color = onSurfaceColor,
                size = 16.dp
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                if (showAdvancedFields) "Hide advanced fields" else "Show advanced fields",
                color = onSurfaceColor
            )
        }
        if (showAdvancedFields) {
            advancedDetailRows.forEach { (label, value) ->
                Text("$label: $value", color = onSurfaceColor)
            }
            if (node.attributes.isNotEmpty()) {
                Text("Other attributes:", color = onSurfaceColor)
                node.attributes.forEach { (key, value) ->
                    Text("$key: $value", color = onSurfaceColor)
                }
            }
            if (node.extras.isNotEmpty()) {
                Text("Extras:", color = onSurfaceColor)
                node.extras.forEach { (key, value) ->
                    Text("$key: $value", color = onSurfaceColor)
                }
            }
        }
        Spacer(modifier = Modifier.height(sectionSpacing))
        Text(
            if (hasMatchedUniqueXPath) "Matched unique XPath:" else "No matched unique XPath found",
            color = onSurfaceColor
        )
        if (availableConfigurableQueryFields.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Fields included in auto-generated path:",
                color = onSurfaceColor
            )
            availableConfigurableQueryFields.forEach { field ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            setQueryFieldEnabled(field, field in bannedQueryFields.value)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = field !in bannedQueryFields.value,
                        onCheckedChange = { checked ->
                            setQueryFieldEnabled(field, checked)
                        }
                    )
                    Text(field, color = onSurfaceColor)
                }
            }
        }
        OutlinedTextField(
            value = queryPathDraft.value,
            onValueChange = { queryPathDraft.value = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = 2,
            isError = queryPathError != null,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = if (queryPathMatchesSelectedUniquely) successBorderColor else TextFieldDefaults.colors().focusedIndicatorColor,
                unfocusedIndicatorColor = if (queryPathMatchesSelectedUniquely) successBorderColor else TextFieldDefaults.colors().unfocusedIndicatorColor
            ),
            placeholder = { Text("text=\"Skip.*\" and parent.className=\"android.widget.FrameLayout\" and width=100~200") },
            supportingText = {
                Text(
                    queryPathError ?: if (queryPathMatchesSelectedUniquely) {
                        "Query uniquely matches the selected node."
                    } else {
                        "Uncheck dynamic fields to regenerate. text uses regex; other string fields use plain equality. Supports parent.*, child[n].*, sibling[+/-n].*, and bounds like left>100."
                    }
                )
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = {
                onSaveRule()
            },
            enabled = queryPathDraft.value.trim().isNotEmpty() && queryPathError == null
        ) {
            Text("Save rule")
        }
    } ?: Text("No node selected", color = onSurfaceColor)
}


fun SnapshotNode.editorHierarchyLabel(): String {
    val classLabel = className
        ?.substringAfterLast('.')
        ?.takeIf(String::isNotBlank)
        ?: "Node"
    if (classLabel.endsWith("TextView")) {
        val textLabel = text
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.truncate(10)
        if (textLabel != null) {
            return textLabel
        }
    }
    return classLabel
}
