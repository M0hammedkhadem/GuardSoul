package com.agon.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.agon.app.ui.screens.AdvancedScreen
import com.agon.app.ui.screens.HomeScreen
import com.agon.app.ui.screens.JournalScreen
import com.agon.app.ui.screens.ListsScreen
import com.agon.app.ui.screens.PermissionsScreen
import com.agon.app.ui.screens.ProgressScreen
import com.agon.app.ui.screens.ProtectionScreen
import com.agon.app.ui.screens.SafeSearchScreen
import com.agon.app.ui.screens.SettingsScreen
import com.agon.app.ui.theme.AgonAppTheme
import com.agon.app.ui.theme.GreenAccent
import com.agon.app.ui.theme.GreenPill
import com.agon.app.ui.theme.TextSecondary
import com.agon.app.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AgonAppTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    MainApp()
                }
            }
        }
    }
}

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val navItems = listOf(
    NavItem("home", "الرئيسية", Icons.Outlined.Home),
    NavItem("protection", "الحماية", Icons.Outlined.Shield),
    NavItem("lists", "القوائم", Icons.Outlined.Search),
    NavItem("advanced", "متقدم", Icons.Outlined.Tune),
    NavItem("journal", "المفكرة", Icons.AutoMirrored.Outlined.MenuBook),
    NavItem("progress", "التقدم", Icons.Outlined.EmojiEvents),
)

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val vm: MainViewModel = hiltViewModel()
    val snackbarHostState = remember { SnackbarHostState() }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = navItems.any { it.route == currentRoute }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { if (showBottomBar) BottomNav(navController, currentRoute) },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("home") {
                HomeScreen(
                    vm = vm,
                    snackbarHostState = snackbarHostState,
                    onOpenSettings = { navController.navigate("settings") },
                    onOpenPermissions = { navController.navigate("permissions") },
                    onOpenProgress = {
                        navController.navigate("progress") {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable("protection") {
                ProtectionScreen(
                    vm = vm,
                    snackbarHostState = snackbarHostState,
                    onOpenSafeSearch = { navController.navigate("safesearch") },
                )
            }
            composable("lists") { ListsScreen(vm = vm, snackbarHostState = snackbarHostState) }
            composable("advanced") { AdvancedScreen(vm = vm, snackbarHostState = snackbarHostState) }
            composable("journal") { JournalScreen(vm = vm, snackbarHostState = snackbarHostState) }
            composable("progress") { ProgressScreen(vm = vm) }
            composable("settings") {
                SettingsScreen(
                    vm = vm,
                    snackbarHostState = snackbarHostState,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("safesearch") {
                SafeSearchScreen(
                    vm = vm,
                    snackbarHostState = snackbarHostState,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("permissions") {
                PermissionsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
fun BottomNav(navController: NavHostController, currentRoute: String?) {
    NavigationBar(containerColor = Color(0xFF0E1822)) {
        navItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = {
                    Text(
                        item.label,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                selected = selected,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = GreenAccent,
                    selectedTextColor = GreenAccent,
                    indicatorColor = GreenPill,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                ),
                onClick = {
                    if (!selected) {
                        navController.navigate(item.route) {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            )
        }
    }
}
