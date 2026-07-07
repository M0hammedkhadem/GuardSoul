package com.agon.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.agon.app.ui.BlockActivity
import com.agon.app.ui.components.PinGate
import com.agon.app.ui.screens.*
import com.agon.app.ui.theme.*
import com.agon.app.utils.AccessibilityUtils
import com.agon.app.utils.PermissionUtils
import com.agon.app.utils.ServiceManager
import com.agon.app.viewmodel.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        (application as GuardianApp).setCurrentActivity(this)
        handleIntent(intent)
        setContent {
            AgonAppTheme {
                val app = application as GuardianApp
                val onboardingComplete by produceState<Boolean?>(initialValue = null) {
                    value = app.repository.getAppSettings().isOnboardingComplete()
                }
                val isComplete = onboardingComplete ?: false
                if (onboardingComplete != null) {
                    MainApp(initialOnboardingComplete = isComplete)
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(background),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = primary)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val uri = intent.data ?: return
        val url = uri.toString()

        val isReelsLink = url.contains("facebook.com/reel/") ||
            url.contains("fb.watch/") ||
            url.contains("facebook.com/share/v/")

        if (isReelsLink) {
            val app = application as GuardianApp
            val settings = app.repository.getAppSettings()
            val shieldActive = settings.isShieldActiveSync()

            if (shieldActive) {
                val facebookMode = kotlinx.coroutines.runBlocking {
                    settings.facebookModeFlow.first()
                }
                if (facebookMode == "reels" || facebookMode == "full") {
                    val blockIntent = Intent(this@MainActivity, BlockActivity::class.java).apply {
                        putExtra(BlockActivity.EXTRA_APP_NAME, "Facebook Reels")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    }
                    startActivity(blockIntent)
                    finish()
                }
            }
        }
    }

    override fun onDestroy() {
        (application as GuardianApp).setCurrentActivity(null)
        super.onDestroy()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LanguageManager.apply(base))
    }
}

@Composable
fun MainApp(
    initialOnboardingComplete: Boolean = false,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as GuardianApp
    val settings = app.repository.getAppSettings()
    val scope = rememberCoroutineScope()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isBottomNavVisible = currentRoute in listOf("home", "lists", "social", "content")

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

                LaunchedEffect(Unit) {
                    PermissionUtils.syncPermissionsWithCache(context, settings)
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            PermissionUtils.syncPermissionsWithCache(context, settings)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                OnboardingScreen(
                    accessibilityGranted = accessibilityGranted,
                    initialLanguage = LanguageManager.currentLanguageCode,
                    onSetName = { name ->
                        scope.launch {
                            if (name.isNotBlank()) settings.setProfileName(name)
                        }
                    },
                    onSetLanguage = { lang ->
                        LanguageManager.setLanguage(context, lang)
                    },
                    onRequestLanguageChange = { _, lang ->
                        LanguageManager.setLanguage(context, lang)
                        (context as Activity).recreate()
                    },
                    onComplete = {
                        scope.launch {
                            settings.setOnboardingComplete()
                            (context as Activity).recreate()
                        }
                    },
                    onRequestPermission = { key ->
                        when (key) {
                            "accessibility" -> {
                                AccessibilityUtils.openAccessibilitySettings(context)
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

            composable("lists") {
                val vm: ListsViewModel = viewModel()
                ListsScreen(vm = vm)
            }

            composable("content") {
                val vm: ContentViewModel = viewModel()
                ContentScreen(vm = vm)
            }

            composable("social") {
                PinGate(hasPinSet = hasPinSet, storedHash = pinHash) {
                    val vm: SocialViewModel = viewModel()
                    SocialScreen(vm = vm)
                }
            }

            composable("permissions") {
                PermissionsScreen(onBack = { navController.popBackStack() })
            }

            composable("settings") {
                val vm: SettingsViewModel = viewModel()
                PinGate(hasPinSet = hasPinSet, storedHash = pinHash) {
                    SettingsScreen(vm = vm,
                        onNavigateToSocial = { navController.navigate("social") },
                        onNavigateToPermissions = { navController.navigate("permissions") },
                        onNavigateToPinSetup = { navController.navigate("pin_setup") },
                        onBack = { navController.popBackStack() }
                    )
                }
            }


        }
    }
}

@Composable
fun BottomNav(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val layoutDirection = LocalLayoutDirection.current

    // Unified visual order: Lists | Social | Shield (Shield always far right)
    // In RTL, Row lays out right-to-left, so we reverse the list for RTL
    val items = if (layoutDirection == LayoutDirection.Rtl) {
        listOf(
            BottomNavItem("home", Icons.Default.Shield, stringResource(R.string.nav_shield)),
            BottomNavItem("social", Icons.Default.PhoneAndroid, stringResource(R.string.nav_social)),
            BottomNavItem("content", Icons.Default.Block, stringResource(R.string.nav_content)),
            BottomNavItem("lists", Icons.AutoMirrored.Filled.List, stringResource(R.string.nav_lists)),
        )
    } else {
        listOf(
            BottomNavItem("lists", Icons.AutoMirrored.Filled.List, stringResource(R.string.nav_lists)),
            BottomNavItem("content", Icons.Default.Block, stringResource(R.string.nav_content)),
            BottomNavItem("social", Icons.Default.PhoneAndroid, stringResource(R.string.nav_social)),
            BottomNavItem("home", Icons.Default.Shield, stringResource(R.string.nav_shield)),
        )
    }
    Surface(
        color = surface,
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
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
