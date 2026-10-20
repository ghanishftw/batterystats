package com.gnsh.batterystats.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.rounded.Battery5Bar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gnsh.batterystats.data.BatterySnapshot
import com.gnsh.batterystats.ui.screens.HealthScreen
import com.gnsh.batterystats.ui.screens.HistoryScreen
import com.gnsh.batterystats.ui.screens.NowScreen
import com.gnsh.batterystats.ui.screens.SettingsScreen
import com.gnsh.batterystats.ui.screens.UsageScreen
import com.gnsh.batterystats.ui.screens.statusText
import kotlinx.coroutines.delay

private enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    Now("Now", Icons.Outlined.BatteryStd, Icons.Rounded.Battery5Bar),
    Usage("Usage", Icons.Outlined.Insights, Icons.Rounded.Insights),
    Health("Health", Icons.Outlined.FavoriteBorder, Icons.Rounded.Favorite),
    History("History", Icons.Rounded.History, Icons.Rounded.History),
}

@Composable
fun AppRoot(vm: MainViewModel) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    BackHandler(showSettings) { showSettings = false }
    val motion = MaterialTheme.motionScheme

    AnimatedContent(
        showSettings,
        transitionSpec = {
            val dir = if (targetState) 1 else -1
            (slideInHorizontally(motion.defaultSpatialSpec()) { it * dir / 4 } + fadeIn(motion.defaultEffectsSpec()))
                .togetherWith(slideOutHorizontally(motion.defaultSpatialSpec()) { -it * dir / 4 } + fadeOut(motion.fastEffectsSpec()))
        },
        label = "root",
    ) { settingsOpen ->
        if (settingsOpen) SettingsPage(vm) { showSettings = false }
        else MainPage(vm) { showSettings = true }
    }
}

@Composable
private fun MainPage(vm: MainViewModel, openSettings: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Now) }
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val nights by vm.nights.collectAsStateWithLifecycle()
    val samples by vm.samples.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(30_000); value = System.currentTimeMillis() }
    }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val motion = MaterialTheme.motionScheme

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(if (tab == Tab.Now) "Battery Stats" else tab.label) },
                subtitle = { Text(subtitle(snap)) },
                actions = {
                    FilledTonalIconButton(onClick = openSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                },
                scrollBehavior = scroll,
            )
        },
        bottomBar = {
            ShortNavigationBar {
                Tab.entries.forEach { t ->
                    ShortNavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = { Icon(if (tab == t) t.selectedIcon else t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        AnimatedContent(
            tab,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.96f))
                    .togetherWith(fadeOut(motion.fastEffectsSpec()))
            },
            label = "tab",
        ) { t ->
            Body(t, padding, vm, snap, now, live, settings, sessions, nights, samples, health)
        }
    }
}

@Composable
private fun Body(
    t: Tab, padding: PaddingValues, vm: MainViewModel, snap: BatterySnapshot?, now: Long,
    live: com.gnsh.batterystats.data.Session?, settings: com.gnsh.batterystats.data.AppSettings,
    sessions: List<com.gnsh.batterystats.data.Session>, nights: List<com.gnsh.batterystats.data.Night>, samples: List<com.gnsh.batterystats.data.Sample>,
    health: com.gnsh.batterystats.data.HealthEstimate?,
) {
    when (t) {
        Tab.Now -> NowScreen(padding, snap, live, settings, vm.capacityMah(settings)) { vm.setMonitor(true) }
        Tab.Usage -> UsageScreen(padding, live, sessions, nights.firstOrNull(), samples, now, vm.capacityMah(settings))
        Tab.Health -> HealthScreen(padding, health, snap)
        Tab.History -> HistoryScreen(padding, sessions, nights, settings, vm.capacityMah(settings))
    }
}

private fun subtitle(s: BatterySnapshot?) = s?.let { "${it.level}% · ${statusText(it)}" } ?: ""

@Composable
private fun SettingsPage(vm: MainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding -> SettingsScreen(padding, vm, settings) }
}
