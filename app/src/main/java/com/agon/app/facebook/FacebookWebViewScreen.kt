package com.agon.app.facebook

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agon.app.R
import com.agon.app.ui.theme.*
import com.agon.app.utils.AssetLoader
import kotlin.math.roundToInt

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FacebookWebViewScreen(
    onBack: () -> Unit,
    viewModel: FacebookViewModel = viewModel()
) {
    val context = LocalContext.current
    val fbSettings by viewModel.settings.collectAsState()
    val fabState by viewModel.fabState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showSettings by remember { mutableStateOf(false) }
    var fabOffsetX by remember { mutableStateOf(0f) }
    var fabOffsetY by remember { mutableStateOf(0f) }

    val fabColor by animateColorAsState(
        targetValue = when {
            !fbSettings.blockerEnabled -> Color(0xFF64748B)
            fabState.isCommentPage -> Color(0xFFF59E0B)
            else -> Color(0xFF10B981)
        },
        label = "fabColor"
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.pauseWebView()
                Lifecycle.Event.ON_START -> viewModel.resumeWebView()
                Lifecycle.Event.ON_DESTROY -> viewModel.destroyWebView()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // WebView
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    val ws = settings
                    ws.javaScriptEnabled = true
                    ws.domStorageEnabled = true
                    ws.loadWithOverviewMode = true
                    ws.useWideViewPort = true
                    ws.builtInZoomControls = false
                    ws.setSupportZoom(false)
                    ws.userAgentString = ws.userAgentString.replace(
                        "wv", ""
                    )

                    val bridge = FacebookBridge(
                        onBlocked = { count -> viewModel.handleReelBlocked(count) },
                        onPerfWarning = { ms -> viewModel.handlePerfWarning(ms) }
                    )
                    addJavascriptInterface(bridge, "Android")

                    val script = AssetLoader.loadScript(ctx)

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            if (url != null) viewModel.onPageStarted(url)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (url != null && view != null) {
                                view.evaluateJavascript(script, null)
                                viewModel.onPageFinished(url)
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString() ?: return false
                            val host = request?.url?.host ?: return false

                            val forbiddenPatterns = listOf(
                                "play.google.com",
                                "apps.facebook.com",
                                "l.facebook.com",
                                "market://",
                                "www.facebook.com/help",
                                "www.facebook.com/settings"
                            )

                            for (pattern in forbiddenPatterns) {
                                if (host.contains(pattern, ignoreCase = true) || url.contains(pattern, ignoreCase = true)) {
                                    return true
                                }
                            }
                            return false
                        }
                    }

                    setDownloadListener { url, _, _, _, _ ->
                        if (url.contains("play.google.com", ignoreCase = true)) {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.toast_link_blocked),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            return@setDownloadListener
                        }
                    }

                    webChromeClient = WebChromeClient()

                    loadUrl("https://m.facebook.com")
                    viewModel.setWebView(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Back button (top-left)
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .size(40.dp),
            shape = CircleShape,
            color = surface.copy(alpha = 0.9f),
            shadowElevation = 4.dp,
            onClick = {
                if (viewModel.canGoBack()) {
                    viewModel.navigateBack()
                } else {
                    onBack()
                }
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.contentdesc_back),
                    tint = text,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Refresh button (top-right)
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(40.dp),
            shape = CircleShape,
            color = surface.copy(alpha = 0.9f),
            shadowElevation = 4.dp,
            onClick = { viewModel.refresh() }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.contentdesc_refresh),
                    tint = text,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Draggable FAB
        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset { IntOffset(fabOffsetX.roundToInt(), fabOffsetY.roundToInt()) }
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        fabOffsetX += dragAmount.x
                        fabOffsetY += dragAmount.y
                    }
                },
            shape = RoundedCornerShape(16.dp),
            color = fabColor,
            shadowElevation = 8.dp,
            onClick = { showSettings = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = stringResource(R.string.contentdesc_blocker),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                if (fabState.blockedCount > 0) {
                    Text(
                        text = "${fabState.blockedCount}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.contentdesc_settings_webview),
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    if (showSettings) {
        FacebookSettingsSheet(
            settings = fbSettings,
            onToggleBlocker = { viewModel.toggleBlocker() },
            onSetThreshold = { viewModel.setConfidenceThreshold(it) },
            onSetScheduleEnabled = { viewModel.setScheduleEnabled(it) },
            onSetScheduleStart = { viewModel.setScheduleStartHour(it) },
            onSetScheduleEnd = { viewModel.setScheduleEndHour(it) },
            onSetFriendProtection = { viewModel.setFriendProtection(it) },
            onDismiss = { showSettings = false }
        )
    }
}
