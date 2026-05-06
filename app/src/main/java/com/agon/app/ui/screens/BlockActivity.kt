package com.agon.app.ui.screens

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.ui.theme.*

class BlockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appName = intent.getStringExtra("APP_NAME") ?: "This App"
        
        setContent {
            AgonAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(danger.copy(alpha = 0.15f))
                                .border(2.dp, danger.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Blocked",
                                tint = danger,
                                modifier = Modifier.size(50.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Text(
                            text = "App Blocked",
                            color = text,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "$appName is currently blocked by Guardian.",
                            color = textSecondary,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(48.dp))
                        
                        Button(
                            onClick = { 
                                finish() 
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primary),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Text("Go Back", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}