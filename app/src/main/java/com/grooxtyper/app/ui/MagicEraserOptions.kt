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
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val EraserAccent = Color(0xFFFF5722)
private val EraserPanel = Color(0xE61C1C1E)

/**
 * Options bar Magic Eraser (ala Photoshop/TypeR).
 *
 * Cuma tiga hal yang relevan: toleransi (seberapa jauh warna boleh beda),
 * sample merged (hapus dari gabungan layer atau layer aktif), dan
 * anti-alias (tepi selections halus).
 */
@Composable
fun MagicEraserOptionsBar(
    tolerance: Float,
    onTolerance: (Float) -> Unit,
    sampleMerged: Boolean,
    onSampleMerged: (Boolean) -> Unit,
    antialias: Boolean,
    onAntialias: (Boolean) -> Unit,
    busy: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .background(EraserPanel, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.AutoFixHigh,
                contentDescription = null,
                tint = EraserAccent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                if (busy) "Menghapus..." else "Magic Eraser",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "Ketuk area untuk dihapus",
                color = Color.Gray,
                fontSize = 10.sp
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Toleransi",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(62.dp)
            )
            Slider(
                value = tolerance,
                onValueChange = onTolerance,
                valueRange = 0f..255f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = EraserAccent,
                    activeTrackColor = EraserAccent
                )
            )
            Text(
                "${tolerance.toInt()}",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                modifier = Modifier.width(34.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            EraserToggle("Sample merged", sampleMerged, onSampleMerged)
            EraserToggle("Anti-alias", antialias, onAntialias)
        }
    }
}
