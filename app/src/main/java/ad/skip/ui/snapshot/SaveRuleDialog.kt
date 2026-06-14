package ad.skip.ui.snapshot

import ad.skip.G
import ad.skip.R
import ad.skip.db.Rule
import ad.skip.db.RuleTable
import ad.skip.db.Snapshot
import ad.skip.query.NodeQuery
import ad.skip.ui.widgets.ResIcon
import ad.skip.ui.widgets.RowVCenterSpaced
import ad.skip.util.ActionType
import ad.skip.util.Click
import ad.skip.util.Swipe
import ad.skip.util.loge
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SaveRuleDialog(
    snapshot: Snapshot,
    queryPathDraft: MutableState<String>,
    selectedActionType: MutableState<ActionType>,
    snapshotCoordinateSpace: SnapshotCoordinateSpace?,
    swipeStartDraft: MutableState<String>,
    swipeEndDraft: MutableState<String>,
    swipeDurationDraft: MutableState<String>,
    onDismissRequest: () -> Unit,
    onStartSwipePicking: () -> Unit
) {
    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val queryPathError = remember(queryPathDraft.value) {
        queryPathDraft.value.trim()
            .takeIf { it.isNotEmpty() }
            ?.let(NodeQuery::validate)
    }
    val swipeDraft = remember(
        selectedActionType.value,
        snapshotCoordinateSpace,
        swipeStartDraft.value,
        swipeEndDraft.value,
        swipeDurationDraft.value
    ) {
        parseSwipeRuleDraft(
            coordinateSpace = snapshotCoordinateSpace,
            startText = swipeStartDraft.value,
            endText = swipeEndDraft.value,
            durationText = swipeDurationDraft.value
        )
    }
    var description by remember(snapshot.desc) {
        mutableStateOf(snapshot.desc)
    }

    fun ensureSwipeDraftDefaults() {
        if (swipeStartDraft.value.isBlank()) {
            swipeStartDraft.value =
                snapshotCoordinateSpace?.let {
                    formatSwipePointDraft(it.width / 2, (it.height * 0.62f).roundToInt())
                }.orEmpty()
        }
        if (swipeEndDraft.value.isBlank()) {
            swipeEndDraft.value =
                snapshotCoordinateSpace?.let {
                    formatSwipePointDraft(it.width / 2, (it.height * 0.38f).roundToInt())
                }.orEmpty()
        }
        if (swipeDurationDraft.value.isBlank()) {
            swipeDurationDraft.value = "220"
        }
    }

    LaunchedEffect(selectedActionType.value, snapshotCoordinateSpace) {
        if (selectedActionType.value == ActionType.Swipe) {
            ensureSwipeDraftDefaults()
        }
    }

    AlertDialog(
        modifier = Modifier.width(400.dp),
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        ),
        onDismissRequest = onDismissRequest,
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    label = { Text("Description (*)") }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Action:")
                Spacer(modifier = Modifier.height(4.dp))
                RuleActionSelector(
                    selectedActionType = selectedActionType
                )
                if (selectedActionType.value == ActionType.Swipe) {
                    Spacer(modifier = Modifier.height(8.dp))
                    snapshotCoordinateSpace?.let { space ->
                        Text(
                            "Coordinate range: x = 0..${space.width}, y = 0..${space.height}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    RowVCenterSpaced(6) {
                        CoordinateInputField(
                            label = "Start",
                            state = swipeStartDraft,
                            modifier = Modifier.width(140.dp)
                        )

                        ResIcon(R.drawable.ic_right_arrow, size = 16.dp)

                        CoordinateInputField(
                            label = "End",
                            state = swipeEndDraft,
                            modifier = Modifier.width(140.dp)
                        )

                        ResIcon(
                            R.drawable.ic_aim,
                            onClick = onStartSwipePicking,
                            size = 24.dp,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    NumberInputField(
                        label = "Duration (ms)",
                        state = swipeDurationDraft,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val selectedQuery = queryPathDraft.value.trim()
                    val resolvedQueryError = NodeQuery.validate(selectedQuery)
                    if (resolvedQueryError != null) {
                        Toast.makeText(ctx, resolvedQueryError, Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }

                    onDismissRequest()

                    coroutineScope.launch {
                        try {
                            RuleTable.addNew(
                                ctx,
                                Rule(
                                    desc = description,
                                    packageName = snapshot.packageName,
                                    activityName = snapshot.activityName,
                                    queryPath = selectedQuery,
                                    action = when (selectedActionType.value) {
                                        ActionType.Click -> Click()
                                        else -> Swipe(
                                            startXRatio = swipeDraft?.startXRatio ?: 0f,
                                            startYRatio = swipeDraft?.startYRatio ?: 0f,
                                            endXRatio = swipeDraft?.endXRatio ?: 0f,
                                            endYRatio = swipeDraft?.endYRatio ?: 0f,
                                            durationMs = swipeDraft?.durationMs ?: 100
                                        )
                                    },
                                )
                            )
                            G.reloadRules(ctx)
                            Toast.makeText(ctx, "Rule saved", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            loge("Failed to save rule: $e")
                            Toast.makeText(ctx, "Failed to save rule: $e", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = description.trim().isNotEmpty() &&
                    queryPathDraft.value.trim().isNotEmpty() &&
                    queryPathError == null &&
                    (selectedActionType.value != ActionType.Swipe || swipeDraft != null)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
        }
    )
}
