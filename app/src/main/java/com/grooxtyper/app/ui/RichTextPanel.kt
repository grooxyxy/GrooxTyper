package com.grooxtyper.app.ui

import android.graphics.Color as AndroidColor
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.grooxtyper.app.model.SpanStyle
import com.grooxtyper.app.model.TextBox
import kotlin.math.roundToInt

private val RichAccent = Color(0xFFFF5722)
private val RichPanel = Color(0xFF1C1C1E)

/**
 * Panel GAYA PER KATA: satu kalimat bisa punya gaya berbeda tiap kata.
 *
 * Contoh dari user: "Ayo main sama om" -> kata "Ayo" dan "main" serif merah
 * bold + shadow putih, sedangkan "sama" dan "om" script putih italic +
 * outline hitam.
 *
 * Alur: centang kata di daftar (bisa banyak), ubah gaya di bawah, tombol
 * Terapkan menulis SpanStyle ke rentang karakter tiap kata terpilih.Undo
 * ditangani pemanggil (satu langkah untuk seluruh kotak).
 */
@Composable
fun RichTextPanel(
    box: TextBox,
    fonts: List<Pair<String, android.graphics.Typeface>>,
    defaultColor: Int,
    onApply: () -> Unit,
    onClose: () -> Unit
) {
    val words = remember(box.id, box.text) { box.wordRanges() }
    val sel = remember(box.id, box.text) { mutableStateOf(setOf<Int>()) }
    val chosen = sel.value

    // Gaya kerja (dibaca dari kata pertama yang terpilih).
    val firstIdx = chosen.minOrNull() ?: 0
    val firstSpan = words.getOrNull(firstIdx)?.let { box.spanAt(it.first) }
    var fontName by remember(box.id, box.text, chosen) {
        mutableStateOf(
            firstSpan?.fontName ?: words.getOrNull(firstIdx)?.let { box.spanAt(it.first) }?.fontName ?: box.fontName
        )
    }
    var sizeMul by remember(box.id, box.text, chosen) {
        mutableFloatStateOf(firstSpan?.fontSizeMul ?: 1f)
    }
    var wordColor by remember(box.id, box.text, chosen) {
        mutableStateOf(firstSpan?.color ?: defaultColor)
    }
    var boldOn by remember(box.id, box.text, chosen) {
        mutableStateOf(firstSpan?.bold ?: box.bold)
    }
    var italicOn by remember(box.id, box.text, chosen) {
        mutableStateOf(firstSpan?.italic ?: box.italic)
    }
    var shadowOn by remember(box.id, box.text, chosen) {
        mutableStateOf(box.shadow != null)
    }
    var outlineOn by remember(box.id, box.text, chosen) {
        mutableStateOf((firstSpan?.outlineWidth ?: box.outlineWidth) > 0f)
    }
    var outlineW by remember(box.id, box.text, chosen) {
        mutableFloatStateOf(firstSpan?.outlineWidth ?: box.outlineWidth.coerceAtLeast(6f))
    }
    var fontMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .padding(bottom = 72.dp, start = 10.dp, end = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xF01C1C1E))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .heightIn(max = 430.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Gaya per kata",
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${chosen.size} kata dipilih",
                color = Color.Gray, fontSize = 10.sp
            )
        }
        // Daftar kata: ketuk untuk centang/batal centang.
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            words.forEachIndexed { i, range ->
                val word = box.text.substring(range.first, range.last + 1)
                val st = box.spanAt(range.first)
                val checked = i in chosen
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (checked) Color(0xFF1F3D2B) else RichPanel)
                        .clickable {
                            sel.value = if (checked) chosen - i else chosen + i
                        }
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = {
                            sel.value = if (checked) chosen - i else chosen + i
                        },
                        colors = CheckboxDefaults.colors(checkedColor = RichAccent),
                        modifier = Modifier.height(28.dp)
                    )
                    Text(
                        word,
                        color = if (checked) Color.White else Color(0xFFB0B0B0),
                        fontSize = 13.sp,
                        fontWeight = if (st?.bold == true) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                    if (st != null && !st.isBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(width = 12.dp, height = 12.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(st?.color ?: box.color))
                        )
                        Text(
                            "beda",
                            color = RichAccent, fontSize = 9.sp
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        // Font kata terpilih.
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(RichPanel)
                    .clickable { fontMenu = true }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Text(
                    "Font kata: $fontName",
                    color = Color.White, fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Text("Ubah", color = RichAccent, fontSize = 11.sp)
            }
            DropdownMenu(expanded = fontMenu, onDismissRequest = { fontMenu = false }) {
                fonts.forEach { (name, _) ->
                    DropdownMenuItem(
                        text = { Text(name, color = Color.White, fontSize = 12.sp) },
                        onClick = {
                            fontName = name
                            fontMenu = false
                        }
                    )
                }
            }
        }
        Text(
            "Ukuran kata: ${(sizeMul * 100).roundToInt()}%",
            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
        )
        Slider(
            value = sizeMul,
            onValueChange = { sizeMul = it.coerceIn(0.4f, 2.5f) },
            valueRange = 0.4f..2.5f,
            colors = SliderDefaults.colors(thumbColor = RichAccent, activeTrackColor = RichAccent)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Warna kata", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(wordColor))
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(26.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val swatches = listOf(
                AndroidColor.WHITE, AndroidColor.BLACK, AndroidColor.RED,
                AndroidColor.parseColor("#FF5722"), AndroidColor.parseColor("#FFD54F"),
                AndroidColor.parseColor("#4CAF50"), AndroidColor.parseColor("#2196F3"),
                AndroidColor.parseColor("#9C27B0")
            )
            swatches.forEach { c ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color(c))
                        .clickable { wordColor = c }
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Tebal", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = boldOn,
                onCheckedChange = { boldOn = it },
                colors = SwitchDefaults.colors(checkedThumbColor = RichAccent)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Miring", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = italicOn,
                onCheckedChange = { italicOn = it },
                colors = SwitchDefaults.colors(checkedThumbColor = RichAccent)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Bayangan", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = shadowOn,
                onCheckedChange = { shadowOn = it },
                colors = SwitchDefaults.colors(checkedThumbColor = RichAccent)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Outline", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = outlineOn,
                onCheckedChange = { outlineOn = it },
                colors = SwitchDefaults.colors(checkedThumbColor = RichAccent)
            )
        }
        if (outlineOn) {
            Text(
                "Tebal outline: ${outlineW.roundToInt()}",
                color = Color(0xFF9AA0A6), fontSize = 10.sp, fontWeight = FontWeight.Bold
            )
            Slider(
                value = outlineW,
                onValueChange = { outlineW = it },
                valueRange = 0f..24f,
                colors = SliderDefaults.colors(thumbColor = RichAccent, activeTrackColor = RichAccent)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (chosen.isEmpty()) return@Button
                    val st = SpanStyle(
                        fontName = fontName,
                        // Typeface ikut disimpan agar font kustom langsung
                        // berlaku tanpa perlu buka daftar font lagi.
                        typeface = fonts.firstOrNull { it.first == fontName }?.second,
                        fontSizeMul = sizeMul,
                        color = wordColor,
                        bold = boldOn,
                        italic = italicOn,
                        outlineWidth = if (outlineOn) outlineW else null,
                        outlineColor = if (outlineOn) AndroidColor.BLACK else null,
                        shadowColor = if (shadowOn) (box.shadow?.color ?: 0x80000000.toInt()) else null,
                        shadowDx = if (shadowOn) (box.shadow?.dx ?: 4f) else null,
                        shadowDy = if (shadowOn) (box.shadow?.dy ?: 4f) else null,
                        shadowBlur = if (shadowOn) (box.shadow?.blur ?: 8f) else null
                    )
                    for (i in chosen) {
                        val r = words.getOrNull(i) ?: continue
                        box.applySpanStyle(r.first, r.last + 1, st)
                    }
                    onApply()
                },
                colors = ButtonDefaults.buttonColors(containerColor = RichAccent),
                modifier = Modifier.weight(1f)
            ) { Text("Terapkan", color = Color.White, fontSize = 12.sp) }
            TextButton(
                onClick = {
                    for (i in chosen) {
                        val r = words.getOrNull(i) ?: continue
                        box.applySpanStyle(r.first, r.last + 1, SpanStyle())
                    }
                    onApply()
                },
                enabled = chosen.isNotEmpty()
            ) { Text("Hapus gaya", color = Color(0xFFEF5350), fontSize = 11.sp) }
            TextButton(onClick = onClose) { Text("Tutup", color = RichAccent, fontSize = 11.sp) }
        }
        TextButton(
            onClick = {
                box.clearSpans()
                sel.value = emptySet()
                onApply()
            }
        ) { Text("Reset semua gaya kata", color = Color.Gray, fontSize = 11.sp) }
    }
}
