package com.grooxtyper.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grooxtyper.app.model.selection.SelectionMode

/** Salinan warna tema editor (nilai privat file lain tak bisa dipakai). */
private val WandAccent = Color(0xFFFF5722)
private val WandPanelBg = Color(0xFF2C2C2E)

/**
 * Panel pengaturan Magic Wand lengkap (file terpisah karena batas
 * method JVM 64KB melarang menambah UI baru ke CanvasEditorScreen).
 *
 * Isi minimal sesuai spesifikasi:
 *
 * Magic Wand | Toleransi [32] | Contiguous [ON] | Anti-alias [ON] |
 * Sample Merged [ON/OFF] | Mode [New/Add/Subtract/Intersect] |
 * Bubble Aware [OFF] | Feather [0]
 *
 * Mode memakai tombol segmen supaya muat di layar sempit.
 */
@Composable
fun WandToolsPanel(
    tolerance: Float,
    onTolerance: (Float) -> Unit,
    contiguous: Boolean,
    onContiguous: (Boolean) -> Unit,
    antialias: Boolean,
    onAntialias: (Boolean) -> Unit,
    sampleMerged: Boolean,
    onSampleMerged: (Boolean) -> Unit,
    mode: SelectionMode,
    onMode: (SelectionMode) -> Unit,
    feather: Float,
    onFeather: (Float) -> Unit,
    bubbleAware: Boolean,
    onBubbleAware: (Boolean) -> Unit,
    busy: Boolean,
    pixelCount: Long,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .padding(bottom = 72.dp, start = 12.dp, end = 12.dp)
            .background(WandPanelBg, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.AutoFixHigh,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (busy) "Memproses..." else "Ketuk area warna mirip",
                color = Color.Gray, fontSize = 11.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            if (pixelCount > 0L) {
                Text(
                    WandCountText(pixelCount),
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Hapus",
                    color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onClear() }
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Toleransi",
                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.width(64.dp)
            )
            Slider(
                value = tolerance,
                onValueChange = onTolerance,
                valueRange = 0f..255f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = WandAccent, activeTrackColor = WandAccent)
            )
            Text(
                "${tolerance.toInt()}",
                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.width(32.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (m in SelectionMode.entries) {
                val aktif = m == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (aktif) WandAccent else Color(0xFF38383A),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onMode(m) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        WandModeLabel(m),
                        color = Color.White, fontSize = 11.sp,
                        fontWeight = if (aktif) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        WandSwitchRow(
            label = "Contiguous",
            hint = "Hanya yang terhubung",
            checked = contiguous,
            onChecked = onContiguous
        )
        WandSwitchRow(
            label = "Anti-alias",
            hint = "Tepi bertingkat",
            checked = antialias,
            onChecked = onAntialias
        )
        WandSwitchRow(
            label = "Sample Merged",
            hint = "Baca gabungan layer",
            checked = sampleMerged,
            onChecked = onSampleMerged
        )
        WandSwitchRow(
            label = "Bubble Aware",
            hint = "Outline jadi batas",
            checked = bubbleAware,
            onChecked = onBubbleAware
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Feather",
                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.width(64.dp)
            )
            Slider(
                value = feather,
                onValueChange = onFeather,
                valueRange = 0f..20f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = WandAccent, activeTrackColor = WandAccent)
            )
            Text(
                "${feather.toInt()}px",
                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.width(40.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
        Text(
            "Ketuk bubble = satu bubble. Tambah = gabung, Kurang = buang, Iris = irisan.",
            color = Color.Gray, fontSize = 11.sp
        )
    }
}

/** Baris sakelar berlabel untuk panel wand. */
@Composable
private fun WandSwitchRow(
    label: String,
    hint: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(96.dp)
        )
        Text(
            hint,
            color = Color.Gray, fontSize = 10.sp,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedThumbColor = WandAccent)
        )
    }
}

/** Label Indonesia untuk tiap mode seleksi. */
private fun WandModeLabel(mode: SelectionMode): String = when (mode) {
    SelectionMode.NEW -> "Baru"
    SelectionMode.ADD -> "Tambah"
    SelectionMode.SUBTRACT -> "Kurang"
    SelectionMode.INTERSECT -> "Iris"
}

/** Teks jumlah piksel yang ringkas (mis. 75,9 rb). */
private fun WandCountText(n: Long): String = when {
    n >= 1_000_000L -> "${n / 1_000_000L},${(n % 1_000_000L) / 100_000L} jt"
    n >= 1_000L -> "${n / 1_000L} rb"
    else -> "$n px"
}
