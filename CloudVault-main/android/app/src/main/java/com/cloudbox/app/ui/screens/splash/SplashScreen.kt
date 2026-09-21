package com.cloudbox.app.ui.screens.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudbox.app.data.local.TokenManager
import com.cloudbox.app.ui.components.CloudboxScreen
import com.cloudbox.app.ui.components.CloudLogo
import com.cloudbox.app.ui.components.PillButton
import com.cloudbox.app.ui.theme.CloudMuted
import com.cloudbox.app.ui.theme.CloudNavy

@Composable
fun SplashScreen(
    onGetStarted: () -> Unit,
    onLogin: () -> Unit
) {
    CloudboxScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CloudLogo(size = 120)
            Spacer(Modifier.height(28.dp))
            Text(
                text = "Cloudbox",
                color = CloudNavy,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Your files. Anywhere. Always with you.",
                color = CloudMuted,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(40.dp))
            PillButton(
                text = "Get Started",
                onClick = {
                    TokenManager.markOnboardingSeen()
                    onGetStarted()
                }
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = {
                TokenManager.markOnboardingSeen()
                onLogin()
            }) {
                Text("Log In", color = CloudNavy, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}