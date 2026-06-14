package ad.skip.ui.setting

import ad.skip.util.Util.isAccessibilityServiceEnabled
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner


@Composable
fun SettingsTab(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current

    val lifecycleOwner = LocalLifecycleOwner.current

    var accessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(ctx)) }
    fun refreshAccessibilityStatus() {
        accessibilityEnabled = isAccessibilityServiceEnabled(ctx)
    }

    LaunchedEffect(ctx) {
        refreshAccessibilityStatus()
    }

    DisposableEffect(lifecycleOwner, ctx) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME) {
                // This runs every single time the user returns to the app
                //   e.g., after granting accessibility permission
                refreshAccessibilityStatus()
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun openAccessibilitySettings() {
        ctx.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("AdSkip", style = TextStyle(fontSize = 24.sp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = ::openAccessibilitySettings),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = accessibilityEnabled,
                onCheckedChange = { openAccessibilitySettings() }
            )
            Text(
                if (accessibilityEnabled) {
                    "Accessibility permission granted"
                } else {
                    "Accessibility permission required"
                }
            )
        }
    }
}
