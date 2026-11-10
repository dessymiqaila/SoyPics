package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SteelBlueAccent

@Composable
fun SettingsDialog(
    currentTheme: String,
    currentSlideOrientation: String,
    currentSmoothnessMs: Int,
    currentShuffleEnabled: Boolean,
    onThemeChanged: (String) -> Unit,
    onSlideOrientationChanged: (String) -> Unit,
    onSmoothnessChanged: (Int) -> Unit,
    onShuffleChanged: (Boolean) -> Unit,
    onReshuffleNow: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var themeMode by remember { mutableStateOf(currentTheme) }
    var slideOrientation by remember { mutableStateOf(currentSlideOrientation) }
    var smoothnessMs by remember { mutableFloatStateOf(currentSmoothnessMs.toFloat()) }
    var shuffleEnabled by remember { mutableStateOf(currentShuffleEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("settings_dialog"),
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Preferences & Settings",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Section 1: App Theme
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "THEME (TEMA TAMPILAN)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = SteelBlueAccent
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeOptionCard(
                            title = "Dark",
                            icon = Icons.Default.DarkMode,
                            isSelected = themeMode == "DARK",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                themeMode = "DARK"
                                onThemeChanged("DARK")
                            }
                        )
                        ThemeOptionCard(
                            title = "Light",
                            icon = Icons.Default.LightMode,
                            isSelected = themeMode == "LIGHT",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                themeMode = "LIGHT"
                                onThemeChanged("LIGHT")
                            }
                        )
                        ThemeOptionCard(
                            title = "System",
                            icon = Icons.Default.SettingsBrightness,
                            isSelected = themeMode == "SYSTEM",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                themeMode = "SYSTEM"
                                onThemeChanged("SYSTEM")
                            }
                        )
                    }
                }

                // Section 2: Slide Orientation (Efek Geser)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "IMAGE SLIDE EFFECT (EFEK GESER)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = SteelBlueAccent
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SlideOptionCard(
                            title = "Vertical",
                            subtitle = "Up = Prev, Down = Next",
                            icon = Icons.Default.SwapVert,
                            isSelected = slideOrientation == "VERTICAL",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                slideOrientation = "VERTICAL"
                                onSlideOrientationChanged("VERTICAL")
                            }
                        )
                        SlideOptionCard(
                            title = "Horizontal",
                            subtitle = "Left = Prev, Right = Next",
                            icon = Icons.Default.SwapHoriz,
                            isSelected = slideOrientation == "HORIZONTAL",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                slideOrientation = "HORIZONTAL"
                                onSlideOrientationChanged("HORIZONTAL")
                            }
                        )
                    }
                }

                // Section 3: Transition Smoothness (Kelembutan Efek)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = SteelBlueAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "TRANSITION SMOOTHNESS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = SteelBlueAccent
                                )
                            )
                        }
                        Text(
                            text = "${smoothnessMs.toInt()} ms",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Slider(
                        value = smoothnessMs,
                        onValueChange = {
                            smoothnessMs = it
                        },
                        onValueChangeFinished = {
                            onSmoothnessChanged(smoothnessMs.toInt())
                        },
                        valueRange = 150f..800f,
                        steps = 12,
                        colors = SliderDefaults.colors(
                            thumbColor = SteelBlueAccent,
                            activeTrackColor = SteelBlueAccent,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PresetPill("Snappy", 200, smoothnessMs.toInt()) {
                            smoothnessMs = 200f
                            onSmoothnessChanged(200)
                        }
                        PresetPill("Smooth", 400, smoothnessMs.toInt()) {
                            smoothnessMs = 400f
                            onSmoothnessChanged(400)
                        }
                        PresetPill("Silky", 600, smoothnessMs.toInt()) {
                            smoothnessMs = 600f
                            onSmoothnessChanged(600)
                        }
                        PresetPill("Ultra", 800, smoothnessMs.toInt()) {
                            smoothnessMs = 800f
                            onSmoothnessChanged(800)
                        }
                    }
                }

                // Section 4: Random / Shuffle
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Randomize Order (Acak Total)",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Shuffles all photos in random sequence",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = shuffleEnabled,
                            onCheckedChange = {
                                shuffleEnabled = it
                                onShuffleChanged(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SteelBlueAccent
                            )
                        )
                    }

                    if (onReshuffleNow != null) {
                        OutlinedButton(
                            onClick = onReshuffleNow,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = SteelBlueAccent
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Reshuffle Viewer Now",
                                color = SteelBlueAccent,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SteelBlueAccent)
            ) {
                Text(
                    text = "Done",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
private fun ThemeOptionCard(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) SteelBlueAccent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val bgColor = if (isSelected) SteelBlueAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(width = if (isSelected) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(14.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isSelected) SteelBlueAccent else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
            color = if (isSelected) SteelBlueAccent else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SlideOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) SteelBlueAccent else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val bgColor = if (isSelected) SteelBlueAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .border(width = if (isSelected) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(14.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) SteelBlueAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isSelected) SteelBlueAccent else MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 13.sp
        )
    }
}

@Composable
private fun PresetPill(
    label: String,
    value: Int,
    currentValue: Int,
    onClick: () -> Unit
) {
    val isSelected = kotlin.math.abs(value - currentValue) < 50
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (isSelected) SteelBlueAccent.copy(alpha = 0.2f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .border(
                width = 1.dp,
                color = if (isSelected) SteelBlueAccent else Color.Transparent,
                shape = CircleShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) SteelBlueAccent else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
