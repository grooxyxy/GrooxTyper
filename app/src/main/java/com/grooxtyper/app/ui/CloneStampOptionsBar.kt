package com.grooxtyper.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
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

private val CloneAccent = Color(0xFFFF5722)
private val ClonePanel = Color(0xE61C1C1E)

/**
 * Options bar Clone Stamp (ala TypeR): ukuran stamp, hardness tepi,
 * opacity, plus status titik sumber.
 */
@Composable
fun CloneStampOptionsBar(
    size: Float,
    onSize: (Float) -> Unit,
    hardness: Float,
    onHardness: (Float) -> Unit,
    opacity: Float,
    onOpacity: (Float) -> Unit,
    hasSource: Boolean,
    onResetSource: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .background(ClonePanel, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = null,
                tint = CloneAccent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "Clone Stamp",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (hasSource) "Sumber aktif — geser untuk menyalin" else "Ketuk kanvas untuk menetapkan sumber",
                color = if (hasSource) Color.Gray else CloneAccent,
                fontSize = 10.sp,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF38383A))
                    .clickable { onResetSource() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Sumber baru", color = Color.White, fontSize = 10.sp)
            }
        }
        CloneSlider("Ukuran", size, 4f..200f, "px", onSize)
        CloneSlider("Hardness", hardness, 0f..1f, "", onHardness)
        CloneSlider("Opacity", opacity, 0.05f..1f, "%", onOpacity, opacity * 100f)
    }
}

@Composable
private fun CloneSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onValue: (Float) -> Unit,
    displayScale: Float = 1f
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(62.dp)
        )
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = CloneAccent,
                activeTrackColor = CloneAccent
            )
        )
        Text(
            "${(value * displayScale).toInt()}$unit",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(42.dp)
        )
    }
}
