package com.cloudbox.app.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudbox.app.data.api.models.CloudFile
import com.cloudbox.app.ui.theme.CloudBackground
import com.cloudbox.app.ui.theme.CloudBlue
import com.cloudbox.app.ui.theme.CloudBorder
import com.cloudbox.app.ui.theme.CloudCard
import com.cloudbox.app.ui.theme.CloudMuted
import com.cloudbox.app.ui.theme.CloudNavy
import com.cloudbox.app.ui.theme.CloudSky
import com.cloudbox.app.ui.theme.CloudSoft
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

enum class MainTab(val label: String) {
    Home("Home"),
    Files("Files"),
    Shared("Shared")
}

@Composable
fun CloudboxScreen(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFF8FBFF), CloudBackground, Color(0xFFEAF3FF))
                )
            )
    ) {
        content()
    }
}

@Composable
fun CloudLogo(size: Int = 88) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(listOf(CloudSky, CloudBlue))
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Cloud,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size((size * 0.55).dp)
        )
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(28.dp),
        colors = ButtonDefaults.buttonColors(containerColor = CloudBlue, contentColor = Color.White)
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun CloudTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = CloudMuted) },
        leadingIcon = { Icon(leadingIcon, contentDescription = null, tint = CloudMuted) },
        trailingIcon = trailing,
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = CloudCard,
            unfocusedContainerColor = CloudCard,
            focusedBorderColor = CloudBlue,
            unfocusedBorderColor = CloudBorder,
            cursorColor = CloudBlue
        )
    )
}

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String = "Search files and folders...") {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = CloudMuted) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CloudMuted) },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = CloudCard,
            unfocusedContainerColor = CloudCard,
            focusedBorderColor = CloudBlue,
            unfocusedBorderColor = Color.Transparent,
            cursorColor = CloudBlue
        )
    )
}

@Composable
fun BackRow(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = CloudNavy)
        }
        Text(title, color = CloudNavy, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CloudBottomBar(selected: MainTab, onSelect: (MainTab) -> Unit) {
    NavigationBar(containerColor = CloudCard, tonalElevation = 8.dp) {
        MainTab.entries.forEach { tab ->
            val selectedTab = selected == tab
            NavigationBarItem(
                selected = selectedTab,
                onClick = { onSelect(tab) },
                icon = {
                    Icon(
                        imageVector = when (tab) {
                            MainTab.Home -> Icons.Filled.Home
                            MainTab.Files -> Icons.Filled.Folder
                            MainTab.Shared -> Icons.Filled.PeopleAlt
                        },
                        contentDescription = tab.label
                    )
                },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = CloudBlue,
                    selectedTextColor = CloudBlue,
                    unselectedIconColor = CloudMuted,
                    unselectedTextColor = CloudMuted,
                    indicatorColor = CloudSoft
                )
            )
        }
    }
}

@Composable
fun FileGlyph(file: CloudFile, modifier: Modifier = Modifier) {
    val (icon, tint, bg) = fileVisual(file)
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun FileGlyphFrom(name: String, mime: String?, modifier: Modifier = Modifier) {
    val (icon, tint, bg) = fileVisualFrom(name, mime)
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

fun fileVisual(file: CloudFile): Triple<ImageVector, Color, Color> {
    if (file.is_folder) return Triple(Icons.Default.Folder, Color(0xFF2F80FF), Color(0xFFE3F0FF))
    return fileVisualFrom(file.filename, file.mime_type)
}

fun fileVisualFrom(name: String, mime: String?): Triple<ImageVector, Color, Color> {
    val m = mime.orEmpty()
    val lower = name.lowercase()
    return when {
        m.startsWith("image/") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ->
            Triple(Icons.Default.Image, Color(0xFF1E88E5), Color(0xFFE3F2FD))
        m.startsWith("video/") || lower.endsWith(".mp4") ->
            Triple(Icons.Default.VideoLibrary, Color(0xFF8E24AA), Color(0xFFF3E5F5))
        lower.endsWith(".pdf") || m == "application/pdf" ->
            Triple(Icons.Default.PictureAsPdf, Color(0xFFE53935), Color(0xFFFFEBEE))
        lower.endsWith(".zip") || lower.endsWith(".rar") ->
            Triple(Icons.Default.Archive, Color(0xFFFB8C00), Color(0xFFFFF3E0))
        m.startsWith("text/") || lower.endsWith(".txt") || lower.endsWith(".doc") ->
            Triple(Icons.Default.Description, Color(0xFF607D8B), Color(0xFFECEFF1))
        else -> Triple(Icons.Default.InsertDriveFile, Color(0xFF5C6BC0), Color(0xFFE8EAF6))
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceAtMost(units.lastIndex)
    return String.format(Locale.US, "%.1f %s", bytes / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}

fun formatDate(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return try {
        val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(value.take(19))
        SimpleDateFormat("MMM d, yyyy", Locale.US).format(parsed!!)
    } catch (_: Exception) {
        value.take(10)
    }
}

fun formatDateTime(value: String?): String {
    if (value.isNullOrBlank()) return ""
    return try {
        val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(value.take(19))
        SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.US).format(parsed!!)
    } catch (_: Exception) {
        value
    }
}

fun relativeDate(value: String?): String {
    val formatted = formatDate(value)
    return formatted.ifBlank { "Recently" }
}

@Composable
fun ActionTile(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(CloudSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = CloudBlue, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = CloudNavy, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun FileRowCard(
    file: CloudFile,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    selected: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) CloudSoft else CloudCard)
            .clickable(onClick = onClick)
            .padding(12.dp)
        ,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        FileGlyph(file)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(file.filename, color = CloudNavy, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, color = CloudMuted, fontSize = 12.sp)
        }
    }
}
