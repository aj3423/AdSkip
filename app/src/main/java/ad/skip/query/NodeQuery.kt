package ad.skip.query

import ad.skip.db.Snapshot
import ad.skip.db.SnapshotNode
import ad.skip.db.flattenedNodes
import ad.skip.db.toQueryValue
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import java.util.IdentityHashMap

private data class SnapshotNodeContext(
    val parent: SnapshotNode?,
    val indexInParent: Int
)

private interface NodeNavigator<T> {
    fun parent(node: T): T?
    fun child(node: T, index: Int): T?
    fun indexInParent(node: T): Int?
    fun stringField(node: T, field: String): String?
    fun numericField(node: T, field: String): Int?

    // Queries are evaluated relative to the node currently being tested.
    // This walks prefixes like parent.parent, child[2], or sibling[-1]
    // until we reach the node that actually owns the requested field.
    fun resolve(node: T, path: NodePath): T? {
        var current: T? = node
        for (step in path.traversals) {
            current = when {
                current == null -> null
                step is TraversalStep.Parent -> parent(current)
                step is TraversalStep.Child -> child(current, step.index)
                step is TraversalStep.Sibling -> {
                    val parentNode = parent(current) ?: return null
                    val currentIndex = indexInParent(current) ?: return null
                    child(parentNode, currentIndex + step.offset)
                }

                else -> null
            }
        }
        return current
    }
}

private class SnapshotNavigator(
    private val contexts: IdentityHashMap<SnapshotNode, SnapshotNodeContext>
) : NodeNavigator<SnapshotNode> {
    override fun parent(node: SnapshotNode): SnapshotNode? =
        contexts[node]?.parent

    override fun child(node: SnapshotNode, index: Int): SnapshotNode? =
        node.children.getOrNull(index)

    override fun indexInParent(node: SnapshotNode): Int? =
        contexts[node]?.indexInParent

    override fun stringField(node: SnapshotNode, field: String): String? =
        snapshotStringFieldValue(node, field)

    override fun numericField(node: SnapshotNode, field: String): Int? =
        snapshotNumericFieldValue(node, field)
}

private object AccessibilityNavigator : NodeNavigator<AccessibilityNodeInfo> {
    override fun parent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        node.parent

    override fun child(node: AccessibilityNodeInfo, index: Int): AccessibilityNodeInfo? =
        if (index in 0 until node.childCount) node.getChild(index) else null

    override fun indexInParent(node: AccessibilityNodeInfo): Int? {
        val parentNode = node.parent ?: return null
        for (index in 0 until parentNode.childCount) {
            if (parentNode.getChild(index) == node) {
                return index
            }
        }
        return null
    }

    override fun stringField(node: AccessibilityNodeInfo, field: String): String? =
        accessibilityStringFieldValue(node, field)

    override fun numericField(node: AccessibilityNodeInfo, field: String): Int? =
        accessibilityNumericFieldValue(node, field)
}

object NodeQuery {
    private val primaryFields = listOf(
        "text",
        "desc",
        "hintText",
        "stateDescription",
        "tooltipText",
        "uniqueId",
        "viewId",
        "className",
        "paneTitle",
    )

    val configurablePrimaryFields: List<String> = primaryFields

    fun generateUniqueCandidate(
        snapshot: Snapshot,
        targetNode: SnapshotNode,
        bannedFields: Set<String> = emptySet()
    ): String? {
        val contexts = buildSnapshotContexts(snapshot.root)
        val navigator = SnapshotNavigator(contexts)
        val allNodes = snapshot.flattenedNodes()
        val selectedClauses = exactStringClauses(targetNode, bannedFields).toMutableList()
        var currentMatches = if (selectedClauses.isEmpty()) {
            allNodes
        } else {
            allNodes.filter { node ->
                matchesNode(node, selectedClauses, navigator)
            }
        }
        if (currentMatches.size == 1 && currentMatches.first() === targetNode) {
            return buildQuery(selectedClauses)
        }

        // Primary fields on the selected node are always included when present.
        // If that is still ambiguous, expand outward to siblings, parents,
        // children, and finally bounds.
        val clauseGroups = listOf(
            siblingStringClauses(targetNode, contexts, bannedFields),
            ancestorStringClauses(targetNode, contexts, bannedFields),
            childStringClauses(targetNode, bannedFields),
            exactBoundsClauses(targetNode)
        ).map(::dedupeClauses)

        for (group in clauseGroups) {
            val remaining = group.toMutableList()
            while (currentMatches.size > 1 && remaining.isNotEmpty()) {
                val bestCandidate = remaining
                    .map { clause ->
                        val candidateClauses = selectedClauses + clause
                        val matches = allNodes.filter { node ->
                            matchesNode(node, candidateClauses, navigator)
                        }
                        clause to matches
                    }
                    .filter { (_, matches) -> matches.size < currentMatches.size }
                    .minWithOrNull(
                        compareBy<Pair<QueryClause, List<SnapshotNode>>> { it.second.size }
                            .thenBy { it.first.render().length }
                    )
                    ?: break

                selectedClauses += bestCandidate.first
                currentMatches = bestCandidate.second
                remaining.remove(bestCandidate.first)
            }

            if (selectedClauses.isNotEmpty() && currentMatches.size == 1 && currentMatches.first() === targetNode) {
                return buildQuery(selectedClauses)
            }
        }

        return null
    }

    fun findMatchingNode(root: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
        val parsed = parse(query) ?: return null
        return findMatchingNode(root, parsed)
    }

    internal fun findMatchingNode(root: AccessibilityNodeInfo, query: ParsedQuery): AccessibilityNodeInfo? =
        findMatchingAccessibilityNode(root, query)

    fun validate(query: String): String? =
        when (val result = NodeQueryParser.parse(query)) {
            is QueryParseResult.Success -> null
            is QueryParseResult.Error -> result.message
        }

    internal fun isUniqueMatch(snapshot: Snapshot, targetNode: SnapshotNode, query: String): Boolean {
        val parsed = parse(query) ?: return false
        val contexts = buildSnapshotContexts(snapshot.root)
        val navigator = SnapshotNavigator(contexts)
        val matches = snapshot.flattenedNodes().filter { node ->
            matchesNode(node, parsed.clauses, navigator)
        }
        return matches.size == 1 && matches.first() === targetNode
    }

    private fun findMatchingAccessibilityNode(
        root: AccessibilityNodeInfo?,
        query: ParsedQuery
    ): AccessibilityNodeInfo? {
        if (root == null) return null
        if (matchesNode(root, query.clauses, AccessibilityNavigator)) {
            return root
        }
        for (index in 0 until root.childCount) {
            val match = findMatchingAccessibilityNode(root.getChild(index), query)
            if (match != null) {
                return match
            }
        }
        return null
    }

    private fun findMatchingSnapshotNode(
        root: SnapshotNode?,
        query: ParsedQuery,
        navigator: SnapshotNavigator
    ): SnapshotNode? {
        if (root == null) return null
        if (matchesNode(root, query.clauses, navigator)) {
            return root
        }
        for (child in root.children) {
            val match = findMatchingSnapshotNode(child, query, navigator)
            if (match != null) {
                return match
            }
        }
        return null
    }

    internal fun parse(query: String): ParsedQuery? =
        when (val result = NodeQueryParser.parse(query)) {
            is QueryParseResult.Success -> result.query
            is QueryParseResult.Error -> null
        }

    private fun buildQuery(clauses: List<QueryClause>): String =
        clauses.joinToString(" and ") { clause -> clause.render() }

    private fun exactStringClauses(node: SnapshotNode, bannedFields: Set<String>): List<QueryClause> =
        configurablePrimaryFields.mapNotNull { field ->
            if (field in bannedFields) return@mapNotNull null
            snapshotStringFieldValue(node, field)
                ?.takeIf(String::isNotEmpty)
                ?.let { value ->
                    exactStringClause(NodePath(emptyList(), field), value)
                }
        }

    private fun ancestorStringClauses(
        node: SnapshotNode,
        contexts: IdentityHashMap<SnapshotNode, SnapshotNodeContext>,
        bannedFields: Set<String>
    ): List<QueryClause> {
        val clauses = mutableListOf<QueryClause>()
        var current = contexts[node]?.parent
        var depth = 1
        while (current != null) {
            val path = NodePath(List(depth) { TraversalStep.Parent }, "")
            clauses += configurablePrimaryFields.mapNotNull { field ->
                if (field in bannedFields) return@mapNotNull null
                snapshotStringFieldValue(current, field)
                    ?.takeIf(String::isNotEmpty)
                    ?.let { value ->
                        exactStringClause(path.copy(field = field), value)
                    }
            }
            current = contexts[current]?.parent
            depth++
        }
        return clauses
    }

    private fun siblingStringClauses(
        node: SnapshotNode,
        contexts: IdentityHashMap<SnapshotNode, SnapshotNodeContext>,
        bannedFields: Set<String>
    ): List<QueryClause> {
        val context = contexts[node] ?: return emptyList()
        val parent = context.parent ?: return emptyList()
        val clauses = mutableListOf<QueryClause>()

        parent.children.forEachIndexed { siblingIndex, sibling ->
            // Offsets are relative to the selected node:
            // previous siblings are negative, next siblings are positive.
            val offset = siblingIndex - context.indexInParent
            if (offset == 0) return@forEachIndexed
            val path = NodePath(listOf(TraversalStep.Sibling(offset)), "")
            clauses += configurablePrimaryFields.mapNotNull { field ->
                if (field in bannedFields) return@mapNotNull null
                snapshotStringFieldValue(sibling, field)
                    ?.takeIf(String::isNotEmpty)
                    ?.let { value ->
                        exactStringClause(path.copy(field = field), value)
                    }
            }
        }

        return clauses
    }

    private fun childStringClauses(node: SnapshotNode, bannedFields: Set<String>): List<QueryClause> =
        node.children.flatMapIndexed { index, child ->
            val path = NodePath(listOf(TraversalStep.Child(index)), "")
            configurablePrimaryFields.mapNotNull { field ->
                if (field in bannedFields) return@mapNotNull null
                snapshotStringFieldValue(child, field)
                    ?.takeIf(String::isNotEmpty)
                    ?.let { value ->
                        exactStringClause(path.copy(field = field), value)
                    }
            }
        }

    private fun exactBoundsClauses(node: SnapshotNode): List<QueryClause> =
        listOf(
            NumericComparisonClause(NodePath(emptyList(), "width"), ComparisonOperator.EQ, node.bounds.right - node.bounds.left),
            NumericComparisonClause(NodePath(emptyList(), "height"), ComparisonOperator.EQ, node.bounds.bottom - node.bounds.top),
            NumericComparisonClause(NodePath(emptyList(), "left"), ComparisonOperator.EQ, node.bounds.left),
            NumericComparisonClause(NodePath(emptyList(), "top"), ComparisonOperator.EQ, node.bounds.top),
            NumericComparisonClause(NodePath(emptyList(), "right"), ComparisonOperator.EQ, node.bounds.right),
            NumericComparisonClause(NodePath(emptyList(), "bottom"), ComparisonOperator.EQ, node.bounds.bottom),
        )

    private fun dedupeClauses(clauses: List<QueryClause>): List<QueryClause> =
        clauses.distinctBy { it.render() }

    private fun exactStringClause(path: NodePath, value: String): StringQueryClause =
        if (path.field == "text") {
            // Generated text clauses are emitted as literal regex text so users
            // can still edit them into a broader regex manually if they want.
            val pattern = value.toLiteralRegexPattern()
            StringQueryClause(
                path = path,
                value = pattern,
                matchMode = StringMatchMode.REGEX,
                regex = Regex(pattern)
            )
        } else {
            StringQueryClause(
                path = path,
                value = value,
                matchMode = StringMatchMode.EXACT
            )
        }

    private fun buildSnapshotContexts(root: SnapshotNode): IdentityHashMap<SnapshotNode, SnapshotNodeContext> {
        val contexts = IdentityHashMap<SnapshotNode, SnapshotNodeContext>()

        fun visit(node: SnapshotNode, parent: SnapshotNode?, indexInParent: Int) {
            contexts[node] = SnapshotNodeContext(parent, indexInParent)
            node.children.forEachIndexed { index, child ->
                visit(child, node, index)
            }
        }

        visit(root, null, -1)
        return contexts
    }

    private fun <T> matchesNode(
        node: T,
        clauses: List<QueryClause>,
        navigator: NodeNavigator<T>
    ): Boolean =
        clauses.all { clause ->
            val resolvedNode = navigator.resolve(node, clause.path) ?: return@all false
            when (clause) {
                is StringQueryClause -> {
                    val value = navigator.stringField(resolvedNode, clause.path.field) ?: return@all false
                    // Only text uses regex; the other string fields stay exact so
                    // generated queries remain readable and predictable.
                    when (clause.matchMode) {
                        StringMatchMode.EXACT -> value == clause.value
                        StringMatchMode.REGEX -> clause.regex?.matches(value) == true
                    }
                }

                is NumericComparisonClause -> {
                    val value = navigator.numericField(resolvedNode, clause.path.field) ?: return@all false
                    when (clause.operator) {
                        ComparisonOperator.EQ -> value == clause.value
                        ComparisonOperator.GT -> value > clause.value
                        ComparisonOperator.GTE -> value >= clause.value
                        ComparisonOperator.LT -> value < clause.value
                        ComparisonOperator.LTE -> value <= clause.value
                    }
                }

                is NumericRangeClause -> {
                    val value = navigator.numericField(resolvedNode, clause.path.field) ?: return@all false
                    value in clause.minValue..clause.maxValue
                }
            }
        }
}

private fun snapshotStringFieldValue(node: SnapshotNode, field: String): String? =
    when (field) {
        "viewId" -> node.viewId
        "text" -> node.text
        "desc" -> node.description
        "className" -> node.className
        "clickable" -> node.clickable.toString()
        "focusable" -> node.focusable.toString()
        "checkable" -> node.checkable.toString()
        "checked" -> node.checked.toString()
        "visibleToUser" -> node.visibleToUser.toString()
        "enabled" -> node.enabled.toString()
        "package" -> node.packageName
        "paneTitle" -> node.paneTitle
        "hintText" -> node.hintText
        "error" -> node.error
        "stateDescription" -> node.stateDescription
        "tooltipText" -> node.tooltipText
        "uniqueId" -> node.uniqueId
        "isSelected" -> node.isSelected.toString()
        "isFocused" -> node.isFocused.toString()
        "isAccessibilityFocused" -> node.isAccessibilityFocused.toString()
        "isEditable" -> node.isEditable.toString()
        "isPassword" -> node.isPassword.toString()
        "isScrollable" -> node.isScrollable.toString()
        "isLongClickable" -> node.isLongClickable.toString()
        "isDismissable" -> node.isDismissable.toString()
        "isMultiLine" -> node.isMultiLine.toString()
        "isContentInvalid" -> node.isContentInvalid.toString()
        "isShowingHintText" -> node.isShowingHintText.toString()
        "isImportantForAccessibility" -> node.isImportantForAccessibility.toString()
        "isScreenReaderFocusable" -> node.isScreenReaderFocusable.toString()
        "isHeading" -> node.isHeading.toString()
        "isTextEntryKey" -> node.isTextEntryKey.toString()
        "isContextClickable" -> node.isContextClickable.toString()
        "rangeInfo" -> node.rangeInfo?.toQueryValue()
        "collectionInfo" -> node.collectionInfo?.toQueryValue()
        "collectionItemInfo" -> node.collectionItemInfo?.toQueryValue()
        else -> node.attributes[field]
    }

private fun snapshotNumericFieldValue(node: SnapshotNode, field: String): Int? =
    when (field) {
        "left" -> node.bounds.left
        "right" -> node.bounds.right
        "top" -> node.bounds.top
        "bottom" -> node.bounds.bottom
        "width" -> node.bounds.right - node.bounds.left
        "height" -> node.bounds.bottom - node.bounds.top
        "drawingOrder" -> node.drawingOrder
        "liveRegion" -> node.liveRegion
        "maxTextLength" -> node.maxTextLength
        "movementGranularities" -> node.movementGranularities
        "textSelectionStart" -> node.textSelectionStart
        "textSelectionEnd" -> node.textSelectionEnd
        else -> null
    }

private fun accessibilityStringFieldValue(node: AccessibilityNodeInfo, field: String): String? =
    when (field) {
        "viewId" -> node.viewIdResourceName
        "text" -> node.text?.toString()
        "desc" -> node.contentDescription?.toString()
        "className" -> node.className?.toString()
        "clickable" -> node.isClickable.toString()
        "focusable" -> node.isFocusable.toString()
        "checkable" -> node.isCheckable.toString()
        "checked" -> node.isChecked.toString()
        "visibleToUser" -> node.isVisibleToUser.toString()
        "enabled" -> node.isEnabled.toString()
        "package" -> node.packageName?.toString()
        "paneTitle" -> node.paneTitle?.toString()
        "hintText" -> node.hintText?.toString()
        "error" -> node.error?.toString()
        "stateDescription" -> node.stateDescription?.toString()
        "tooltipText" -> node.tooltipText?.toString()
        "uniqueId" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) node.uniqueId else null
        "isSelected" -> node.isSelected.toString()
        "isFocused" -> node.isFocused.toString()
        "isAccessibilityFocused" -> node.isAccessibilityFocused.toString()
        "isEditable" -> node.isEditable.toString()
        "isPassword" -> node.isPassword.toString()
        "isScrollable" -> node.isScrollable.toString()
        "isLongClickable" -> node.isLongClickable.toString()
        "isDismissable" -> node.isDismissable.toString()
        "isMultiLine" -> node.isMultiLine.toString()
        "isContentInvalid" -> node.isContentInvalid.toString()
        "isShowingHintText" -> node.isShowingHintText.toString()
        "isImportantForAccessibility" -> node.isImportantForAccessibility.toString()
        "isScreenReaderFocusable" -> node.isScreenReaderFocusable.toString()
        "isHeading" -> node.isHeading.toString()
        "isTextEntryKey" -> node.isTextEntryKey.toString()
        "isContextClickable" -> node.isContextClickable.toString()
        "rangeInfo" -> node.rangeInfo?.let {
            listOf(
                "type=${it.type}",
                "min=${it.min}",
                "max=${it.max}",
                "current=${it.current}"
            ).joinToString(", ")
        }

        "collectionInfo" -> node.collectionInfo?.let {
            listOf(
                "rowCount=${it.rowCount}",
                "columnCount=${it.columnCount}",
                "isHierarchical=${it.isHierarchical}",
                "selectionMode=${it.selectionMode}"
            ).joinToString(", ")
        }

        "collectionItemInfo" -> node.collectionItemInfo?.let {
            listOf(
                "rowIndex=${it.rowIndex}",
                "rowSpan=${it.rowSpan}",
                "columnIndex=${it.columnIndex}",
                "columnSpan=${it.columnSpan}",
                "isSelected=${it.isSelected}"
            ).joinToString(", ")
        }

        else -> null
    }

private fun accessibilityNumericFieldValue(node: AccessibilityNodeInfo, field: String): Int? {
    val bounds = Rect().also(node::getBoundsInScreen)
    return when (field) {
        "left" -> bounds.left
        "right" -> bounds.right
        "top" -> bounds.top
        "bottom" -> bounds.bottom
        "width" -> bounds.width()
        "height" -> bounds.height()
        "drawingOrder" -> node.drawingOrder
        "liveRegion" -> node.liveRegion
        "maxTextLength" -> node.maxTextLength
        "movementGranularities" -> node.movementGranularities
        "textSelectionStart" -> node.textSelectionStart
        "textSelectionEnd" -> node.textSelectionEnd
        else -> null
    }
}
