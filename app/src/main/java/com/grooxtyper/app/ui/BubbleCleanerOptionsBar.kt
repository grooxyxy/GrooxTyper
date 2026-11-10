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
import androidx.compose.material.icons.filled.BubbleChart
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

private val CleanerAccent = Color(0xFFFF5722)
private val CleanerPanel = Color(0xE61C1C1E)

/**
 * Options bar Bubble Cleaner (ala TypeR "Bubble Cleaner").
 *
 * Dua setelan yang TypeR pakai juga: **Tolerance** (seberapa jauh warna
 * boleh beda saat flood interior gelembung) dan **Margin** (seberapa jauh
 * huruf dilebarkan supaya tepi anti-alias ikut tertutup).
 */
@Composable
fun BubbleCleanerOptionsBar(
    tolerance: Float,
    onTolerance: (Float) -> Unit,
    margin: Float,
    onMargin: (Float) -> Unit,
    busy: Boolean,
    lastPixels: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .background(CleanerPanel, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.BubbleChart,
                contentDescription = null,
                tint = CleanerAccent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                if (busy) "Membersihkan..." else "Bubble Cleaner",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                if (lastPixels > 0) "$lastPixels px dibersihkan" else "Ketuk dalam gelembung",
                color = Color.Gray,
                fontSize = 10.sp
            )
        }
        CleanerSlider("Toleransi", tolerance, 0f..255f, "") { onTolerance(it) }
        CleanerSlider("Margin", margin, 0f..20f, "px") { onMargin(it) }
        Text(
            "Teks dalam gelembung diganti warna gelembung itu sendiri. Outline tetap utuh.",
            color = Color.Gray,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun CleanerSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onValue: (Float) -> Unit
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
                thumbColor = CleanerAccent,
                activeTrackColor = CleanerAccent
            )
        )
        Text(
            "${value.toInt()}$unit",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(38.dp)
        )
    }
}
