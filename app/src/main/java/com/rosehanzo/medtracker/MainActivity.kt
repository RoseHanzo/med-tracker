package com.rosehanzo.medtracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.rosehanzo.medtracker.data.Store
import com.rosehanzo.medtracker.health.HealthSync
import com.rosehanzo.medtracker.reminders.Reminders
import com.rosehanzo.medtracker.ui.CalendarScreen
import com.rosehanzo.medtracker.ui.MedTheme
import com.rosehanzo.medtracker.ui.ResumeTick
import com.rosehanzo.medtracker.ui.SettingsScreen
import com.rosehanzo.medtracker.ui.TodayScreen
import com.rosehanzo.medtracker.ui.TrendsScreen
import com.rosehanzo.medtracker.update.Updater
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var resumeTick by mutableIntStateOf(0)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        Reminders.createChannels(this)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MedTheme {
                CompositionLocalProvider(ResumeTick provides resumeTick) {
                    App()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        Reminders.scheduleAll(this)
        lifecycleScope.launch { runCatching { Updater.check() } }
        if (Store.data(this).settings.hcAutoSync) {
            lifecycleScope.launch {
                runCatching {
                    if (HealthSync.granted(this@MainActivity).isNotEmpty()) HealthSync.sync(this@MainActivity)
                }
            }
        }
    }
}

private data class Tab(val label: String, val selected: ImageVector, val unselected: ImageVector)

private val tabs = listOf(
    Tab("Today", Icons.Filled.Today, Icons.Outlined.Today),
    Tab("Calendar", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    Tab("Trends", Icons.Filled.Insights, Icons.Outlined.Insights),
    Tab("Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
private fun App() {
    val data by Store.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                tabs.forEachIndexed { i, t ->
                    val selected = tab == i
                    NavigationBarItem(
                        selected = selected,
                        onClick = { tab = i },
                        icon = { Icon(if (selected) t.selected else t.unselected, contentDescription = null) },
                        label = {
                            Text(t.label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> TodayScreen(data)
                1 -> CalendarScreen(data)
                2 -> TrendsScreen(data)
                else -> SettingsScreen(data)
            }
        }
    }
}
