package com.grooxtyper.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.grooxtyper.app.model.SfxInkSpec
import kotlin.math.roundToInt

/** Warna aksen oranye (sama dengan warna tombol utama editor). */
private val Accent = Color(0xFFFF5722)

/**
 * Bagian "Tinta SFX": gaya huruf ala video lettering komik - isi tinta solid,
 * tepi bergerigi, dan outline putih yang juga mengikuti cekungan huruf.
 *
 * Semua slider memakai satuan yang enak dibaca: tebal outline, kasar tepi,
 * tebal huruf, dan kemiringan. Nilai default-nya hasil pengukuran video
 * (lihat [com.grooxtyper.app.model.SfxInk]).
 */
@Composable
fun InkSfxSection(
    spec: SfxInkSpec?,
    onSpec: (SfxInkSpec?) -> Unit
) {
    val on = spec != null
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Tinta SFX",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                "Tinta solid + tepi bergerigi + outline putih (gaya video)",
                color = Color.Gray,
                fontSize = 11.sp
            )
        }
        Switch(
            checked = on,
            onCheckedChange = { enabled ->
                onSpec(if (enabled) SfxInkSpec() else null)
            },
            colors = SwitchDefaults.colors(checkedThumbColor = Accent)
        )
    }
    if (!on) return
    val cur = spec!!

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Button(
            onClick = { onSpec(SfxInkSpec(seed = (cur.seed * 31 + 7))) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3A41)),
            modifier = Modifier.weight(1f)
        ) { Text("Acak Tepi", color = Color.White, fontSize = 12.sp) }
        Button(
            onClick = { onSpec(SfxInkSpec(gradient = !cur.gradient)) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3A41)),
            modifier = Modifier.weight(1f)
        ) { Text(if (cur.gradient) "Gradien" else "Datar", color = Color.White, fontSize = 12.sp) }
        Button(
            onClick = {
                onSpec(
                    cur.copy(
                        spatter = if (cur.spatter > 0) 0 else 18,
                        outlineRatio = if (cur.spatter > 0) cur.outlineRatio else 0.52f,
                        roughOutline = if (cur.spatter > 0) cur.roughOutline else 0.46f
                    )
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3A41)),
            modifier = Modifier.weight(1f)
        ) { Text("Percikan", color = Color.White, fontSize = 12.sp) }
    }

    InkSlider("Outline", cur.outlineRatio, 0f..0.9f) { onSpec(cur.copy(outlineRatio = it)) }
    InkSlider("Kasar tepi", cur.roughOutline, 0f..0.8f) { onSpec(cur.copy(roughOutline = it)) }
    InkSlider("Tebal huruf", cur.strokeRatio, 0.03f..0.14f) { onSpec(cur.copy(strokeRatio = it)) }
    InkSlider("Miring", cur.tiltDeg, -35f..35f) { onSpec(cur.copy(tiltDeg = it)) }
}

/** Slider kecil dengan label + nilai persen, untuk parameter tinta SFX. */
@Composable
private fun InkSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(top = 1.dp)) {
        Text(
            "$label ${(value * 100f).roundToInt()}",
            color = Color(0xFF9AA0A6),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = Accent,
                activeTrackColor = Accent
            )
        )
    }
}
