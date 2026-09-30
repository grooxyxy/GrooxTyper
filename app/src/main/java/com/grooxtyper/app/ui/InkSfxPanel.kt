package com.grooxtyper.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grooxtyper.app.model.SfxGenre
import com.grooxtyper.app.model.SfxInkSpec
import com.grooxtyper.app.model.SfxStyleSpec
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
    onSpec: (SfxInkSpec?) -> Unit,
    style: SfxStyleSpec? = null,
    onStyle: (SfxStyleSpec?) -> Unit = {}
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

/**
 * Pemilih gaya SFX bersama: empat genre (horror, romance, action, fantasy)
 * memakai angka yang sama dengan kuas SFX, sumbernya
 * `docs/sfx-lettering-research.md`. Tombol "Bawaan" mengembalikan genre terpilih
 * ke presetnya, jadi user selalu bisa kembali ke tampilan yang paling mirip
 * referensi tanpa mengingat angkanya.
 */
@Composable
fun SfxStyleRow(
    style: SfxStyleSpec?,
    onStyle: (SfxStyleSpec?) -> Unit
) {
    var picked by remember { mutableStateOf(SfxGenre.HORROR) }
    val st = style
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Gaya SFX",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    if (st == null) "Belum dipakai: teks Tinta SFX masih gaya manual"
                    else "Aktif: gradasi + outline dua lapis + bayangan keras",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
            Switch(
                checked = st != null,
                onCheckedChange = { on -> onStyle(if (on) SfxStyleSpec.presetOf(picked) else null) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Accent,
                    checkedTrackColor = Accent.copy(alpha = 0.5f)
                )
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SfxGenre.values().forEach { g ->
                val on = g == picked
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (on) Accent else Color(0xFF2C2C2E))
                        .clickable { picked = g }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                ) {
                    Text(
                        g.displayName.removePrefix("SFX "),
                        color = if (on) Color.White else Color.LightGray,
                        fontSize = 11.sp
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onStyle(SfxStyleSpec.presetOf(picked)) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2C2C2E)
                )
            ) { Text("Bawaan", fontSize = 12.sp, color = Color.White) }
            Button(
                onClick = { onStyle(null) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2C2C2E)
                )
            ) { Text("Matikan", fontSize = 12.sp, color = Color.White) }
        }
        if (st != null) {
            Text(
                "Miring ${st.tiltPerWord.toInt()} derajat, kasar ${(st.roughness * 100).toInt()}%, " +
                    "outline ${(st.outlineScale * 100).toInt()}% H, " +
                    "bayangan ${if (st.shadowBlur == 0f) "keras" else "lembut"}",
                color = Color.Gray, fontSize = 11.sp
            )
        }
    }
}
