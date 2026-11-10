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
import androidx.compose.material.icons.filled.AlignHorizontalCenter
import androidx.compose.material.icons.filled.AlignVerticalCenter
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grooxtyper.app.model.LayerAlignEngine

private val AlignAccent = Color(0xFFFF5722)
private val AlignChip = Color(0xFF38383A)

/**
 * Panel Auto-Align Layers (ala TypeR / Photoshop).
 *
 * Dua bagian:
 *
 *  - **Ratakan / Sebarkan** (ala Photoshop): rapatkan layer terpilih ke
 *    tepi/tengah area acuan, atau sebarkan dengan jarak sama.
 *  - **Auto-Align (Reposition)**: geser layer gambar sampai isinya paling
 *    cocok dengan layer acuan, memakai korelasi tepi — proyeksi
 *    "Reposition" ala TypeR (isi piksel tak disentuh).
 */
@Composable
fun LayerAlignPanel(
    selectedCount: Int,
    onAlign: (LayerAlignEngine.Align) -> Unit,
    onDistribute: (LayerAlignEngine.Distribute) -> Unit,
    onAutoAlignContent: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Auto-Align Layers" +
                if (selectedCount > 1) " ($selectedCount layer dipilih)" else "",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        Text(
            "Ratakan ke area gabungan layer terpilih, atau seasarkan ke layer acuan.",
            color = Color.Gray,
            fontSize = 10.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AlignIcon(Icons.Default.FormatAlignLeft, "Kiri", enabled = selectedCount > 0) {
                onAlign(LayerAlignEngine.Align.LEFT)
            }
            AlignIcon(Icons.Default.AlignHorizontalCenter, "Tengah", enabled = selectedCount > 0) {
                onAlign(LayerAlignEngine.Align.CENTER_H)
            }
            AlignIcon(Icons.Default.FormatAlignRight, "Kanan", enabled = selectedCount > 0) {
                onAlign(LayerAlignEngine.Align.RIGHT)
            }
            Spacer(modifier = Modifier.width(6.dp))
            AlignIcon(Icons.Default.FormatAlignCenter, "Atas", enabled = selectedCount > 0) {
                onAlign(LayerAlignEngine.Align.TOP)
            }
            AlignIcon(Icons.Default.AlignVerticalCenter, "Tengah V", enabled = selectedCount > 0) {
                onAlign(LayerAlignEngine.Align.MIDDLE_V)
            }
            AlignIcon(Icons.Default.FormatAlignCenter, "Bawah", enabled = selectedCount > 0) {
                onAlign(LayerAlignEngine.Align.BOTTOM)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AlignChip("Sebar H", enabled = selectedCount > 2) {
                onDistribute(LayerAlignEngine.Distribute.HORIZONTAL)
            }
            AlignChip("Sebar V", enabled = selectedCount > 2) {
                onDistribute(LayerAlignEngine.Distribute.VERTICAL)
            }
            AlignChip("Cocokkan isi", enabled = selectedCount > 1) {
                onAutoAlignContent()
            }
        }
    }
}

@Composable
private fun AlignIcon(
    icon: ImageVector,
    desc: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) AlignChip else Color(0xFF2A2A2C))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = desc,
            tint = if (enabled) Color.White else Color(0xFF666666),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun AlignChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (enabled) AlignAccent else Color(0xFF2A2A2C))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 9.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            color = if (enabled) Color.White else Color(0xFF888888),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
