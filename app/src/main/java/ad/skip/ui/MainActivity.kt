package ad.skip.ui

import ad.skip.G
import ad.skip.R
import ad.skip.ui.history.HistoryTab
import ad.skip.ui.rule.RulesTab
import ad.skip.ui.setting.SettingsTab
import ad.skip.ui.snapshot.SnapshotsTab
import ad.skip.ui.theme.AdSkipTheme
import ad.skip.ui.widgets.ResIcon
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AdSkipTheme {
                MainContent()
            }
        }
    }
}

private enum class MainTab(val label: String, val iconRes: Int) {
    Settings("Settings", R.drawable.ic_setting),
    Snapshots("Snapshots", R.drawable.ic_screenshot),
    Rules("Rules", R.drawable.ic_focus),
    History("History", R.drawable.ic_log)
}

@Composable
fun MainContent() {
    var selectedTab by remember { mutableStateOf(MainTab.Settings) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { tab ->
                    val badgeCount = when (tab) {
                        MainTab.Settings -> null
                        MainTab.Snapshots -> G.snapshots.values.sumOf { it.size }
                        MainTab.Rules -> G.rules.values.sumOf { it.size }
                        MainTab.History -> G.histories.size
                    }
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            if (badgeCount != null && badgeCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge {
                                            Text(badgeCount.toString())
                                        }
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier.size(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ResIcon(tab.iconRes, size = 24.dp)
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier.size(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    ResIcon(tab.iconRes, size = 24.dp)
                                }
                            }
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            MainTab.Settings -> SettingsTab(modifier = Modifier.padding(innerPadding))
            MainTab.Snapshots -> SnapshotsTab(modifier = Modifier.padding(innerPadding))
            MainTab.Rules -> RulesTab(modifier = Modifier.padding(innerPadding))
            MainTab.History -> HistoryTab(modifier = Modifier.padding(innerPadding))
        }
    }
}
