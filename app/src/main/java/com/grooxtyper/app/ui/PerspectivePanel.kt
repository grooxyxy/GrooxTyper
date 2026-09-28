package com.grooxtyper.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grooxtyper.app.model.PerspSpec
import com.grooxtyper.app.model.TextBox
import kotlin.math.roundToInt

// Warna lokal file (setiap file UI punya sendiri; lihat BulkTextDialog).
private val PerspAccent = Color(0xFFFF5722)

/**
 * Panel kontrol perspektif (mode grid). Berdampingan dengan grid di kanvas:
 * titik biru bisa diseret langsung, panel ini untuk angka halus + preset.
 *
 * Arah perspektif_now bebas per sudut (lihat [PerspSpec]), bukan lagi hanya
 * "sempit atas" atau "sempit bawah" seperti versi lama yang terasa kaku.
 */
@Composable
fun PerspectivePanel(
    box: TextBox,
    onChange: () -> Unit,
    onClose: () -> Unit
) {
    val p = remember(box.id) {
        val src = box.persp ?: PerspSpec.fromKeystone(box.perspX, box.perspY)
        PerspSpec().also { it.copyFrom(src) }
    }
    // Varian lokal supaya slider terasa responsif; commit ke box saat Selesai.
    var tlX by remember(box.id) { mutableFloatStateOf(p.tlX) }
    var tlY by remember(box.id) { mutableFloatStateOf(p.tlY) }
    var trX by remember(box.id) { mutableFloatStateOf(p.trX) }
    var trY by remember(box.id) { mutableFloatStateOf(p.trY) }
    var brX by remember(box.id) { mutableFloatStateOf(p.brX) }
    var brY by remember(box.id) { mutableFloatStateOf(p.brY) }
    var blX by remember(box.id) { mutableFloatStateOf(p.blX) }
    var blY by remember(box.id) { mutableFloatStateOf(p.blY) }

    fun applyPreset(spec: PerspSpec) {
        tlX = spec.tlX; tlY = spec.tlY
        trX = spec.trX; trY = spec.trY
        brX = spec.brX; brY = spec.brY
        blX = spec.blX; blY = spec.blY
    }

    fun commit() {
        val sp = PerspSpec(tlX, tlY, trX, trY, brX, brY, blX, blY)
        sp.clampAll()
        box.persp = sp
        // Sinkronkan juga keystone lama supaya panel teks lama tetap sinkron.
        box.perspX = ((tlX + trX + brX + blX) / 4f).coerceIn(-1f, 1f)
        box.perspY = ((tlY + trY + brY + blY) / 4f).coerceIn(-1f, 1f)
        onChange()
    }

    val presets = listOf(
        "Datar" to PerspSpec(),
        "Jauh atas" to PerspSpec(tlX = 0.45f, trX = -0.45f),
        "Jauh bawah" to PerspSpec(brX = 0.45f, blX = -0.45f),
        "Jauh kiri" to PerspSpec(tlY = 0.45f, blY = 0.45f),
        "Jauh kanan" to PerspSpec(trY = -0.45f, brY = -0.45f),
        "Miring" to PerspSpec(tlX = 0.4f, blX = -0.4f)
    )

    Column(
        modifier = Modifier
            .padding(bottom = 72.dp, start = 10.dp, end = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xEE1C1C1E))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .height(268.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Perspektif",
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Seret titik biru di kanvas juga bisa",
                color = Color.Gray, fontSize = 10.sp
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            presets.take(3).forEach { (label, spec) ->
                PresetChip(label, Modifier.weight(1f)) { applyPreset(spec) }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            presets.drop(3).forEach { (label, spec) ->
                PresetChip(label, Modifier.weight(1f)) { applyPreset(spec) }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        CornerSliders("Kiri-atas", tlX, { tlX = it; commit() }, tlY, { tlY = it; commit() })
        CornerSliders("Kanan-atas", trX, { trX = it; commit() }, trY, { trY = it; commit() })
        CornerSliders("Kanan-bawah", brX, { brX = it; commit() }, brY, { brY = it; commit() })
        CornerSliders("Kiri-bawah", blX, { blX = it; commit() }, blY, { blY = it; commit() })
        Spacer(modifier = Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    applyPreset(PerspSpec())
                    commit()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3A41)),
                modifier = Modifier.weight(1f)
            ) { Text("Datar", color = Color.White, fontSize = 12.sp) }
            Button(
                onClick = {
                    commit()
                    onClose()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PerspAccent),
                modifier = Modifier.weight(1f)
            ) { Text("Selesai", color = Color.White, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun PresetChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF3A3A41))
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Dua slider (X dan Y) untuk satu sudut perspektif. */
@Composable
private fun CornerSliders(
    label: String,
    x: Float,
    onX: (Float) -> Unit,
    y: Float,
    onY: (Float) -> Unit
) {
    Column {
        Text(
            "$label  X ${(x * 100).roundToInt()}  Y ${(y * 100).roundToInt()}",
            color = Color(0xFF9AA0A6), fontSize = 10.sp, fontWeight = FontWeight.Bold
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = x,
                onValueChange = onX,
                valueRange = -1.2f..1.2f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = PerspAccent, activeTrackColor = PerspAccent
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Slider(
                value = y,
                onValueChange = onY,
                valueRange = -1.2f..1.2f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF00E5FF), activeTrackColor = Color(0xFF00E5FF)
                )
            )
        }
    }
}
