package ad.skip.ui.snapshot

import ad.skip.db.Snapshot
import ad.skip.db.SnapshotNode
import ad.skip.db.flattenedNodes
import ad.skip.query.NodeQuery
import ad.skip.util.ActionType
import ad.skip.util.spf
import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun SnapshotEditorScreen(snapshot: Snapshot, bmp: Bitmap) {
    val ctx = LocalContext.current
    val queryFieldBanPrefs = remember(ctx) { spf.QueryFieldBan(ctx) }

    val selectedNode = remember { mutableStateOf<SnapshotNode?>(null) }
    val selectedActionType = remember { mutableStateOf(ActionType.Click) }
    var middlePaneFraction by remember { mutableFloatStateOf(0.42f) }
    val saveRuleDialogVisible = remember { mutableStateOf(false) }
    val queryPathDraft = remember { mutableStateOf("") }
    val swipeStartDraft = remember { mutableStateOf("") }
    val swipeEndDraft = remember { mutableStateOf("") }
    val swipeDurationDraft = remember { mutableStateOf("220") }
    val swipePickInProgress = remember { mutableStateOf(false) }
    val magnifierCanvasOffset = remember { mutableStateOf<Offset?>(null) }
    val magnifierSourceOffset = remember { mutableStateOf<Offset?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "highlightBorder")
    val highlightRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "highlightRotation"
    )
    val animatedMiddlePaneFraction by animateFloatAsState(
        targetValue = middlePaneFraction,
        animationSpec = tween(durationMillis = 180),
        label = "editorSplitter"
    )
    val flatNodes = remember(snapshot.id) { snapshot.flattenedNodes() }
    val snapshotCoordinateSpace = remember(snapshot.id, flatNodes) {
        resolveSnapshotCoordinateSpace(snapshot, bmp, flatNodes)
    }
    val expandedNodeKeys = remember(snapshot.id) { mutableStateListOf<String>() }
    val treeNodeRequesters = remember(snapshot.id) { mutableStateMapOf<String, BringIntoViewRequester>() }
    val bannedQueryFields = remember(snapshot.packageName) {
        mutableStateOf(queryFieldBanPrefs.get(snapshot.packageName))
    }
    // Regenerate the suggested query only when the selection is stable.
    // While the loupe is moving we skip this work to keep dragging responsive.
    val uniqueQuery = remember(snapshot.id, selectedNode.value, magnifierCanvasOffset.value, bannedQueryFields.value) {
        if (magnifierCanvasOffset.value != null) { // Don't calc generate unique path when magnify dragging for better performance
            null
        } else if (selectedNode.value != null) {
            NodeQuery.generateUniqueCandidate(
                snapshot = snapshot,
                targetNode = selectedNode.value!!,
                bannedFields = bannedQueryFields.value
            )
        } else {
            null
        }
    }

    LaunchedEffect(snapshot.id, selectedNode.value) {
        val selected = selectedNode.value ?: return@LaunchedEffect
        val pathKeys = buildPathKeys(snapshot.root, selected)
        pathKeys.dropLast(1).forEach { key ->
            if (key !in expandedNodeKeys) {
                expandedNodeKeys.add(key)
            }
        }
    }

    LaunchedEffect(snapshot.id, selectedNode.value, expandedNodeKeys.toList()) {
        val selected = selectedNode.value ?: return@LaunchedEffect
        val selectedKey = buildPathKeys(snapshot.root, selected).lastOrNull() ?: return@LaunchedEffect
        treeNodeRequesters[selectedKey]?.bringIntoView()
    }

    LaunchedEffect(snapshot.id, selectedNode.value, uniqueQuery) {
        queryPathDraft.value = uniqueQuery.orEmpty()
    }

    val queryPathError = remember(queryPathDraft.value) {
        queryPathDraft.value.trim()
            .takeIf { it.isNotEmpty() }
            ?.let(NodeQuery::validate)
    }
    val queryPathMatchesSelectedUniquely = remember(snapshot.id, selectedNode.value, queryPathDraft.value, queryPathError) {
        val selected = selectedNode.value
        val query = queryPathDraft.value.trim()
        selected != null &&
            query.isNotEmpty() &&
            queryPathError == null &&
            NodeQuery.isUniqueMatch(snapshot, selected, query)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Keep the screenshot pane at its natural aspect ratio, but cap it at half the screen
        // so the hierarchy tree and details pane always remain visible.
        val maxLeftWidth = maxWidth / 2
        val desiredLeftWidth = bmp.let { bitmap ->
            maxHeight * (bitmap.width.toFloat() / bitmap.height.toFloat())
        }
        val leftPaneWidth = if (desiredLeftWidth < maxLeftWidth) desiredLeftWidth else maxLeftWidth

        Row(modifier = Modifier.fillMaxSize()) {
            LeftPane(
                modifier = Modifier
                    .width(leftPaneWidth)
                    .fillMaxHeight()
                    .background(Color.DarkGray),
                bitmap = bmp,
                flatNodes = flatNodes,
                selectedNode = selectedNode,
                selectedActionType = selectedActionType,
                saveRuleDialogVisible = saveRuleDialogVisible,
                swipePickInProgress = swipePickInProgress,
                swipeStartDraft = swipeStartDraft,
                swipeEndDraft = swipeEndDraft,
                snapshotCoordinateSpace = snapshotCoordinateSpace,
                magnifierCanvasOffset = magnifierCanvasOffset,
                magnifierSourceOffset = magnifierSourceOffset,
                highlightRotation = highlightRotation
            )

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(8.dp)
            ) {
                val dividerWidth = 12.dp
                val dividerWidthPx = with(LocalDensity.current) { dividerWidth.toPx() }
                val resizableWidthPx = (constraints.maxWidth.toFloat() - dividerWidthPx).coerceAtLeast(1f)

                if (swipePickInProgress.value) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Drag an arrow on the left screenshot to set the start and end points, then release to continue saving the rule",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {
                        MiddlePane(
                            modifier = Modifier
                                .weight(animatedMiddlePaneFraction)
                                .fillMaxHeight()
                                .padding(end = 8.dp),
                            rootNode = snapshot.root,
                            selectedNode = selectedNode,
                            expandedNodeKeys = expandedNodeKeys,
                            treeNodeRequesters = treeNodeRequesters
                        )

                        Box(
                            modifier = Modifier
                                .width(dividerWidth)
                                .fillMaxHeight()
                                .pointerInput(resizableWidthPx) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        middlePaneFraction =
                                            (middlePaneFraction + dragAmount.x / resizableWidthPx)
                                                .coerceIn(0.2f, 0.8f)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.outline)
                            )
                        }

                        RightPane(
                            modifier = Modifier
                                .weight(1f - animatedMiddlePaneFraction)
                                .fillMaxHeight()
                                .padding(start = 8.dp),
                            snapshot = snapshot,
                            selectedNode = selectedNode.value,
                            hasMatchedUniqueXPath = uniqueQuery != null,
                            bannedQueryFields = bannedQueryFields,
                            configurableQueryFields = NodeQuery.configurablePrimaryFields,
                            queryPathDraft = queryPathDraft,
                            queryPathError = queryPathError,
                            queryPathMatchesSelectedUniquely = queryPathMatchesSelectedUniquely,
                            onSaveRule = {
                                saveRuleDialogVisible.value = true
                            },
                            onTogglePaneFocus = {
                                middlePaneFraction =
                                    if (middlePaneFraction <= 0.25f) 0.8f else 0.2f
                            }
                        )
                    }
                }
            }

            if (saveRuleDialogVisible.value) {
                SaveRuleDialog(
                    snapshot = snapshot,
                    queryPathDraft = queryPathDraft,
                    selectedActionType = selectedActionType,
                    snapshotCoordinateSpace = snapshotCoordinateSpace,
                    swipeStartDraft = swipeStartDraft,
                    swipeEndDraft = swipeEndDraft,
                    swipeDurationDraft = swipeDurationDraft,
                    onDismissRequest = { saveRuleDialogVisible.value = false },
                    onStartSwipePicking = {
                        saveRuleDialogVisible.value = false
                        swipePickInProgress.value = true
                    }
                )
            }
        }
    }
}

fun buildPathKeys(node: SnapshotNode, target: SnapshotNode): List<String> {
    // Expansion state is tracked by stable dotted keys such as "0.1.2".
    val childPath = buildPathKeys(node, target, "0")
    if (childPath.isNotEmpty()) return childPath
    return emptyList()
}

private fun buildPathKeys(node: SnapshotNode, target: SnapshotNode, key: String): List<String> {
    if (node === target) return listOf(key)
    node.children.forEachIndexed { index, child ->
        val childPath = buildPathKeys(child, target, "$key.$index")
        if (childPath.isNotEmpty()) {
            return listOf(key) + childPath
        }
    }
    return emptyList()
}
