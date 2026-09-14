package com.grooxtyper.app.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grooxtyper.app.model.BrushEngine
import com.grooxtyper.app.model.BrushType

@Composable
fun BrushDrawerPanel(
    brushEngine: BrushEngine,
    onClose: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Kuas / Brush", "Stabilizer & Fade")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(Color(0xFF1E1E1E))
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("GrooxTyper Brush Drawer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                ) {
                    Text("Tutup", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Navigation Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp,
                containerColor = Color(0xFF262626),
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

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> { // Brush list with stroke previews
                        Column {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(BrushType.values()) { type ->
                                    val isSelected = brushEngine.brushType == type
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable { brushEngine.brushType = type },
                                        colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF383838) else Color(0xFF282828)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) Color(0xFFFF9800) else Color.Gray)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                type.displayName,
                                                color = if (isSelected) Color.White else Color.LightGray,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp,
                                                modifier = Modifier.width(110.dp)
                                            )

                                            // Stroke Preview Box
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(36.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF1B1B1B)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                ComposeCanvas(modifier = Modifier.fillMaxWidth().height(36.dp)) {
                                                    val canvas = drawContext.canvas.nativeCanvas
                                                    val previewPaint = Paint().apply {
                                                        isAntiAlias = true
                                                        style = Paint.Style.STROKE
                                                        strokeCap = Paint.Cap.ROUND
                                                        strokeWidth = 14f
                                                        color = android.graphics.Color.WHITE
                                                    }
                                                    val path = Path().apply {
                                                        moveTo(20f, size.height / 2f)
                                                        cubicTo(
                                                            size.width * 0.3f, size.height * 0.1f,
                                                            size.width * 0.7f, size.height * 0.9f,
                                                            size.width - 20f, size.height / 2f
                                                        )
                                                    }
                                                    canvas.drawPath(path, previewPaint)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Ukuran Kuas (${brushEngine.size.toInt()} px)", color = Color.LightGray, fontSize = 12.sp)
                            Slider(
                                value = brushEngine.size,
                                onValueChange = { brushEngine.size = it },
                                valueRange = 1f..120f,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9800), activeTrackColor = Color(0xFFFF9800))
                            )

                            Text("Transparansi / Opacity (${(brushEngine.opacity * 100).toInt()}%)", color = Color.LightGray, fontSize = 12.sp)
                            Slider(
                                value = brushEngine.opacity,
                                onValueChange = { brushEngine.opacity = it },
                                valueRange = 0.05f..1.0f,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9800), activeTrackColor = Color(0xFFFF9800))
                            )
                        }
                    }
                    1 -> { // Stabilizer & Force Fade
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Penstabil Garis (Stabilizer)", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = brushEngine.forceFade.isEnabled,
                                    onCheckedChange = { brushEngine.forceFade.isEnabled = it }
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Awal Lancip / Start Taper (${(brushEngine.forceFade.startFade * 100).toInt()}%)", color = Color.LightGray, fontSize = 12.sp)
                            Slider(
                                value = brushEngine.forceFade.startFade,
                                onValueChange = { brushEngine.forceFade.startFade = it },
                                valueRange = 0.05f..0.8f,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFFFF9800), activeTrackColor = Color(0xFFFF9800))
                            )

                            Text("Akhir Lancip / End Taper (${(brushEngine.forceFade.endFade * 100).toInt()}%)", color = Color.LightGray, fontSize = 12.sp)
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
}
