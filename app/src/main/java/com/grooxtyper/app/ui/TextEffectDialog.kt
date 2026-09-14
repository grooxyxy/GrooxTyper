package com.grooxtyper.app.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grooxtyper.app.model.StackableTextConfig
import com.grooxtyper.app.model.TextEngine
import com.grooxtyper.app.model.TextStyleManager

@Composable
fun TextEffectDialog(
    initialConfig: StackableTextConfig = StackableTextConfig(),
    textEngine: TextEngine,
    onConfirm: (StackableTextConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val styleManager = remember { TextStyleManager(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Text & Font", "Color & Style", "Outline & Shadow", "Presets")

    var textContent by remember { mutableStateOf(initialConfig.text) }
    var fontSizeVal by remember { mutableFloatStateOf(initialConfig.fontSize) }
    var wordSpacingVal by remember { mutableFloatStateOf(initialConfig.wordSpacing) }
    var letterSpacingVal by remember { mutableFloatStateOf(initialConfig.letterSpacing) }

    var textColorVal by remember { mutableIntStateOf(initialConfig.textColor) }
    var enableGradient by remember { mutableStateOf(initialConfig.hasGradient) }
    var gradStartVal by remember { mutableIntStateOf(initialConfig.gradientStartColor) }
    var gradEndVal by remember { mutableIntStateOf(initialConfig.gradientEndColor) }
    var blurRadiusVal by remember { mutableFloatStateOf(initialConfig.blurRadius) }

    var enableOutline by remember { mutableStateOf(initialConfig.hasOutline) }
    var outlineColorVal by remember { mutableIntStateOf(initialConfig.outlineColor) }
    var outlineWidthVal by remember { mutableFloatStateOf(initialConfig.outlineWidth) }

    var enableShadow by remember { mutableStateOf(initialConfig.hasShadow) }
    var shadowColorVal by remember { mutableIntStateOf(initialConfig.shadowColor) }
    var shadowDxVal by remember { mutableFloatStateOf(initialConfig.shadowDx) }
    var shadowDyVal by remember { mutableFloatStateOf(initialConfig.shadowDy) }
    var shadowBlurVal by remember { mutableFloatStateOf(initialConfig.shadowRadius) }

    var showColorPickerTarget by remember { mutableStateOf<String?>(null) }

    val currentConfig = StackableTextConfig(
        text = textContent,
        fontSize = fontSizeVal,
        textColor = textColorVal,
        hasOutline = enableOutline,
        outlineColor = outlineColorVal,
        outlineWidth = outlineWidthVal,
        hasShadow = enableShadow,
        shadowColor = shadowColorVal,
        shadowRadius = shadowBlurVal,
        shadowDx = shadowDxVal,
        shadowDy = shadowDyVal,
        hasGradient = enableGradient,
        gradientStartColor = gradStartVal,
        gradientEndColor = gradEndVal,
        blurRadius = blurRadiusVal,
        wordSpacing = wordSpacingVal,
        letterSpacing = letterSpacingVal
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("GrooxTyper Text Studio", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Live Canvas Preview Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1B1B1B))
                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
                        val canvas = drawContext.canvas.nativeCanvas
                        textEngine.renderTextToCanvas(
                            canvas = canvas,
                            config = currentConfig,
                            position = Offset(size.width / 4f, size.height / 2f + 10f),
                            scale = 0.6f
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Navigation Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp,
                    containerColor = Color(0xFF222222),
                    contentColor = Color(0xFFFF9800)
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontSize = 12.sp, color = if (selectedTab == index) Color(0xFFFF9800) else Color.LightGray) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tab Content Body
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (selectedTab) {
                        0 -> { // Text & Font
                            Column {
                                OutlinedTextField(
                                    value = textContent,
                                    onValueChange = { textContent = it },
                                    label = { Text("Text Content") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Font Size (${fontSizeVal.toInt()} px)", color = Color.LightGray, fontSize = 12.sp)
                                Slider(
                                    value = fontSizeVal,
                                    onValueChange = { fontSizeVal = it },
                                    valueRange = 16f..160f,
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9800), activeTrackColor = Color(0xFFFF9800))
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Word & Line Spacing Offset (${wordSpacingVal.toInt()} px)", color = Color.LightGray, fontSize = 12.sp)
                                Slider(
                                    value = wordSpacingVal,
                                    onValueChange = { wordSpacingVal = it },
                                    valueRange = -50f..50f,
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9800), activeTrackColor = Color(0xFFFF9800))
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Letter Spacing (${(letterSpacingVal * 100).toInt()}%)", color = Color.LightGray, fontSize = 12.sp)
                                Slider(
                                    value = letterSpacingVal,
                                    onValueChange = { letterSpacingVal = it },
                                    valueRange = -0.5f..1.0f,
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9800), activeTrackColor = Color(0xFFFF9800))
                                )
                            }
                        }
                        1 -> { // Color & Style
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Text Color", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(Color(textColorVal))
                                            .border(1.dp, Color.White, CircleShape)
                                            .clickable { showColorPickerTarget = "textColor" }
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Linear Gradient", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    Switch(checked = enableGradient, onCheckedChange = { enableGradient = it })
                                }

                                if (enableGradient) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Start Color", color = Color.LightGray, fontSize = 12.sp)
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(Color(gradStartVal))
                                                .border(1.dp, Color.White, CircleShape)
                                                .clickable { showColorPickerTarget = "gradStart" }
                                        )
                                        Text("End Color", color = Color.LightGray, fontSize = 12.sp)
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(Color(gradEndVal))
                                                .border(1.dp, Color.White, CircleShape)
                                                .clickable { showColorPickerTarget = "gradEnd" }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Soft Blur (${blurRadiusVal.toInt()} px)", color = Color.LightGray, fontSize = 12.sp)
                                Slider(
                                    value = blurRadiusVal,
                                    onValueChange = { blurRadiusVal = it },
                                    valueRange = 0f..20f,
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9800), activeTrackColor = Color(0xFFFF9800))
                                )
                            }
                        }
                        2 -> { // Outline & Shadow
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Outline Stroke", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    Switch(checked = enableOutline, onCheckedChange = { enableOutline = it })
                                }
                                if (enableOutline) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Outline Color", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(Color(outlineColorVal))
                                                .border(1.dp, Color.White, CircleShape)
                                                .clickable { showColorPickerTarget = "outlineColor" }
                                        )
                                    }
                                    Text("Outline Width (${outlineWidthVal.toInt()} px)", color = Color.LightGray, fontSize = 12.sp)
                                    Slider(
                                        value = outlineWidthVal,
                                        onValueChange = { outlineWidthVal = it },
                                        valueRange = 1f..35f,
                                        colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9800), activeTrackColor = Color(0xFFFF9800))
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Drop Shadow", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    Switch(checked = enableShadow, onCheckedChange = { enableShadow = it })
                                }
                                if (enableShadow) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Shadow Color", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(Color(shadowColorVal))
                                                .border(1.dp, Color.White, CircleShape)
                                                .clickable { showColorPickerTarget = "shadowColor" }
                                        )
                                    }
                                    Text("Shadow Offset (${shadowDxVal.toInt()}px, ${shadowDyVal.toInt()}px)", color = Color.LightGray, fontSize = 12.sp)
                                    Slider(
                                        value = shadowDxVal,
                                        onValueChange = { shadowDxVal = it; shadowDyVal = it },
                                        valueRange = -25f..25f,
                                        colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9800), activeTrackColor = Color(0xFFFF9800))
                                    )
                                    Text("Shadow Blur Radius (${shadowBlurVal.toInt()} px)", color = Color.LightGray, fontSize = 12.sp)
                                    Slider(
                                        value = shadowBlurVal,
                                        onValueChange = { shadowBlurVal = it },
                                        valueRange = 0f..30f,
                                        colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9800), activeTrackColor = Color(0xFFFF9800))
                                    )
                                }
                            }
                        }
                        3 -> { // Presets
                            Column {
                                Text("Style Presets", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                styleManager.savedStyles.forEach { style ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF333333))
                                            .clickable {
                                                val cfg = style.textConfig
                                                textColorVal = cfg.textColor
                                                enableOutline = cfg.hasOutline
                                                outlineColorVal = cfg.outlineColor
                                                outlineWidthVal = cfg.outlineWidth
                                                enableShadow = cfg.hasShadow
                                                shadowColorVal = cfg.shadowColor
                                                shadowDxVal = cfg.shadowDx
                                                shadowDyVal = cfg.shadowDy
                                                shadowBlurVal = cfg.shadowRadius
                                                enableGradient = cfg.hasGradient
                                                gradStartVal = cfg.gradientStartColor
                                                gradEndVal = cfg.gradientEndColor
                                                wordSpacingVal = cfg.wordSpacing
                                                letterSpacingVal = cfg.letterSpacing
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(style.name, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                        Text("Apply", color = Color(0xFFFF9800), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(currentConfig) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
            ) {
                Text("Render Text to Layer", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF2A2A2A)
    )

    if (showColorPickerTarget != null) {
        val initColor = when (showColorPickerTarget) {
            "textColor" -> textColorVal
            "gradStart" -> gradStartVal
            "gradEnd" -> gradEndVal
            "outlineColor" -> outlineColorVal
            "shadowColor" -> shadowColorVal
            else -> AndroidColor.WHITE
        }

        ColorPickerDialog(
            initialColor = initColor,
            onColorSelected = { picked ->
                when (showColorPickerTarget) {
                    "textColor" -> textColorVal = picked
                    "gradStart" -> gradStartVal = picked
                    "gradEnd" -> gradEndVal = picked
                    "outlineColor" -> outlineColorVal = picked
                    "shadowColor" -> shadowColorVal = picked
                }
            },
            onDismiss = { showColorPickerTarget = null }
        )
    }
}
