package com.agon.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.agon.app.data.BlockEvent
import com.agon.app.facebook.FacebookWebViewScreen
import com.agon.app.services.AIExplorerService
import com.agon.app.ui.screens.*
import com.agon.app.ui.theme.*

import com.agon.app.viewmodel.GuardianViewModel

class MainActivity : ComponentActivity() {
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val intent = Intent(this, AIExplorerService::class.java).apply {
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    private val mediaProjectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.agon.app.REQUEST_MEDIA_PROJECTION") {
                val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjectionLauncher.launch(mpm.createScreenCaptureIntent())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AgonAppTheme {
                MainApp()
            }
        }
        registerReceiver(
            mediaProjectionReceiver,
            IntentFilter("com.agon.app.REQUEST_MEDIA_PROJECTION"),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_NOT_EXPORTED else 0
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(mediaProjectionReceiver) } catch (_: Exception) {}
    }
}

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val viewModel: GuardianViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isBottomNavVisible = currentRoute in listOf("home", "social", "content", "lists", "statistics", "profile")

    // Force onboarding if not completed
    LaunchedEffect(state.onboardingCompleted) {
        if (!state.onboardingCompleted) {
            navController.navigate("onboarding") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    // App unlock gate for settings
    val needsUnlock = state.pinCode != null && !state.appUnlocked && !state.onboardingCompleted

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (isBottomNavVisible && state.onboardingCompleted) {
                BottomNav(navController)
            }
        },
        containerColor = background
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (state.onboardingCompleted) "home" else "onboarding",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("onboarding") {
                OnboardingScreen(
                    onComplete = { name ->
                        viewModel.completeOnboarding(name)
                        navController.navigate("home") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    },
                    onRequestPermission = { /* handled by system settings intents */ },
                    accessibilityGranted = state.accessibilityGranted,
                    vpnGranted = state.vpnGranted,
                    deviceAdminGranted = state.deviceAdminGranted,
                    overlayGranted = state.overlayGranted,
                    usageAccessGranted = state.usageAccessGranted
                )
            }

            composable("pin_setup") {
                PinSetupScreen(
                    existingPin = state.pinCode,
                    onPinSet = { pin ->
                        viewModel.setPinCode(pin)
                        navController.popBackStack()
                    },
                    onSkip = {
                        navController.popBackStack()
                    }
                )
            }

            composable("home") {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToPermissions = { navController.navigate("permissions") },
                    onNavigateToSettings = { navController.navigate("settings") }
                )
            }

            composable("social") {
                SocialScreen(
                    onLaunchFacebookWrapper = { navController.navigate("facebook_webview") },
                    viewModel = viewModel
                )
            }

            composable("facebook_webview") {
                FacebookWebViewScreen(onBack = { navController.popBackStack() })
            }

            composable("content") { ContentScreen(viewModel = viewModel) }

            composable("lists") { ListsScreen(viewModel = viewModel) }

            composable("permissions") {
                PermissionsScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = viewModel
                )
            }

            composable("settings") {
                SettingsScreen(
                    onNavigateToSocial = { navController.navigate("social") },
                    onNavigateToContent = { navController.navigate("content") },
                    onNavigateToLists = { navController.navigate("lists") },
                    onNavigateToPermissions = { navController.navigate("permissions") },
                    onNavigateToProfile = { navController.navigate("profile") },
                    onNavigateToPinSetup = { navController.navigate("pin_setup") },
                    onNavigateToSchedule = { navController.navigate("schedule") },
                    onNavigateToTimeLimits = { navController.navigate("time_limits") },
                    onNavigateToStatistics = { navController.navigate("statistics") },
                    onNavigateToExportImport = { navController.navigate("export_import") },
                    onBack = { navController.popBackStack() },
                    viewModel = viewModel
                )
            }

            composable("profile") {
                ProfileScreen(
                    state = state,
                    onUpdateName = { viewModel.updateProfileName(it) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable("schedule") {
                ScheduleScreen(
                    rules = state.scheduleRules,
                    onAddRule = { viewModel.addScheduleRule(it) },
                    onUpdateRule = { viewModel.updateScheduleRule(it) },
                    onDeleteRule = { viewModel.deleteScheduleRule(it) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable("time_limits") {
                TimeLimitsScreen(
                    limits = state.dailyTimeLimits,
                    onAddLimit = { viewModel.addTimeLimit(it) },
                    onRemoveLimit = { viewModel.removeTimeLimit(it) },
                    onBack = { navController.popBackStack() }
                )
            }

            composable("statistics") {
                StatisticsScreen(
                    blocksCount = state.blocksCount,
                    shieldActivatedAt = state.shieldActivatedAt,
                    blockEvents = state.blockEvents,
                    onReset = { viewModel.resetStatistics() },
                    onBack = { navController.popBackStack() }
                )
            }

            composable("export_import") {
                ExportImportScreen(
                    state = state,
                    onImport = { websites, keywords, apps ->
                        viewModel.importBlocklist(websites, keywords, apps)
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@Composable
fun BottomNav(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val items = listOf(
        BottomNavItem("home", Icons.Default.Shield, "Shield"),
        BottomNavItem("social", Icons.Default.PhoneAndroid, "Social"),
        BottomNavItem("content", Icons.Default.VisibilityOff, "Content"),
        BottomNavItem("lists", Icons.Default.List, "Lists"),
        BottomNavItem("statistics", Icons.Default.BarChart, "Stats"),
        BottomNavItem("profile", Icons.Default.Person, "Profile")
    )

    Surface(
        color = surface,
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            navController.navigate(item.route) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) primary.copy(alpha = 0.15f) else Color.Transparent)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = if (selected) primary else textMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = item.label,
                        color = if (selected) primary else textMuted,
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

data class BottomNavItem(val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String)