package com.grooxtyper.app.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grooxtyper.app.model.StackableTextConfig

@Composable
fun TextPanel(
    initialConfig: StackableTextConfig,
    onConfirm: (StackableTextConfig) -> Unit,
    onClose: () -> Unit
) {
    var textInput by remember { mutableStateOf(initialConfig.text) }
    var fontSizeVal by remember { mutableFloatStateOf(initialConfig.fontSize) }
    var textColorVal by remember { mutableIntStateOf(initialConfig.textColor) }

    var enableOutline by remember { mutableStateOf(initialConfig.hasOutline) }
    var outlineColorVal by remember { mutableIntStateOf(initialConfig.outlineColor) }
    var outlineWidthVal by remember { mutableFloatStateOf(initialConfig.outlineWidth) }

    var enableShadow by remember { mutableStateOf(initialConfig.hasShadow) }
    var shadowColorVal by remember { mutableIntStateOf(initialConfig.shadowColor) }
    var shadowDxVal by remember { mutableFloatStateOf(initialConfig.shadowDx) }
    var shadowDyVal by remember { mutableFloatStateOf(initialConfig.shadowDy) }
    var shadowBlurVal by remember { mutableFloatStateOf(initialConfig.shadowRadius) }

    var enableGradient by remember { mutableStateOf(initialConfig.hasGradient) }
    var gradStartColorVal by remember { mutableIntStateOf(initialConfig.gradientStartColor) }
    var gradEndColorVal by remember { mutableIntStateOf(initialConfig.gradientEndColor) }

    var textBlurVal by remember { mutableFloatStateOf(initialConfig.blurRadius) }
    var selectedTypeface by remember { mutableStateOf(initialConfig.typeface) }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Text & Font", "Style & Color", "Outline & Shadow", "Gradient & Blur")

    val colorOptions = listOf(
        AndroidColor.WHITE, AndroidColor.BLACK, AndroidColor.RED,
        AndroidColor.YELLOW, AndroidColor.GREEN, AndroidColor.CYAN,
        AndroidColor.BLUE, AndroidColor.MAGENTA, AndroidColor.DKGRAY
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
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
                Text("ibisPaint Text Tool Editor", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                                outlineWidth = outlineWidthVal,
                                hasShadow = enableShadow,
                                shadowColor = shadowColorVal,
                                shadowRadius = shadowBlurVal,
                                shadowDx = shadowDxVal,
                                shadowDy = shadowDyVal,
                                hasGradient = enableGradient,
                                gradientStartColor = gradStartColorVal,
                                gradientEndColor = gradEndColorVal,
                                blurRadius = textBlurVal,
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
                    0 -> { // Text & Font
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            label = { Text("Isi Teks / Text Content") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Ukuran Font (${fontSizeVal.toInt()} px)", color = Color.LightGray, fontSize = 12.sp)
                        Slider(
                            value = fontSizeVal,
                            onValueChange = { fontSizeVal = it },
                            valueRange = 16f..160f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9800), activeTrackColor = Color(0xFFFF9800))
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Jenis Font / Typeface Style:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val typefaces = listOf(
                                "Bold" to Typeface.DEFAULT_BOLD,
                                "Normal" to Typeface.DEFAULT,
                                "Serif" to Typeface.SERIF,
                                "Monospace" to Typeface.MONOSPACE
                            )
                            typefaces.forEach { (name, tf) ->
                                Button(
                                    onClick = { selectedTypeface = tf },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (selectedTypeface == tf) Color(0xFFFF9800) else Color(0xFF333333))
                                ) {
                                    Text(name, color = if (selectedTypeface == tf) Color.Black else Color.White, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    1 -> { // Style & Color
                        Text("Warna Utama Teks (Fill Color)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            colorOptions.forEach { col ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(col))
                                        .border(if (textColorVal == col) 2.dp else 0.dp, Color(0xFFFF9800), CircleShape)
                                        .clickable { textColorVal = col }
                                )
                            }
                        }
                    }
                    2 -> { // Outline & Shadow
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Efek Garis Tepi (Outer Outline)", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Switch(checked = enableOutline, onCheckedChange = { enableOutline = it })
                        }
                        if (enableOutline) {
                            Text("Ketebalan Garis (${outlineWidthVal.toInt()} px)", color = Color.LightGray, fontSize = 11.sp)
                            Slider(
                                value = outlineWidthVal,
                                onValueChange = { outlineWidthVal = it },
                                valueRange = 1f..40f,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9800), activeTrackColor = Color(0xFFFF9800))
                            )
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                colorOptions.take(5).forEach { col ->
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(col))
                                            .clickable { outlineColorVal = col }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Efek Bayangan (Drop Shadow)", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Switch(checked = enableShadow, onCheckedChange = { enableShadow = it })
                        }
                        if (enableShadow) {
                            Text("Offset X/Y (${shadowDxVal.toInt()}px, ${shadowDyVal.toInt()}px)", color = Color.LightGray, fontSize = 11.sp)
                            Slider(value = shadowDxVal, onValueChange = { shadowDxVal = it; shadowDyVal = it }, valueRange = -20f..20f)
                            Text("Kelembutan Bayangan Blur (${shadowBlurVal.toInt()}px)", color = Color.LightGray, fontSize = 11.sp)
                            Slider(value = shadowBlurVal, onValueChange = { shadowBlurVal = it }, valueRange = 1f..30f)
                        }
                    }
                    3 -> { // Gradient & Blur
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Efek Warna Gradien Linear", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Switch(checked = enableGradient, onCheckedChange = { enableGradient = it })
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Efek Blur Filter Teks (${textBlurVal.toInt()} px)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Slider(
                            value = textBlurVal,
                            onValueChange = { textBlurVal = it },
                            valueRange = 0f..25f,
                            colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9800), activeTrackColor = Color(0xFFFF9800))
                        )
                    }
                }
            }
        }
    }
}
