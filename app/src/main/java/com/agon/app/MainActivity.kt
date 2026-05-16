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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.agon.app.facebook.FacebookWebViewScreen
import com.agon.app.services.AIExplorerService
import com.agon.app.ui.screens.*
import com.agon.app.ui.theme.*

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_EXPORTED else 0
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
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isBottomNavVisible = currentRoute in listOf("home", "social", "content", "lists")

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
            startDestination = "home",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("home") { 
                HomeScreen(
                    onNavigateToPermissions = { navController.navigate("permissions") },
                    onNavigateToSettings = { navController.navigate("settings") }
                ) 
            }
            composable("social") { SocialScreen(onLaunchFacebookWrapper = { navController.navigate("facebook_webview") }) }
            composable("facebook_webview") {
                FacebookWebViewScreen(onBack = { navController.popBackStack() })
            }
            composable("content") { ContentScreen() }
            composable("lists") { ListsScreen() }
            composable("permissions") { 
                PermissionsScreen(
                    onBack = { navController.popBackStack() }
                ) 
            }
            composable("settings") { 
                SettingsScreen(
                    onNavigateToSocial = { navController.navigate("social") },
                    onNavigateToContent = { navController.navigate("content") },
                    onNavigateToLists = { navController.navigate("lists") },
                    onNavigateToPermissions = { navController.navigate("permissions") },
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

    Surface(
        color = surface,
        border = BorderStroke(1.dp, cardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomBottomNavItem(
                icon = Icons.Default.Shield,
                label = "Shield",
                isSelected = currentRoute == "home",
                onClick = {
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
            CustomBottomNavItem(
                icon = Icons.Default.PhoneAndroid,
                label = "Social",
                isSelected = currentRoute == "social",
                onClick = {
                    navController.navigate("social") {
                        popUpTo("home")
                    }
                }
            )
            CustomBottomNavItem(
                icon = Icons.Default.VisibilityOff,
                label = "Content",
                isSelected = currentRoute == "content",
                onClick = {
                    navController.navigate("content") {
                        popUpTo("home")
                    }
                }
            )
            CustomBottomNavItem(
                icon = Icons.Default.List,
                label = "Lists",
                isSelected = currentRoute == "lists",
                onClick = {
                    navController.navigate("lists") {
                        popUpTo("home")
                    }
                }
            )
        }
    }
}

@Composable
fun CustomBottomNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = if (isSelected) primary else textMuted
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (isSelected) primary.copy(alpha = 0.15f) else Color.Transparent)
                .padding(horizontal = 20.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, color = color, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}
