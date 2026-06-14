package ad.skip.ui.rule

import ad.skip.G
import ad.skip.R
import ad.skip.db.Rule
import ad.skip.db.RuleTable
import ad.skip.ui.rememberPackageIconBitmap
import ad.skip.ui.widgets.ResIcon
import ad.skip.util.Util.getAppName
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RulesTab(modifier: Modifier = Modifier) {

    val ctx = LocalContext.current

    LaunchedEffect(ctx) {
        G.reloadRules(ctx)
    }

    var editingRule by remember(G.rules) { mutableStateOf<Rule?>(null) }
    var descriptionDraft by remember(editingRule?.id) {
        mutableStateOf(editingRule?.desc.orEmpty())
    }

    // a mapOf<packageName, isExpanded>
    val expandedPackages = remember(G.rules) {
        mutableStateMapOf<String, Boolean>().apply {
            putAll(G.rules.mapValues { true })
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Rules", style = TextStyle(fontSize = 24.sp))
        if (G.rules.isEmpty()) {
            Text("No saved rules yet.", modifier = Modifier.padding(top = 16.dp))
        } else {
            LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
                G.rules.forEach { (packageName, rules) ->
                    item(key = packageName) {
                        val iconBitmap = rememberPackageIconBitmap(packageName)
                        val isExpanded = expandedPackages[packageName] ?: true
                        OutlinedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    expandedPackages[packageName] = !isExpanded
                                }
                                .padding(vertical = 6.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ResIcon(
                                    if (isExpanded) R.drawable.ic_tree_expand_more else R.drawable.ic_tree_chevron_right,
                                    size = 18.dp
                                    )
                                Spacer(modifier = Modifier.width(8.dp))
                                Image(
                                    bitmap = iconBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(getAppName(ctx, packageName))
                                    Text(
                                        "${rules.size} rules",
                                        style = TextStyle(fontSize = 12.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    if (expandedPackages[packageName] != false) {
                        items(rules, key = { it.id }) { rule ->
                            RuleListItem(
                                rule = rule,
                                onClick = {
                                    editingRule = rule
                                    descriptionDraft = rule.desc
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    editingRule?.let { rule ->
        AlertDialog(
            onDismissRequest = { editingRule = null },
            title = { Text("Rule description") },
            text = {
                Column {
                    OutlinedTextField(
                        value = descriptionDraft,
                        onValueChange = { descriptionDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false
                    )

                    Text(rule.packageName)
                    Text(rule.activityName ?: "")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        RuleTable.updateById(ctx, rule.id, rule.copy(desc = descriptionDraft.trim()))
                        G.reloadRules(ctx)
                        editingRule = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            RuleTable.deleteById(ctx, rule.id)
                            // refresh
                            G.reloadRules(ctx)

                            editingRule = null
                        }
                    ) {
                        Text("Delete")
                    }
                }
            }
        )
    }
}

@Composable
private fun RuleListItem(
    rule: Rule,
    onClick: () -> Unit
) {
    val iconBitmap = rememberPackageIconBitmap(rule.packageName)
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 4.dp, bottom = 4.dp)
            .clickable(onClick = onClick),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            val description = rule.desc.takeIf { it.isNotBlank() }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    bitmap = iconBitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(description ?: "Unnamed rule")
            }
            if (description == null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("package: ${rule.packageName.ifBlank { "(any)" }}")
                Text("activity: ${rule.activityName?.ifBlank { "(any)" } ?: "(any)"}")
                Text("query: ${rule.queryPath}")
            }
        }
    }
}
