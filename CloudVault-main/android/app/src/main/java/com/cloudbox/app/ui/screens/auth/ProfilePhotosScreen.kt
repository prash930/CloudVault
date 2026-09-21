package com.cloudbox.app.ui.screens.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cloudbox.app.BuildConfig
import com.cloudbox.app.ui.components.BackRow
import com.cloudbox.app.ui.components.CloudboxScreen
import com.cloudbox.app.ui.components.CloudLogo
import com.cloudbox.app.ui.components.PillButton
import com.cloudbox.app.ui.screens.home.HomeViewModel
import com.cloudbox.app.ui.theme.CloudBlue
import com.cloudbox.app.ui.theme.CloudBorder
import com.cloudbox.app.ui.theme.CloudCard
import com.cloudbox.app.ui.theme.CloudMuted
import com.cloudbox.app.ui.theme.CloudNavy
import com.cloudbox.app.ui.theme.CloudSoft
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ProfilePhotosScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    var pickingSlot by remember { mutableIntStateOf(0) }
    var uploadingSlot by remember { mutableIntStateOf(0) }
    val uiState by viewModel.uiState.collectAsState()
    val hasAvatar1 = uiState.hasAvatar1
    val hasAvatar2 = uiState.hasAvatar2

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            uploadingSlot = pickingSlot
            viewModel.uploadAvatar(context.contentResolver, it, pickingSlot)
        }
    }

    fun avatarUrl(slot: Int) = BuildConfig.BASE_URL + "auth/avatar/$slot"

    CloudboxScreen {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            LaunchedEffect(Unit) {
                viewModel.refreshMe()
            }
            BackRow("Add Profile Photos", onBack)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Make it personal.",
                color = CloudNavy,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Add a photo to your account. You can add up to two.",
                color = CloudMuted,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(28.dp))

            Row(Modifier.fillMaxWidth()) {
                AvatarPicker(
                    label = "Photo 1",
                    hasAvatar = hasAvatar1,
                    isUploading = uploadingSlot == 1,
                    url = avatarUrl(1),
                    onClick = {
                        pickingSlot = 1
                        picker.launch("image/*")
                    },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(24.dp))
                AvatarPicker(
                    label = "Photo 2",
                    hasAvatar = hasAvatar2,
                    isUploading = uploadingSlot == 2,
                    url = avatarUrl(2),
                    onClick = {
                        pickingSlot = 2
                        picker.launch("image/*")
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.weight(1f))

            if (uiState.error != null) {
                Text(
                    uiState.error!!,
                    color = CloudBlue,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                )
            }

            PillButton(
                text = "Continue",
                onClick = onDone,
                enabled = hasAvatar1 || hasAvatar2
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text("Skip for now", color = CloudMuted)
            }
        }
    }
}

@Composable
private fun AvatarPicker(
    label: String,
    hasAvatar: Boolean,
    isUploading: Boolean,
    url: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .clip(CircleShape)
                .background(CloudCard)
                .border(3.dp, if (hasAvatar) CloudBlue else CloudBorder, CircleShape)
                .clickable(enabled = !isUploading, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (isUploading) {
                CircularProgressIndicator(color = CloudBlue, modifier = Modifier.size(40.dp))
            } else if (hasAvatar) {
                AsyncImage(
                    model = url,
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                )
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(CloudBlue)
                        .padding(4.dp)
                )
            } else {
                Icon(Icons.Default.Person, contentDescription = null, tint = CloudMuted, modifier = Modifier.size(56.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = CloudNavy, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = null,
                tint = CloudBlue,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}