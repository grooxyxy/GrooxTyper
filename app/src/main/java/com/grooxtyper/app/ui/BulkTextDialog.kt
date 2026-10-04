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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.grooxtyper.app.model.TextLayer

// Warna lokal file (setiap file UI mendefinisikan sendiri; lihat
// BrushDrawerPanel/TextEditorPanel — JANGAN internal agar tak bentrok).
private val BulkAccent = Color(0xFFFF5722)
private val BulkPanelBg = Color(0xFF2C2C2E)

/**
 * Dialog Edit Teks Massal: pilih beberapa kotak teks, terapkan ukuran /
 * warna / tebal / miring / font sekaligus, atau hapus yang dipilih.
 * Ditaruh di file sendiri (bukan di badan CanvasEditorScreen) karena
 * composable raksasa melebihi batas method JVM 64KB → build gagal
 * "Method too large".
 */
@Composable
fun BulkTextDialog(
    layers: List<TextLayer>,
    initialChecked: Set<String>,
    initialSize: Float,
    initialBold: Boolean,
    initialItalic: Boolean,
    initialFontName: String?,
    initialColor: Int,
    fonts: List<Pair<String, android.graphics.Typeface>>,
    onApply: (ids: Set<String>, size: Float, bold: Boolean, italic: Boolean, fontName: String?, color: Int) -> Unit,
    onDelete: (ids: Set<String>) -> Unit,
    onEmptySelection: () -> Unit,
    onClose: () -> Unit
) {
    var bulkChecked by remember { mutableStateOf(initialChecked) }
    var bulkSize by remember { mutableFloatStateOf(initialSize) }
    var bulkBold by remember { mutableStateOf(initialBold) }
    var bulkItalic by remember { mutableStateOf(initialItalic) }
    var bulkFontName by remember { mutableStateOf(initialFontName) }
    var bulkColor by remember { mutableIntStateOf(initialColor) }
    var showColor by remember { mutableStateOf(false) }
    var fontExpanded by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Edit Teks Massal", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Centang kotak teks, lalu terapkan ukuran/warna/tebal sekaligus.",
                    color = Color.Gray, fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (layers.isEmpty()) {
                    Text("Belum ada teks di kanvas.", color = Color.LightGray, fontSize = 12.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        layers.forEach { tl ->
                            val checked = tl.id in bulkChecked
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (checked) Color(0xFF1F3D2B) else BulkPanelBg)
                                    .clickable {
                                        bulkChecked = if (checked) bulkChecked - tl.id else bulkChecked + tl.id
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        bulkChecked = if (checked) bulkChecked - tl.id else bulkChecked + tl.id
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = BulkAccent)
                                )
                                Text(
                                    tl.box.text.take(28).ifBlank { "(kosong)" },
                                    color = if (checked) Color.White else Color.Gray,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Ukuran font: ${bulkSize.toInt()}px", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = bulkSize,
                    onValueChange = { bulkSize = it },
                    valueRange = 1f..220f,
                    colors = SliderDefaults.colors(thumbColor = BulkAccent, activeTrackColor = BulkAccent)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tebal (bold)", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = bulkBold,
                        onCheckedChange = { bulkBold = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = BulkAccent)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Miring (italic)", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = bulkItalic,
                        onCheckedChange = { bulkItalic = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = BulkAccent)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Warna teks", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(bulkColor))
                            .clickable { showColor = true }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Font: ${bulkFontName ?: "Campuran"}",
                        color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { fontExpanded = !fontExpanded }) {
                        Text(if (fontExpanded) "Tutup" else "Pilih", color = BulkAccent, fontSize = 12.sp)
                    }
                }
                if (fontExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (fonts.isEmpty()) {
                            Text("Belum ada font.", color = Color.Gray, fontSize = 12.sp)
                        }
                        fonts.forEach { (name, _) ->
                            Text(
                                name,
                                color = if (name == bulkFontName) BulkAccent else Color.LightGray,
                                fontSize = 12.sp,
                                fontWeight = if (name == bulkFontName) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (name == bulkFontName) Color(0xFF1F3D2B) else BulkPanelBg)
                                    .clickable { bulkFontName = name; fontExpanded = false }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        if (bulkChecked.isEmpty()) {
                            onEmptySelection()
                            return@Button
                        }
                        onApply(bulkChecked, bulkSize, bulkBold, bulkItalic, bulkFontName, bulkColor)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BulkAccent),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Terapkan ke ${bulkChecked.size} teks", color = Color.White, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) {
                Text("Selesai", color = BulkAccent)
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onDelete(bulkChecked) },
                enabled = bulkChecked.isNotEmpty()
            ) { Text("Hapus dipilih", color = Color.Red) }
        },
        containerColor = BulkPanelBg
    )
    if (showColor) {
        ColorPickerDialog(
            initialColor = bulkColor,
            onColorSelected = { bulkColor = it },
            onDismiss = { showColor = false }
        )
    }
}
