package ad.skip

import ad.skip.db.History
import ad.skip.db.HistoryTable
import ad.skip.db.Rule
import ad.skip.db.RuleTable
import ad.skip.db.Snapshot
import ad.skip.db.SnapshotTable
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap

object G {
    val snapshots: SnapshotStateMap<String, SnapshotStateList<Snapshot>> = mutableStateMapOf()
    val rules: SnapshotStateMap<String, SnapshotStateList<Rule>> = mutableStateMapOf()
    val histories: SnapshotStateList<History> = mutableStateListOf()

    // must be called on app startup
    fun init(ctx: Context) {
        reloadSnapshots(ctx)
        reloadRules(ctx)
        reloadHistories(ctx)
    }
    fun reloadRules(ctx: Context) {
        rules.clear()
        RuleTable.listAll(ctx)
            .groupBy { it.packageName } // Group into Map<String, List<Rule>>
            .forEach { (pkgName, ruleList) ->
                rules[pkgName] = mutableStateListOf<Rule>().apply { addAll(ruleList) }
            }
    }
    fun reloadSnapshots(ctx: Context) {
        snapshots.clear()
        SnapshotTable.listAll(ctx)
            .groupBy { it.packageName } // Group into Map<String, List<Rule>>
            .forEach { (pkgName, snapshotList) ->
                snapshots[pkgName] = mutableStateListOf<Snapshot>().apply { addAll(snapshotList) }
            }
    }
    fun reloadHistories(ctx: Context) {
        histories.clear()
        histories.addAll(HistoryTable.listAll(ctx))
    }
}