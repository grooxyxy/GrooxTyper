package com.grooxtyper.app.ui

import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grooxtyper.app.model.FontManager
import com.grooxtyper.app.model.StackableTextConfig

enum class ColorPickerTarget {
    TEXT_COLOR,
    OUTLINE_COLOR,
    SHADOW_COLOR,
    OUTER_GLOW_COLOR,
    INNER_GLOW_COLOR,
    GRADIENT_START,
    GRADIENT_END,
    BG_COLOR
}

@Composable
fun TextPanel(
    initialConfig: StackableTextConfig,
    onConfirm: (StackableTextConfig) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val fontManager = remember { FontManager(context) }
    var availableFonts by remember { mutableStateOf(fontManager.getAvailableFonts()) }

    var textInput by remember { mutableStateOf(initialConfig.text) }
    var fontSizeVal by remember { mutableFloatStateOf(initialConfig.fontSize) }
    var textColorVal by remember { mutableIntStateOf(initialConfig.textColor) }

    // 1. Outer Stroke / Outline (px)
    var enableOutline by remember { mutableStateOf(initialConfig.hasOutline) }
    var outlineColorVal by remember { mutableIntStateOf(initialConfig.outlineColor) }
    var outlineWidthPx by remember { mutableFloatStateOf(initialConfig.outlineWidthPx) }

    // 2. Drop Shadow (px distance, px blur)
    var enableShadow by remember { mutableStateOf(initialConfig.hasShadow) }
    var shadowColorVal by remember { mutableIntStateOf(initialConfig.shadowColor) }
    var shadowDxPx by remember { mutableFloatStateOf(initialConfig.shadowDxPx) }
    var shadowDyPx by remember { mutableFloatStateOf(initialConfig.shadowDyPx) }
    var shadowRadiusPx by remember { mutableFloatStateOf(initialConfig.shadowRadiusPx) }

    // 3. Outer Glow (px radius)
    var enableOuterGlow by remember { mutableStateOf(initialConfig.hasOuterGlow) }
    var outerGlowColorVal by remember { mutableIntStateOf(initialConfig.outerGlowColor) }
    var outerGlowRadiusPx by remember { mutableFloatStateOf(initialConfig.outerGlowRadiusPx) }

    // 4. Inner Glow (px size)
    var enableInnerGlow by remember { mutableStateOf(initialConfig.hasInnerGlow) }
    var innerGlowColorVal by remember { mutableIntStateOf(initialConfig.innerGlowColor) }
    var innerGlowSizePx by remember { mutableFloatStateOf(initialConfig.innerGlowSizePx) }

    // 5. Gradient Fill
    var enableGradient by remember { mutableStateOf(initialConfig.hasGradient) }
    var gradStartColorVal by remember { mutableIntStateOf(initialConfig.gradientStartColor) }
    var gradEndColorVal by remember { mutableIntStateOf(initialConfig.gradientEndColor) }

    // 6. Background Banner (px corner, px padding)
    var enableBgBanner by remember { mutableStateOf(initialConfig.hasBackgroundBanner) }
    var bgColorVal by remember { mutableIntStateOf(initialConfig.backgroundColor) }
    var bgCornerRadiusPx by remember { mutableFloatStateOf(initialConfig.backgroundCornerRadiusPx) }
    var bgPaddingPx by remember { mutableFloatStateOf(initialConfig.backgroundPaddingPx) }

    // 7. Spacing Strictly in PX (Pixel Metrics)
    var wordSpacingPx by remember { mutableFloatStateOf(initialConfig.wordSpacingPx) }
    var letterSpacingPx by remember { mutableFloatStateOf(initialConfig.letterSpacingPx) }
    var lineSpacingPx by remember { mutableFloatStateOf(initialConfig.lineSpacingPx) }

    var selectedTypeface by remember { mutableStateOf(initialConfig.typeface) }
    var selectedFontName by remember { mutableStateOf(initialConfig.fontName) }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Text & Font", "Color & Spacing (px)", "Stroke & Shadow", "Glow & Gradient", "Banner & Presets")

    // Full Color Wheel State Integration
    var showWheelPicker by remember { mutableStateOf(false) }
    var activeColorTarget by remember { mutableStateOf(ColorPickerTarget.TEXT_COLOR) }

    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(it)
            inputStream?.let { stream ->
                val fileName = "custom_${System.currentTimeMillis()}.ttf"
                val importedTf = fontManager.importFontFile(stream, fileName)
                importedTf?.let { tf ->
                    availableFonts = fontManager.getAvailableFonts()
                    selectedTypeface = tf
                    selectedFontName = fileName
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(470.dp)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(Color(0xFF1E1E1E))
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Photoshop Text Layer Styles Studio", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Row {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.LightGray)
                    }
                    Button(
                        onClick = {
                            val newConfig = StackableTextConfig(
                                text = textInput,
                                fontSize = fontSizeVal,
                                textColor = textColorVal,
                                hasOutline = enableOutline,
                                outlineColor = outlineColorVal,
                                outlineWidthPx = outlineWidthPx,
                                hasShadow = enableShadow,
                                shadowColor = shadowColorVal,
                                shadowRadiusPx = shadowRadiusPx,
                                shadowDxPx = shadowDxPx,
                                shadowDyPx = shadowDyPx,
                                hasOuterGlow = enableOuterGlow,
                                outerGlowColor = outerGlowColorVal,
                                outerGlowRadiusPx = outerGlowRadiusPx,
                                hasInnerGlow = enableInnerGlow,
                                innerGlowColor = innerGlowColorVal,
                                innerGlowSizePx = innerGlowSizePx,
                                hasGradient = enableGradient,
                                gradientStartColor = gradStartColorVal,
                                gradientEndColor = gradEndColorVal,
                                hasBackgroundBanner = enableBgBanner,
                                backgroundColor = bgColorVal,
                                backgroundCornerRadiusPx = bgCornerRadiusPx,
                                backgroundPaddingPx = bgPaddingPx,
                                wordSpacingPx = wordSpacingPx,
                                letterSpacingPx = letterSpacingPx,
                                lineSpacingPx = lineSpacingPx,
                                fontName = selectedFontName,
                                typeface = selectedTypeface
                            )
                            onConfirm(newConfig)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Apply", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Navigation Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                edgePadding = 0.dp,
                containerColor = Color(0xFF262626),
                contentColor = Color(0xFFFF9800)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, fontSize = 12.sp, color = if (selectedTabIndex == index) Color(0xFFFF9800) else Color.LightGray) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                when (selectedTabIndex) {
                    0 -> { // Text, Size & Import Font with Live Preview
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            label = { Text("Text Content") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Font Size (${fontSizeVal.toInt()} px)", color = Color.LightGray, fontSize = 12.sp)
                        Slider(
                            value = fontSizeVal,
                            onValueChange = { fontSizeVal = it },
                            valueRange = 16f..160f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9800), activeTrackColor = Color(0xFFFF9800))
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Font Picker & Live Preview", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Button(
                                onClick = { fontPickerLauncher.launch(arrayOf("*/*")) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Import TTF/OTF", color = Color.White, fontSize = 11.sp)
                            }
                        }

                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(availableFonts) { (name, tf) ->
                                val isSelected = selectedTypeface == tf
                                Card(
                                    modifier = Modifier
                                        .clickable {
                                            selectedTypeface = tf
                                            selectedFontName = name
                                        },
                                    colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFFFF9800) else Color(0xFF2B2B2B)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(name, color = if (isSelected) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(width = 80.dp, height = 30.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color.DarkGray),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                val p = android.graphics.Paint().apply {
                                                    isAntiAlias = true
                                                    textSize = 18f
                                                    color = android.graphics.Color.WHITE
                                                    typeface = tf
                                                }
                                                drawContext.canvas.nativeCanvas.drawText("Aa", 25f, 22f, p)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    1 -> { // Color Wheel & Pixel Precise Spacing (px)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Main Text Color (Color Overlay)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(textColorVal))
                                        .border(2.dp, Color.White, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(onClick = {
                                    activeColorTarget = ColorPickerTarget.TEXT_COLOR
                                    showWheelPicker = true
                                }) {
                                    Icon(Icons.Default.Palette, contentDescription = "Color Wheel", tint = Color(0xFFFF9800))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Word & Letter Spacing (${wordSpacingPx.toInt()} px)", color = Color.LightGray, fontSize = 12.sp)
                        Slider(
                            value = wordSpacingPx,
                            onValueChange = { wordSpacingPx = it },
                            valueRange = -20f..100f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9800), activeTrackColor = Color(0xFFFF9800))
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Line Spacing Atas-Bawah (${lineSpacingPx.toInt()} px)", color = Color.LightGray, fontSize = 12.sp)
                        Slider(
                            value = lineSpacingPx,
                            onValueChange = { lineSpacingPx = it },
                            valueRange = 0f..150f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9800), activeTrackColor = Color(0xFFFF9800))
                        )
                    }
                    2 -> { // Photoshop Stroke & Drop Shadow (px)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Outer Stroke / Outline Effect", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Switch(checked = enableOutline, onCheckedChange = { enableOutline = it })
                        }
                        if (enableOutline) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Stroke Color", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(outlineColorVal))
                                        .clickable {
                                            activeColorTarget = ColorPickerTarget.OUTLINE_COLOR
                                            showWheelPicker = true
                                        }
                                )
                            }
                            Text("Stroke Width (${outlineWidthPx.toInt()} px)", color = Color.LightGray, fontSize = 11.sp)
                            Slider(value = outlineWidthPx, onValueChange = { outlineWidthPx = it }, valueRange = 1f..60f)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Photoshop Drop Shadow", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Switch(checked = enableShadow, onCheckedChange = { enableShadow = it })
                        }
                        if (enableShadow) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Shadow Color", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(shadowColorVal))
                                        .clickable {
                                            activeColorTarget = ColorPickerTarget.SHADOW_COLOR
                                            showWheelPicker = true
                                        }
                                )
                            }
                            Text("Shadow Distance (${shadowDxPx.toInt()} px, ${shadowDyPx.toInt()} px)", color = Color.LightGray, fontSize = 11.sp)
                            Slider(value = shadowDxPx, onValueChange = { shadowDxPx = it; shadowDyPx = it }, valueRange = -40f..40f)
                            Text("Shadow Blur Radius (${shadowRadiusPx.toInt()} px)", color = Color.LightGray, fontSize = 11.sp)
                            Slider(value = shadowRadiusPx, onValueChange = { shadowRadiusPx = it }, valueRange = 1f..50f)
                        }
                    }
                    3 -> { // Glow & Gradient Overlay
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Outer Glow Effect", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Switch(checked = enableOuterGlow, onCheckedChange = { enableOuterGlow = it })
                        }
                        if (enableOuterGlow) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Glow Color", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(outerGlowColorVal))
                                        .clickable {
                                            activeColorTarget = ColorPickerTarget.OUTER_GLOW_COLOR
                                            showWheelPicker = true
                                        }
                                )
                            }
                            Text("Glow Size (${outerGlowRadiusPx.toInt()} px)", color = Color.LightGray, fontSize = 11.sp)
                            Slider(value = outerGlowRadiusPx, onValueChange = { outerGlowRadiusPx = it }, valueRange = 2f..60f)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Linear Gradient Overlay", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Switch(checked = enableGradient, onCheckedChange = { enableGradient = it })
                        }
                        if (enableGradient) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                Button(onClick = {
                                    activeColorTarget = ColorPickerTarget.GRADIENT_START
                                    showWheelPicker = true
                                }) { Text("Start Color", fontSize = 11.sp) }

                                Button(onClick = {
                                    activeColorTarget = ColorPickerTarget.GRADIENT_END
                                    showWheelPicker = true
                                }) { Text("End Color", fontSize = 11.sp) }
                            }
                        }
                    }
                    4 -> { // Banner Frame & 1-Tap Photoshop Style Presets
                        Text("Photoshop 1-Tap Style Presets", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Button(
                                onClick = {
                                    enableOutline = true
                                    outlineColorVal = AndroidColor.BLACK
                                    outlineWidthPx = 12f
                                    enableShadow = true
                                    shadowColorVal = AndroidColor.YELLOW
                                    shadowRadiusPx = 20f
                                    textColorVal = AndroidColor.CYAN
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Cyberpunk", fontSize = 10.sp)
                            }

                            Button(
                                onClick = {
                                    enableGradient = true
                                    gradStartColorVal = AndroidColor.parseColor("#FFD700")
                                    gradEndColorVal = AndroidColor.parseColor("#FFA500")
                                    enableOutline = true
                                    outlineColorVal = AndroidColor.parseColor("#8B4513")
                                    outlineWidthPx = 10f
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Yellow, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Gold Luxury", fontSize = 10.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Background Banner Frame Fill", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Switch(checked = enableBgBanner, onCheckedChange = { enableBgBanner = it })
                        }
                        if (enableBgBanner) {
                            Text("Corner Radius (${bgCornerRadiusPx.toInt()} px)", color = Color.LightGray, fontSize = 11.sp)
                            Slider(value = bgCornerRadiusPx, onValueChange = { bgCornerRadiusPx = it }, valueRange = 0f..50f)
                            Text("Padding (${bgPaddingPx.toInt()} px)", color = Color.LightGray, fontSize = 11.sp)
                            Slider(value = bgPaddingPx, onValueChange = { bgPaddingPx = it }, valueRange = 5f..60f)
                        }
                    }
                }
            }
        }
    }

    if (showWheelPicker) {
        ColorPickerDialog(
            initialColor = when (activeColorTarget) {
                ColorPickerTarget.TEXT_COLOR -> textColorVal
                ColorPickerTarget.OUTLINE_COLOR -> outlineColorVal
                ColorPickerTarget.SHADOW_COLOR -> shadowColorVal
                ColorPickerTarget.OUTER_GLOW_COLOR -> outerGlowColorVal
                ColorPickerTarget.INNER_GLOW_COLOR -> innerGlowColorVal
                ColorPickerTarget.GRADIENT_START -> gradStartColorVal
                ColorPickerTarget.GRADIENT_END -> gradEndColorVal
                ColorPickerTarget.BG_COLOR -> bgColorVal
            },
            onColorSelected = { col ->
                when (activeColorTarget) {
                    ColorPickerTarget.TEXT_COLOR -> textColorVal = col
                    ColorPickerTarget.OUTLINE_COLOR -> outlineColorVal = col
                    ColorPickerTarget.SHADOW_COLOR -> shadowColorVal = col
                    ColorPickerTarget.OUTER_GLOW_COLOR -> outerGlowColorVal = col
                    ColorPickerTarget.INNER_GLOW_COLOR -> innerGlowColorVal = col
                    ColorPickerTarget.GRADIENT_START -> gradStartColorVal = col
                    ColorPickerTarget.GRADIENT_END -> gradEndColorVal = col
                    ColorPickerTarget.BG_COLOR -> bgColorVal = col
                }
            },
            onDismiss = { showWheelPicker = false }
        )
    }
}
