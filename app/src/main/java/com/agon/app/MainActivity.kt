package com.agon.app

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.agon.app.ui.components.PinGate
import com.agon.app.ui.screens.*
import com.agon.app.utils.AccessibilityUtils
import com.agon.app.utils.PermissionUtils
import com.agon.app.ui.theme.*
import com.agon.app.viewmodel.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val app = application as GuardianApp
        val initialOnboardingComplete = runBlocking(Dispatchers.IO) {
            app.repository.getAppSettings().isOnboardingComplete()
        }

        setContent {
            AgonAppTheme {
                MainApp(initialOnboardingComplete = initialOnboardingComplete)
            }
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LanguageManager.apply(base))
    }
}

@Composable
fun MainApp(initialOnboardingComplete: Boolean = false) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as GuardianApp
    val settings = app.repository.getAppSettings()
    val scope = rememberCoroutineScope()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isBottomNavVisible = currentRoute in listOf("home", "social", "content", "lists", "statistics", "profile")

    val onboardingComplete by settings.onboardingCompleteFlow.collectAsState(initial = initialOnboardingComplete)
    val pinHash by settings.pinHashFlow.collectAsState(initial = "")
    val hasPinSet = pinHash.isNotBlank()

    LaunchedEffect(onboardingComplete) {
        if (onboardingComplete) {
            navController.navigate("home") {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (isBottomNavVisible) {
                BottomNav(navController)
            }
        },
        containerColor = background
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (onboardingComplete) "home" else "onboarding",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("onboarding") {
                val lifecycleOwner = LocalLifecycleOwner.current
                
                val accessibilityGranted by settings.permAccessibilityFlow.collectAsState(initial = false)
                val overlayGranted by settings.permOverlayFlow.collectAsState(initial = false)
                val usageAccessGranted by settings.permUsageFlow.collectAsState(initial = false)
                val deviceAdminGranted by settings.permAdminFlow.collectAsState(initial = false)
                val vpnGranted by settings.permVpnFlow.collectAsState(initial = false)
                val notificationGranted by settings.permNotificationsFlow.collectAsState(initial = false)

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            PermissionUtils.syncPermissionsWithCache(context, settings)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                val vpnLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        PermissionUtils.syncPermissionsWithCache(context, settings)
                    }
                }

                val adminLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    PermissionUtils.syncPermissionsWithCache(context, settings)
                }

                OnboardingScreen(
                    accessibilityGranted = accessibilityGranted,
                    vpnGranted = vpnGranted,
                    deviceAdminGranted = deviceAdminGranted,
                    overlayGranted = overlayGranted,
                    usageAccessGranted = usageAccessGranted,
                    notificationGranted = notificationGranted,
                    onComplete = {
                        scope.launch { settings.setOnboardingComplete() }
                        navController.navigate("home") {
                            popUpTo("onboarding") { inclusive = true }
                        }
                    },
                    onRequestPermission = { key ->
                        when (key) {
                            "accessibility" -> {
                                AccessibilityUtils.openAccessibilitySettings(context)
                            }
                            "vpn" -> {
                                val intent = VpnService.prepare(context)
                                if (intent != null) {
                                    vpnLauncher.launch(intent)
                                } else {
                                    PermissionUtils.syncPermissionsWithCache(context, settings)
                                }
                            }
                            "device_admin" -> {
                                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(context, GuardianDeviceAdminReceiver::class.java))
                                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.device_admin_explanation))
                                }
                                adminLauncher.launch(intent)
                            }
                            "overlay" -> {
                                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                            "usage_access" -> {
                                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }
                            "notifications" -> {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(this)
                                    }
                                }
                            }
                        }
                    },
                )
            }

            composable("pin_setup") {
                PinSetupScreen(
                    existingPin = null,
                    onPinSet = { navController.popBackStack() },
                    onSkip = { navController.popBackStack() }
                )
            }

            composable("home") {
                val vm: HomeViewModel = viewModel()
                HomeScreen(
                    vm = vm,
                    onNavigateToPermissions = { navController.navigate("permissions") },
                    onNavigateToSettings = { navController.navigate("settings") }
                )
            }

            composable("social") {
                val vm: SocialViewModel = viewModel()
                SocialScreen(vm = vm)
            }

            composable("content") {
                PinGate(hasPinSet = hasPinSet, storedHash = pinHash) {
                    val vm: ContentViewModel = viewModel()
                    ContentScreen(vm = vm)
                }
            }

            composable("lists") {
                PinGate(hasPinSet = hasPinSet, storedHash = pinHash) {
                    val vm: ListsViewModel = viewModel()
                    ListsScreen(vm = vm)
                }
            }

            composable("permissions") {
                PermissionsScreen(onBack = { navController.popBackStack() })
            }

            composable("settings") {
                PinGate(hasPinSet = hasPinSet, storedHash = pinHash) {
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
                        onBack = { navController.popBackStack() }
                    )
                }
            }

            composable("profile") {
                val vm: ProfileViewModel = viewModel()
                ProfileScreen(vm = vm, onBack = { navController.popBackStack() })
            }

            composable("schedule") {
                PinGate(hasPinSet = hasPinSet, storedHash = pinHash) {
                    val vm: ScheduleViewModel = viewModel()
                    ScheduleScreen(vm = vm, onBack = { navController.popBackStack() })
                }
            }

            composable("time_limits") {
                val vm: TimeLimitsViewModel = viewModel()
                TimeLimitsScreen(vm = vm, onBack = { navController.popBackStack() })
            }

            composable("statistics") {
                val vm: StatisticsViewModel = viewModel()
                StatisticsScreen(vm = vm, onBack = { navController.popBackStack() })
            }

            composable("export_import") {
                ExportImportScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
fun BottomNav(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val items = listOf(
        BottomNavItem("home", Icons.Default.Shield, stringResource(R.string.nav_shield)),
        BottomNavItem("social", Icons.Default.PhoneAndroid, stringResource(R.string.nav_social)),
        BottomNavItem("content", Icons.Default.VisibilityOff, stringResource(R.string.nav_content)),
        BottomNavItem("lists", Icons.AutoMirrored.Filled.List, stringResource(R.string.nav_lists)),
        BottomNavItem("statistics", Icons.Default.BarChart, stringResource(R.string.nav_stats)),
        BottomNavItem("profile", Icons.Default.Person, stringResource(R.string.nav_profile))
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
