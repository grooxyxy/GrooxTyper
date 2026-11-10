package com.grooxtyper.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grooxtyper.app.model.selection.SelectionMode

private val SelAccent = Color(0xFFFF5722)
private val SelPanel = Color(0xE61C1C1E)
private val SelChip = Color(0xFF38383A)
private val SelText = Color(0xFFF2F2F2)

/**
 * Options bar seleksi ala TypeR/Photoshop.
 *
 * Satu bar untuk semua tool seleksi: mode Baru/Tambah/Kurang/Iris, aksi
 * cepat, dan opsi lanjutan Magic Wand. Ini menggantikan panel wand besar
 * yang selalu menutupi kanvas. Panel rinci (toleransi, anti-alias, sample
 * merged, bubble aware, feather) dibuka lewat ikon Tune.
 */
@Composable
fun SelectionOptionsBar(
    tool: ActiveTool,
    mode: SelectionMode,
    onMode: (SelectionMode) -> Unit,
    tolerance: Float,
    onTolerance: (Float) -> Unit,
    /**
     * Dipanggil saat slider toleransi dilepas. Editor memakai ini untuk
     * live selection: mengulang wand di titik seed yang sama tanpa
     * perlu ketuk ulang (ala Photoshop/TypeR).
     */
    onToleranceCommit: () -> Unit = {},
    contiguous: Boolean,
    onContiguous: (Boolean) -> Unit,
    antialias: Boolean,
    onAntialias: (Boolean) -> Unit,
    sampleMerged: Boolean,
    onSampleMerged: (Boolean) -> Unit,
    bubbleAware: Boolean,
    onBubbleAware: (Boolean) -> Unit,
    bubbleMode: Boolean,
    onBubbleMode: (Boolean) -> Unit,
    feather: Float,
    onFeather: (Float) -> Unit,
    hasSelection: Boolean,
    selectionCount: Int,
    pixelCount: Long,
    busy: Boolean,
    polygonPointCount: Int,
    advanced: Boolean,
    onAdvanced: (Boolean) -> Unit,
    onClosePolygon: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselect: () -> Unit,
    onInvert: () -> Unit,
    onRefine: () -> Unit,
    onActions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .background(SelPanel, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = selectionToolIcon(tool),
                contentDescription = null,
                tint = SelAccent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                selectionToolLabel(tool),
                color = SelText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            if (selectionCount > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "$selectionCount area",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (tool == ActiveTool.SELECT_WAND) {
                Text(
                    if (busy) "…" else formatPixelCount(pixelCount),
                    color = if (busy) SelAccent else Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = { onAdvanced(!advanced) },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = if (advanced) "Tutup opsi lanjutan" else "Opsi lanjutan",
                    tint = if (advanced) SelAccent else SelText,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (m in SelectionMode.entries) {
                SelectionChip(
                    label = selectionModeLabel(m),
                    active = m == mode,
                    enabled = !busy,
                    onClick = { onMode(m) }
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(Color(0xFF4A4A4C))
            )
            SelectionChip("Semua", false, true, onSelectAll)
            SelectionChip("Kosong", false, hasSelection, onDeselect)
            SelectionChip("Balik", false, hasSelection, onInvert)
            SelectionChip("Rapi", false, hasSelection && !busy, onRefine)
            if (tool == ActiveTool.POLYGON_LASSO && polygonPointCount >= 3) {
                SelectionChip(
                    "Tutup ($polygonPointCount)",
                    active = true,
                    enabled = true,
                    onClick = onClosePolygon
                )
            }
            SelectionChip("Aksi", false, true, onActions)
        }

        if (advanced && tool == ActiveTool.SELECT_WAND) {
            SelectionSliderRow(
                label = "Toleransi",
                value = tolerance,
                onValue = onTolerance,
                range = 0f..255f,
                valueText = "${tolerance.toInt()}",
                onValueChangeFinished = onToleranceCommit
            )
            SelectionSliderRow(
                label = "Feather",
                value = feather,
                onValue = onFeather,
                range = 0f..20f,
                valueText = "${feather.toInt()}px"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SelectionToggleChip("Nyambung", contiguous, onContiguous)
                SelectionToggleChip("Anti-alias", antialias, onAntialias)
                SelectionToggleChip("Sample merged", sampleMerged, onSampleMerged)
                SelectionToggleChip("Bubble aware", bubbleAware, onBubbleAware)
                SelectionToggleChip("Mode bubble", bubbleMode, onBubbleMode)
            }
            Text(
                "Toleransi tinggi = area warna lebih luas. Mode berlaku untuk semua tool seleksi.",
                color = Color.Gray,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun SelectionSliderRow(
    label: String,
    value: Float,
    onValue: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChangeFinished: () -> Unit = {}
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = SelText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(62.dp)
        )
        Slider(
            value = value,
            onValueChange = onValue,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = SelAccent,
                activeTrackColor = SelAccent
            )
        )
        Text(
            valueText,
            color = SelText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(38.dp)
        )
    }
}

@Composable
private fun SelectionChip(
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                if (active) SelAccent else SelChip,
                RoundedCornerShape(9.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            color = if (enabled) SelText else Color(0xFF777777),
            fontSize = 10.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SelectionToggleChip(
    label: String,
    active: Boolean,
    onToggle: (Boolean) -> Unit
) {
    SelectionChip(
        label = label,
        active = active,
        enabled = true,
        onClick = { onToggle(!active) }
    )
}

/**
 * Dialog Refine Edge untuk hasil Magic Wand. Semua operasi memakai
 * TiledSelectionAdapter yang sudah teruji, lalu disinkronkan kembali ke
 * SelectionEngine agar overlay dan aksi lama tetap bekerja.
 */
@Composable
fun SelectionRefineDialog(
    onDismiss: () -> Unit,
    onApply: (feather: Int, expand: Int, contract: Int, border: Int, smooth: Int) -> Unit
) {
    var feather by remember { mutableFloatStateOf(0f) }
    var expand by remember { mutableFloatStateOf(0f) }
    var contract by remember { mutableFloatStateOf(0f) }
    var border by remember { mutableFloatStateOf(0f) }
    var smooth by remember { mutableFloatStateOf(0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Rapi Tepi Seleksi",
                color = SelText,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "Seperti Refine Edge di TypeR: haluskan, lebarkan, sempitkan, atau buat garis tepi sebelum seleksi dipakai.",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                RefineSlider("Feather", feather, 0f..20f, "px") { feather = it }
                RefineSlider("Expand", expand, 0f..30f, "px") { expand = it }
                RefineSlider("Contract", contract, 0f..30f, "px") { contract = it }
                RefineSlider("Border", border, 0f..20f, "px") { border = it }
                RefineSlider("Smooth", smooth, 0f..10f, "px") { smooth = it }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Tip: Expand dan Contract bisa dipakai berurutan. Border membuat cincin seleksi.",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(
                        feather.toInt(),
                        expand.toInt(),
                        contract.toInt(),
                        border.toInt(),
                        smooth.toInt()
                    )
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = SelAccent)
            ) {
                Text("Terapkan", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF1C1C1E)
    )
}

@Composable
private fun RefineSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    unit: String,
    onValue: (Float) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = SelText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(62.dp)
        )
        Slider(
            value = value,
            onValueChange = onValue,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = SelAccent,
                activeTrackColor = SelAccent
            )
        )
        Text(
            "${value.toInt()}$unit",
            color = SelText,
            fontSize = 10.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(40.dp)
        )
    }
}

private fun selectionModeLabel(mode: SelectionMode): String = when (mode) {
    SelectionMode.NEW -> "Baru"
    SelectionMode.ADD -> "Tambah"
    SelectionMode.SUBTRACT -> "Kurang"
    SelectionMode.INTERSECT -> "Iris"
}

private fun selectionToolLabel(tool: ActiveTool): String = when (tool) {
    ActiveTool.LASSO -> "Lasso Bebas"
    ActiveTool.POLYGON_LASSO -> "Lasso Poligon"
    ActiveTool.SELECT_BOX -> "Marquee Kotak"
    ActiveTool.SELECT_ELLIPSE -> "Marquee Elips"
    ActiveTool.SELECT_WAND -> "Magic Wand"
    else -> "Seleksi"
}

private fun formatPixelCount(n: Long): String = when {
    n >= 1_000_000L -> "${n / 1_000_000L},${(n % 1_000_000L) / 100_000L} jt"
    n >= 1_000L -> "${n / 1_000L} rb"
    else -> "$n px"
}
