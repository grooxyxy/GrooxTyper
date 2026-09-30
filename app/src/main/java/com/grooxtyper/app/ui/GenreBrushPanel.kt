package com.grooxtyper.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.grooxtyper.app.model.BrushEngine
import com.grooxtyper.app.model.GenreBrushEngine
import kotlin.math.roundToInt

private val gAccent = Color(0xFFFF5722)
private val gPanelBgLight = Color(0xFF2C2C2E)

/** Slot warna yang sedang dibuka pemilihnya. */
private const val SLOT_NONE = 0
private const val SLOT_GRAD_START = 1
private const val SLOT_GRAD_END = 2
private const val SLOT_OUTLINE = 3
private const val SLOT_SHADOW = 4

/**
 * Panel setelan kuas SFX bergenre (horror / romance / action / fantasy).
 *
 * Semua yang bisa diatur ada di sini dan langsung dipakai render berikutnya:
 * lebar, gradasi (dua warna + sudut), opacity, outline, bayangan, dan tekstur.
 * Sengaja tanpa preview realtime - nilai yang baru diset langsung dipakai
 * goresan berikutnya, sesuai permintaan (cukup bisa diedit sesuai keinginan).
 */
@Composable
fun GenreBrushPanel(brushEngine: BrushEngine) {
    val store = brushEngine.genreSettings
    var picked by remember { mutableStateOf<GenreBrushEngine.Genre?>(null) }
    val genre = picked ?: GenreBrushEngine.genreOf(brushEngine.brushType)
        ?: GenreBrushEngine.Genre.HORROR
    val st = store.of(genre)
    var colorSlot by remember { mutableIntStateOf(SLOT_NONE) }

    Column {
        GenreUi.chips(genre) { picked = it }

        Button(
            onClick = { st.applyPreset(genre) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text("Kembalikan bawaan ${genre.displayName}", fontSize = 12.sp, color = Color.White)
        }

        Text("Salin setelan ini ke:", color = Color.Gray, fontSize = 11.sp)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GenreBrushEngine.Genre.values().filter { it != genre }.forEach { other ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(gPanelBgLight)
                        .clickable { store.of(other).copyFrom(st) }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(other.displayName, color = Color.LightGray, fontSize = 10.sp)
                }
            }
        }

        GenreUi.label("Lebar: ${(st.widthMul * 100).roundToInt()}%")
        GenreUi.slider(st.widthMul, 0.3f, 2.5f) { st.widthMul = it }

        GenreUi.label("Opacity: ${(st.opacity * 100).roundToInt()}%")
        GenreUi.slider(st.opacity, 0.05f, 1f) { st.opacity = it }

        GenreUi.divider("Gradasi")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Pakai gradasi", color = Color.White, fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = st.gradient,
                onCheckedChange = { st.gradient = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = gAccent,
                    checkedTrackColor = gAccent.copy(alpha = 0.5f)
                )
            )
        }
        if (st.gradient) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GenreUi.swatch(st.gradStart) { colorSlot = SLOT_GRAD_START }
                GenreUi.swatch(st.gradEnd) { colorSlot = SLOT_GRAD_END }
                Text("Warna gradasi", color = Color.Gray, fontSize = 11.sp)
            }
            GenreUi.label("Sudut gradasi: ${st.gradAngle.roundToInt()} derajat")
            GenreUi.slider(st.gradAngle, 0f, 180f) { st.gradAngle = it }
        }

        GenreUi.divider("Outline")
        GenreUi.label("Tebal outline: ${(st.outlineWidth * 100).roundToInt()}%")
        GenreUi.slider(st.outlineWidth, 0f, 1.2f) { st.outlineWidth = it }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            GenreUi.swatch(st.outlineColor) { colorSlot = SLOT_OUTLINE }
            Text("Warna outline", color = Color.Gray, fontSize = 11.sp)
        }

        GenreUi.divider("Bayangan")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Pakai bayangan", color = Color.White, fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = st.shadowOn,
                onCheckedChange = { st.shadowOn = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = gAccent,
                    checkedTrackColor = gAccent.copy(alpha = 0.5f)
                )
            )
        }
        if (st.shadowOn) {
            GenreUi.label("Geser mendatar: ${(st.shadowDx * 100).roundToInt()}% lebar kuas")
            GenreUi.slider(st.shadowDx, -0.3f, 0.3f) { st.shadowDx = it }
            GenreUi.label("Geser tegak: ${(st.shadowDy * 100).roundToInt()}% lebar kuas")
            GenreUi.slider(st.shadowDy, -0.3f, 0.3f) { st.shadowDy = it }
            GenreUi.label("Kabut: ${st.shadowBlur.roundToInt()}")
            GenreUi.slider(st.shadowBlur, 0f, 40f) { st.shadowBlur = it }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GenreUi.swatch(st.shadowColor) { colorSlot = SLOT_SHADOW }
                Text("Warna bayangan", color = Color.Gray, fontSize = 11.sp)
            }
        }

        GenreUi.divider("Tekstur")
        GenreUi.label("Kasar tepi: ${(st.texture * 100).roundToInt()}%")
        GenreUi.slider(st.texture, 0f, 1f) { st.texture = it }
        GenreUi.label("Percikan: ${st.spatter}")
        GenreUi.slider(st.spatter.toFloat(), 0f, 40f) { st.spatter = it.roundToInt() }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "Bentuk tiap genre memang beda: horror bergerigi + tetesan tinta, " +
                "romance lembut, action berepersi + garis kecepatan, fantasy " +
                "bergelombang + kilau bintang.",
            color = Color.Gray, fontSize = 11.sp
        )
    }

    if (colorSlot != SLOT_NONE) {
        ColorPickerDialog(
            initialColor = when (colorSlot) {
                SLOT_GRAD_START -> st.gradStart
                SLOT_GRAD_END -> st.gradEnd
                SLOT_OUTLINE -> st.outlineColor
                else -> st.shadowColor
            },
            onColorSelected = { c ->
                when (colorSlot) {
                    SLOT_GRAD_START -> st.gradStart = c
                    SLOT_GRAD_END -> st.gradEnd = c
                    SLOT_OUTLINE -> st.outlineColor = c
                    SLOT_SHADOW -> st.shadowColor = c
                }
            },
            onDismiss = { colorSlot = SLOT_NONE }
        )
    }
}

/** Label + slider + chip + kotak warna untuk panel genre. */
private object GenreUi {
    @Composable
    fun label(text: String) {
        Text(text, color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }

    @Composable
    fun slider(value: Float, lo: Float, hi: Float, onChange: (Float) -> Unit) {
        Slider(
            value = value.coerceIn(lo, hi),
            onValueChange = onChange,
            valueRange = lo..hi,
            colors = SliderDefaults.colors(thumbColor = gAccent, activeTrackColor = gAccent)
        )
    }

    @Composable
    fun divider(text: String) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }

    /** Empat chip genre (nama persis seperti di daftar kuas). */
    @Composable
    fun chips(selected: GenreBrushEngine.Genre, onPick: (GenreBrushEngine.Genre) -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GenreBrushEngine.Genre.values().forEach { g ->
                val on = g == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (on) gAccent else gPanelBgLight)
                        .clickable { onPick(g) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        g.displayName,
                        color = if (on) Color.White else Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }

    /** Kotak warna kecil; ketuk untuk membuka pemilih warna. */
    @Composable
    fun swatch(color: Int, onPick: () -> Unit) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color(color))
                .border(1.dp, Color.Gray, CircleShape)
                .clickable { onPick() }
        )
    }
}
