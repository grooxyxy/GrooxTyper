package com.grooxtyper.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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

private val RailActive = Color(0xFFFF5722)
private val RailIdle = Color(0xFF2C2C2E)

/**
 * Tombol rail yang konsisten untuk semua tool.
 *
 * Bentuknya: ikon SVG (dari [ToolIcons]) di atas label kecil, dalam pil
 * bulat. Tool aktif memakai warna aksen.
 *
 * @param icon nama ikon di [ToolIcons].
 */
@Composable
fun ToolButton(
    icon: String,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) RailActive else RailIdle)
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val vector = ToolIcons.of(icon)
        if (vector != null) {
            Icon(
                imageVector = vector,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(19.dp)
            )
        } else {
            Box(modifier = Modifier.size(19.dp))
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 9.sp,
            lineHeight = 10.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/**
 * Pemisah tipis antar kelompok tool, biar urutannya terbaca.
 */
@Composable
fun ToolGroupSeparator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(1.dp)
            .height(34.dp)
            .background(Color(0xFF3C3C3E))
    )
}

/** Baris pembungkus kelompok tool. */
@Composable
fun ToolGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}
