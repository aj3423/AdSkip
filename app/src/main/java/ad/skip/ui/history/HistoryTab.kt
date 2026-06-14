package ad.skip.ui.history

import ad.skip.G
import ad.skip.db.RuleTable
import ad.skip.ui.rememberPackageIconBitmap
import ad.skip.util.Util.formatTime
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun HistoryTab(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val entries = G.histories.sortedByDescending { it.time }

    LaunchedEffect(ctx) {
        G.reloadHistories(ctx)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("History", style = TextStyle(fontSize = 24.sp))
        if (entries.isEmpty()) {
            Text("No hit history yet.", modifier = Modifier.padding(top = 16.dp))
        } else {
            LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
                items(entries, key = { it.id }) { history ->
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
                            val rule = RuleTable.findById(ctx, history.ruleId)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val icon = rememberPackageIconBitmap(history.packageName)
                                Image(
                                    bitmap = icon.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(rule?.desc?.takeIf { it.isNotBlank() } ?: history.ruleId.toString())

                            }
                            Text(formatTime(history.time))
                        }
                    }
                }
            }
        }
    }
}
