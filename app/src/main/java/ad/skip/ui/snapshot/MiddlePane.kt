package ad.skip.ui.snapshot

import ad.skip.R
import ad.skip.db.SnapshotNode
import ad.skip.ui.widgets.ResIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun MiddlePane(
    modifier: Modifier = Modifier,
    rootNode: SnapshotNode,
    selectedNode: MutableState<SnapshotNode?>,
    expandedNodeKeys: SnapshotStateList<String>,
    treeNodeRequesters: MutableMap<String, BringIntoViewRequester>
) {
    // The actual tree rendering stays recursive in HierarchyTreeNode; this pane just provides
    // scrolling and the root entry point.
    Column(
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        HierarchyTreeNode(
            node = rootNode,
            nodeKey = "0",
            depth = 0,
            selectedNode = selectedNode,
            expandedNodeKeys = expandedNodeKeys,
            treeNodeRequesters = treeNodeRequesters
        )
    }
}

@Composable
private fun HierarchyTreeNode(
    node: SnapshotNode,
    nodeKey: String,
    depth: Int,
    selectedNode: MutableState<SnapshotNode?>,
    expandedNodeKeys: SnapshotStateList<String>,
    treeNodeRequesters: MutableMap<String, BringIntoViewRequester>
) {
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val isExpanded = nodeKey in expandedNodeKeys
    val hasChildren = node.children.isNotEmpty()
    val isSelected = selectedNode.value === node
    val bringIntoViewRequester = remember(nodeKey) { BringIntoViewRequester() }

    DisposableEffect(nodeKey) {
        treeNodeRequesters[nodeKey] = bringIntoViewRequester
        onDispose {
            treeNodeRequesters.remove(nodeKey)
        }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent
                )
                .clickable { selectedNode.value = node }
                .padding(start = (depth * 6).dp, top = 3.dp, bottom = 3.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasChildren) {
                ResIcon(
                    if (isExpanded) R.drawable.ic_tree_expand_more else R.drawable.ic_tree_chevron_right,
                    color = onSurfaceColor,
                    size = 16.dp,
                ) {
                    if (nodeKey in expandedNodeKeys) {
                        expandedNodeKeys.remove(nodeKey)
                    } else {
                        expandedNodeKeys.add(nodeKey)
                    }
                }
            } else {
                Spacer(modifier = Modifier.width(16.dp))
            }
            Text(
                text = node.editorHierarchyLabel(),
                color = onSurfaceColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (hasChildren && isExpanded) {
            node.children.forEachIndexed { index, child ->
                HierarchyTreeNode(
                    node = child,
                    nodeKey = "$nodeKey.$index",
                    depth = depth + 1,
                    selectedNode = selectedNode,
                    expandedNodeKeys = expandedNodeKeys,
                    treeNodeRequesters = treeNodeRequesters
                )
            }
        }
    }
}
