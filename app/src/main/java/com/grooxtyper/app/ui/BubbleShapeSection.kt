package com.grooxtyper.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grooxtyper.app.model.BubbleSpec
import kotlin.math.roundToInt

private val bAccent = Color(0xFFFF5722)
private val bChipBg = Color(0xFF2C2C2E)

/**
 * Bagian "Bentuk Bubble": menentukan TEKS mengikuti bentuk gelembung mana.
 *
 * Tanpa ini, teks hasil isi-bubble otomatis memakai kotak persegi sehingga
 * baris pertama dan terakhir keluar dari gelembung yang bulat. Dengan ini,
 * teks di-wrap ke biggest rect di dalam elips lalu dipotong tepat pada
 * bentuknya (lihat `TextBox.bubblePathPx` + `TextRenderer.render`).
 *
 * [bubbleW]/[bubbleH] dipakai sebagai ukuran gelembung ketika menyalakan
 * bentuk untuk teks yang dibuat manual (bukan dari deteksi model).
 */
@Composable
fun BubbleShapeSection(
    spec: BubbleSpec?,
    defaultW: Float,
    defaultH: Float,
    onSpec: (BubbleSpec?) -> Unit
) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text(
            "Bentuk Bubble",
            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp
        )
        Text(
            "Pilih bentuk gelembung yang harus diikuti teks.",
            color = Color.Gray, fontSize = 11.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val options = listOf(
                null to "Bebas",
                BubbleSpec(shape = BubbleSpec.SHAPE_ELIPS) to "Elips",
                BubbleSpec(shape = BubbleSpec.SHAPE_BULAT) to "Persegi Bulat"
            )
            options.forEach { (opt, label) ->
                val on = when {
                    opt == null && spec == null -> true
                    opt == null || spec == null -> false
                    else -> spec.shape == opt.shape
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (on) bAccent else bChipBg)
                        .clickable {
                            if (opt == null) {
                                onSpec(null)
                            } else {
                                // Ukuran gelembung mulai dari kotak teks yang
                                // ada (atau cadangan 320x180 poin) supaya teks
                                // langsung terlihat mengikuti bentuknya.
                                val w = if (defaultW > 8f) defaultW else 320f
                                val h = if (defaultH > 8f) defaultH else 180f
                                onSpec(opt.copy(bubbleW = w, bubbleH = h))
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        label,
                        color = if (on) Color.White else Color.LightGray,
                        fontSize = 11.sp
                    )
                }
            }
        }
        if (spec != null) {
            Text(
                "Jarak aman dari tepi: ${(spec.inset * 100).roundToInt()}%",
                color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
            )
            Slider(
                value = spec.inset.coerceIn(0f, 0.35f),
                onValueChange = { onSpec(spec.copy(inset = it)) },
                valueRange = 0f..0.35f,
                colors = SliderDefaults.colors(
                    thumbColor = bAccent, activeTrackColor = bAccent
                )
            )
            if (spec.shape == BubbleSpec.SHAPE_BULAT) {
                Text(
                    "Kelengkungan: ${(spec.roundRatio * 100).roundToInt()}%",
                    color = Color.LightGray, fontSize = 12.sp
                )
                Slider(
                    value = spec.roundRatio.coerceIn(0f, 0.5f),
                    onValueChange = { onSpec(spec.copy(roundRatio = it)) },
                    valueRange = 0f..0.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = bAccent, activeTrackColor = bAccent
                    )
                )
            }
            val wpx = spec.bubbleW.roundToInt()
            val hpx = spec.bubbleH.roundToInt()
            Text(
                "Ukuran gelembung: ${wpx} x ${hpx} poin",
                color = Color.Gray, fontSize = 11.sp
            )
        }
    }
}
