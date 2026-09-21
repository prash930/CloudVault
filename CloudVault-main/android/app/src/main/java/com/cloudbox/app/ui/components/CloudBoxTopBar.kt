package com.cloudbox.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudbox.app.ui.theme.CloudBackground
import com.cloudbox.app.ui.theme.CloudBlue
import com.cloudbox.app.ui.theme.CloudNavy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBoxTopBar(
    title: String,
    onNavigateBack: (() -> Unit)? = null,
    onLogout: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Cloud,
                    contentDescription = null,
                    tint = CloudBlue,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, color = CloudNavy, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        },
        navigationIcon = {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = CloudNavy)
                }
            }
        },
        actions = {
            if (onLogout != null) {
                IconButton(onClick = { showMenu = !showMenu }) {
                    Icon(
                        Icons.Default.AccountCircle,
                        contentDescription = "Account",
                        tint = CloudBlue,
                        modifier = Modifier.size(28.dp).clip(CircleShape)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Logout, contentDescription = null, tint = CloudBlue)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Logout")
                            }
                        },
                        onClick = {
                            showMenu = false
                            onLogout()
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = CloudBackground,
            titleContentColor = CloudNavy,
            actionIconContentColor = CloudNavy
        )
    )
}