package ad.skip.ui.snapshot

import ad.skip.G
import ad.skip.db.Snapshot
import ad.skip.db.SnapshotArchive
import ad.skip.db.SnapshotTable
import ad.skip.ui.rememberPackageIconBitmap
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
fun SnapshotsTab(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val snapshots = G.snapshots.values.flatten().sortedByDescending { it.id }
    var exportSnapshot by remember { mutableStateOf<Snapshot?>(null) }

    LaunchedEffect(ctx) {
        G.reloadSnapshots(ctx)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val snapshot = exportSnapshot
        exportSnapshot = null
        if (uri != null && snapshot != null) {
            runCatching {
                SnapshotArchive.export(ctx, snapshot, uri)
                Toast.makeText(ctx, "Snapshot exported", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(ctx, "Export failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                SnapshotArchive.import(ctx, uri)
                G.reloadSnapshots(ctx)
                Toast.makeText(ctx, "Snapshot imported", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(ctx, "Import failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    var editingSnapshot by remember(snapshots) { mutableStateOf<Snapshot?>(null) }
    var snapshotNameDraft by remember(editingSnapshot) {
        mutableStateOf(editingSnapshot?.desc?.removeSuffix(".snapshot").orEmpty())
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Snapshots", style = TextStyle(fontSize = 24.sp))
            TextButton(
                onClick = {
                    importLauncher.launch(
                        arrayOf(
                            "application/zip",
                            "application/octet-stream",
                            "application/x-zip-compressed"
                        )
                    )
                }
            ) {
                Text("Import")
            }
        }
        if (snapshots.isEmpty()) {
            Text(
                "No snapshots yet. When the accessibility service is active, press both Volume Up+Down to generate one.",
                modifier = Modifier.padding(top = 16.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
                items(snapshots) { snapshot ->
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingSnapshot = snapshot }
                            .padding(vertical = 6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        val iconBitmap = rememberPackageIconBitmap(snapshot.packageName)
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                bitmap = iconBitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = snapshot.desc,
                                style = TextStyle(fontSize = 14.sp)
                            )
                        }
                    }
                }
            }
            editingSnapshot?.let { snapshot ->
                AlertDialog(
                    onDismissRequest = { editingSnapshot = null },
                    title = { Text("Rename snapshot") },
                    text = {
                        OutlinedTextField(
                            value = snapshotNameDraft,
                            onValueChange = { snapshotNameDraft = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                SnapshotTable.updateById(
                                    ctx,
                                    snapshot.id,
                                    snapshot.copy(desc = snapshotNameDraft.trim())
                                )
                                G.reloadSnapshots(ctx)
                                editingSnapshot = null
                            },
                            enabled = snapshotNameDraft.trim().isNotEmpty()
                        ) {
                            Text("Save")
                        }
                    },
                    dismissButton = {
                        Row {
                            TextButton(
                                onClick = {
                                    editingSnapshot = null
                                    ctx.startActivity(
                                        Intent(ctx, SnapshotEditorActivity::class.java).apply {
                                            putExtra("snapshot_id", snapshot.id)
                                        }
                                    )
                                }
                            ) {
                                Text("Open")
                            }
                            TextButton(
                                onClick = {
                                    editingSnapshot = null
                                    exportSnapshot = snapshot
                                    exportLauncher.launch("${snapshot.exportFileName()}.zip")
                                }
                            ) {
                                Text("Export")
                            }
                            TextButton(
                                onClick = {
                                    editingSnapshot = null
                                    SnapshotTable.deleteById(ctx, snapshot.id)
                                    G.reloadSnapshots(ctx)
                                }
                            ) {
                                Text("Delete")
                            }
                        }
                    }
                )
            }
        }
    }
}

private fun Snapshot.exportFileName(): String =
    desc
        .ifBlank { "snapshot" }
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .take(80)
