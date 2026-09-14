package com.grooxtyper.app.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grooxtyper.app.model.BrushEngine
import com.grooxtyper.app.model.BrushType

@Composable
fun BrushPanelDialog(
    brushEngine: BrushEngine,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Basic Brushes", "Brush Dynamics & Stabilizer")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("ibisPaint Brush Studio", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
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

                Spacer(modifier = Modifier.height(12.dp))

                Box(modifier = Modifier.height(280.dp)) {
                    when (selectedTab) {
                        0 -> { // Basic Brushes List & Quick Sliders
                            Column {
                                Text("Select Brush Tool", color = Color.LightGray, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(6.dp))

                                LazyColumn(modifier = Modifier.weight(1f)) {
                                    items(BrushType.values()) { type ->
                                        val isSelected = brushEngine.brushType == type
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .clickable { brushEngine.brushType = type },
                                            colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF383838) else Color(0xFF222222)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(12.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) Color(0xFFFF9800) else Color.Gray)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    type.displayName,
                                                    color = if (isSelected) Color.White else Color.LightGray,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 14.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Text("Thickness / Size (${brushEngine.size.toInt()} px)", color = Color.LightGray, fontSize = 12.sp)
                                Slider(
                                    value = brushEngine.size,
                                    onValueChange = { brushEngine.size = it },
                                    valueRange = 1f..120f,
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9800), activeTrackColor = Color(0xFFFF9800))
                                )

                                Text("Opacity (${(brushEngine.opacity * 100).toInt()}%)", color = Color.LightGray, fontSize = 12.sp)
                                Slider(
                                    value = brushEngine.opacity,
                                    onValueChange = { brushEngine.opacity = it },
                                    valueRange = 0.05f..1.0f,
                                    colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9800), activeTrackColor = Color(0xFFFF9800))
                                )
                            }
                        }
                        1 -> { // Brush Dynamics & Stabilizer
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Stroke Stabilizer (Smoothing)", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Switch(
                                        checked = brushEngine.stabilizer.isEnabled,
                                        onCheckedChange = { brushEngine.stabilizer.isEnabled = it }
                                    )
                                }
                                if (brushEngine.stabilizer.isEnabled) {
                                    Text("Stabilizer Strength (Level ${brushEngine.stabilizer.value})", color = Color.LightGray, fontSize = 12.sp)
                                    Slider(
                                        value = brushEngine.stabilizer.value.toFloat(),
                                        onValueChange = { brushEngine.stabilizer.value = it.toInt() },
                                        valueRange = 1f..10f,
                                        steps = 9,
                                        colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9800), activeTrackColor = Color(0xFFFF9800))
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Force Fade (Real Taper)", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Switch(
                                        checked = brushEngine.forceFade.isEnabled,
                                        onCheckedChange = { brushEngine.forceFade.isEnabled = it }
                                    )
                                }

                                if (brushEngine.forceFade.isEnabled) {
                                    Text("Start Taper (${(brushEngine.forceFade.startFade * 100).toInt()}%)", color = Color.LightGray, fontSize = 12.sp)
                                    Slider(
                                        value = brushEngine.forceFade.startFade,
                                        onValueChange = { brushEngine.forceFade.startFade = it },
                                        valueRange = 0.05f..0.8f,
                                        colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9800), activeTrackColor = Color(0xFFFF9800))
                                    )
                                    Text("End Taper (${(brushEngine.forceFade.endFade * 100).toInt()}%)", color = Color.LightGray, fontSize = 12.sp)
                                    Slider(
                                        value = brushEngine.forceFade.endFade,
                                        onValueChange = { brushEngine.forceFade.endFade = it },
                                        valueRange = 0.05f..0.8f,
                                        colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9800), activeTrackColor = Color(0xFFFF9800))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
            ) {
                Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF2A2A2A)
    )
}
