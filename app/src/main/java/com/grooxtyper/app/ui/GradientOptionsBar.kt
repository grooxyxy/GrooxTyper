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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grooxtyper.app.model.GradientEngine

private val GradAccent = Color(0xFFFF5722)
private val GradPanel = Color(0xE61C1C1E)

/**
 * Options bar Gradient tool (ala TypeR): mode linear/radial, warna akhir,
 * arah terbalik, dan apakah mengikuti seleksi.
 */
@Composable
fun GradientOptionsBar(
    mode: GradientEngine.Mode,
    onMode: (GradientEngine.Mode) -> Unit,
    startColor: Color,
    endColor: Color,
    onEndColor: (Color) -> Unit,
    reversed: Boolean,
    onReversed: (Boolean) -> Unit,
    respectSelection: Boolean,
    onRespectSelection: (Boolean) -> Unit,
    opacity: Float,
    onOpacity: (Float) -> Unit,
    onPickEndColor: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .background(GradPanel, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Gradient",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            GradChip("Linear", mode == GradientEngine.Mode.LINEAR) {
                onMode(GradientEngine.Mode.LINEAR)
            }
            GradChip("Radial", mode == GradientEngine.Mode.RADIAL) {
                onMode(GradientEngine.Mode.RADIAL)
            }
            GradChip("Balik", reversed) { onReversed(!reversed) }
            GradChip("Ikuti seleksi", respectSelection) {
                onRespectSelection(!respectSelection)
            }
            Spacer(modifier = Modifier.weight(1f))
            // Warna awal (dari brush) -> panah -> warna akhir (bisa diganti).
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(startColor)
                    .border(2.dp, Color.White, CircleShape)
            )
            Text(
                "→",
                color = Color.Gray,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(endColor)
                    .border(2.dp, Color.White, CircleShape)
                    .clickable { onPickEndColor() }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Opacity",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(62.dp)
            )
            Slider(
                value = opacity,
                onValueChange = onOpacity,
                valueRange = 0.05f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = GradAccent,
                    activeTrackColor = GradAccent
                )
            )
            Text(
                "${(opacity * 100).toInt()}%",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.width(38.dp)
            )
        }
        Text(
            "Seret di kanvas untuk menentukan arah. Bila ada seleksi, gradasi hanya isi area seleksi.",
            color = Color.Gray,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun GradChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .padding(end = 4.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) GradAccent else Color(0xFF38383A))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}
