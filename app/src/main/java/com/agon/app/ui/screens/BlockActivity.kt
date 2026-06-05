package com.agon.app.ui.screens

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.agon.app.R
import com.agon.app.LanguageManager

class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // FLAG_SECURE blocks screenshots and the screen-record API.
        // On the block screen we never want the user (or another app
        // that observes the screen) to be able to capture the reason
        // text — it would let them screenshot/record the bypass.
        // The Activity restart / Recents thumbnail is also blanked.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goToHome()
            }
        })

        val appName = intent.getStringExtra("APP_NAME") ?: getString(R.string.block_app_name_default)
        val blockReason = intent.getStringExtra("BLOCK_REASON") ?: ""
        val isTimeLimit = blockReason == "time_limit"
        val isShortVideo = blockReason == "shorts_reels_block"
        val isKeyword = blockReason == "keyword_block"
        val isAiTemp = blockReason == "ai_temp_block"
        val isBedtime = blockReason == "bedtime"

        setContent {
            BlockScreen(
                appName = appName,
                isTimeLimit = isTimeLimit,
                isShortVideo = isShortVideo,
                isKeyword = isKeyword,
                isAiTemp = isAiTemp,
                isBedtime = isBedtime,
                onGoBack = { goToHome() }
            )
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LanguageManager.apply(base))
    }

    private fun goToHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}

@Composable
private fun BlockScreen(
    appName: String,
    isTimeLimit: Boolean,
    isShortVideo: Boolean = false,
    isKeyword: Boolean = false,
    isAiTemp: Boolean = false,
    isBedtime: Boolean = false,
    onGoBack: () -> Unit
) {
    val context = LocalContext.current
    // Bedtime grayscale: desaturate the whole screen. Mirrors the
    // iOS "Grayscale at Bedtime" pattern (Screen Time + Family Link).
    val containerColor = if (isBedtime) Color(0xFF1A1A1A) else Color.Black
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF4444).copy(alpha = 0.2f))
                    .border(3.dp, Color(0xFFFF4444).copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = context.getString(R.string.contentdesc_blocked),
                    tint = Color(0xFFFF4444),
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = context.getString(R.string.screen_block_title),
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = when {
                           isTimeLimit -> context.getString(R.string.block_reason_time_limit, appName)
                           isShortVideo -> context.getString(R.string.block_reason_short_video, appName)
                           isKeyword -> context.getString(R.string.block_reason_keyword, appName)
                           isAiTemp -> context.getString(R.string.block_reason_ai_temp, appName)
                           isBedtime -> context.getString(R.string.block_reason_bedtime, appName)
                           else -> context.getString(R.string.block_reason_normal, appName)
                       },
                color = Color(0xFFAAAAAA),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Bedtime grayscale is active — show a hint so the user
            // understands why the screen is monochrome.
            if (isBedtime) {
                Text(
                    text = context.getString(R.string.bedtime_grayscale_hint),
                    color = Color(0xFF7C3AED),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Bonus-time release valve: only show on schedule/bedtime
            // blocks (not on content blocks like porn).
            if (isTimeLimit || isBedtime) {
                OutlinedButton(
                    onClick = onGoBack,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFAAAAAA)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFAAAAAA)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = context.getString(R.string.btn_bonus_time, com.agon.app.utils.BonusTime.DEFAULT_GRANT_MINUTES),
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onGoBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4F8EF7)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = context.getString(R.string.btn_go_back),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
