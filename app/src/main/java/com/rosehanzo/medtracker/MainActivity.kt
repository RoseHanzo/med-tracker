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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
        val s = Store.data(this).settings
        if (s.hcAutoSync) {
            lifecycleScope.launch {
                runCatching {
                    if (HealthSync.granted(this@MainActivity).isNotEmpty()) HealthSync.sync(this@MainActivity)
                }
            }
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Today", Icons.Filled.Home),
    Tab("Calendar", Icons.Filled.DateRange),
    Tab("Trends", Icons.Filled.Favorite),
    Tab("Settings", Icons.Filled.Settings),
)

@Composable
private fun App() {
    val data by Store.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
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
