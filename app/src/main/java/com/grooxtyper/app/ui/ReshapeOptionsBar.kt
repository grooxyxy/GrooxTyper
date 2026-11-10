package com.grooxtyper.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val ReshapeAccent = Color(0xFFFF5722)
private val ReshapePanel = Color(0xE61C1C1E)

/**
 * Options bar Bubble Reshaper (ala TypeR "Bubble Reshaper").
 *
 * Bentuk gelembung bisa digeser titik-titik outline-nya (push/pull).
 * Tombol "Jadikan outline" mengubah bentuk parametrik (elips / persegi
 * bulat) jadi outline kustom yang bisa diubah; "Reset" mengembalikannya.
 */
@Composable
fun ReshapeOptionsBar(
    hasSelection: Boolean,
    pointCount: Int,
    onApplyOutline: () -> Unit,
    onResetOutline: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .background(ReshapePanel, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            Icons.Default.BubbleChart,
            contentDescription = null,
            tint = ReshapeAccent,
            modifier = Modifier.size(18.dp)
        )
        Text(
            if (hasSelection) "Bubble Reshaper - $pointCount titik" else "Pilih teks ber-gelembung",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.weight(1f))
        ReshapeChip("Jadikan outline", hasSelection) { onApplyOutline() }
        ReshapeChip("Reset", hasSelection) { onResetOutline() }
    }
}

@Composable
private fun ReshapeChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (enabled) ReshapeAccent else Color(0xFF38383A))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            color = if (enabled) Color.White else Color(0xFF888888),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
