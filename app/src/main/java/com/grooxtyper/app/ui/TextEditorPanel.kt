package com.grooxtyper.app.ui

import android.graphics.Typeface
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import com.grooxtyper.app.model.TextAlignMode
import com.grooxtyper.app.model.TextBox

private val Accent = Color(0xFFFF5722)
private val PanelBg = Color(0xFF1C1C1E)
private val PanelLight = Color(0xFF2C2C2E)

/**
 * Panel teks baru: 2 tab saja, semua perubahan LANGSUNG
 * terlihat di kanvas (live). Tidak ada tombol Apply.
 */
@Composable
fun TextEditorPanel(
    box: TextBox,
    fonts: List<Pair<String, Typeface>>,
    onImportFont: () -> Unit,
    onChange: () -> Unit,
    onFlatten: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var text by remember(box.id) { mutableStateOf(box.text) }
    var fontSize by remember(box.id) { mutableFloatStateOf(box.fontSize) }
    var colorVal by remember(box.id) { mutableIntStateOf(box.color) }
    var bold by remember(box.id) { mutableStateOf(box.bold) }
    var italic by remember(box.id) { mutableStateOf(box.italic) }
    var align by remember(box.id) { mutableStateOf(box.align) }
    var outlineW by remember(box.id) { mutableFloatStateOf(box.outlineWidth) }
    var outlineColor by remember(box.id) { mutableIntStateOf(box.outlineColor) }
    var shadowOn by remember(box.id) { mutableStateOf(box.shadow != null) }
    var letterSp by remember(box.id) { mutableFloatStateOf(box.letterSpacing) }
    var lineSp by remember(box.id) { mutableFloatStateOf(box.lineSpacing) }
    var showColor by remember { mutableStateOf(false) }
    var colorTarget by remember { mutableIntStateOf(0) } // 0 teks, 1 outline, 2 shadow

    fun push() = onChange()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(430.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(PanelBg)
            .border(1.dp, Color(0xFF38383A), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.Gray)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Teks", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row {
                    IconButton(onClick = onFlatten) {
                        Icon(Icons.Default.Layers, contentDescription = "Flatten", tint = Color.White)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color(0xFFEF5350))
                    }
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Selesai", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(PanelLight)
                    .padding(4.dp)
            ) {
                listOf("Tulis", "Gaya").forEachIndexed { i, t ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (tab == i) Accent else Color.Transparent)
                            .clickable { tab = i }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(t, color = if (tab == i) Color.White else Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (tab == 0) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it; box.text = it.ifEmpty { " " }; push() },
                        label = { Text("Isi teks") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Ukuran ${fontSize.toInt()} px", color = Color.Gray, fontSize = 12.sp)
                    Slider(
                        value = fontSize, onValueChange = { fontSize = it; box.fontSize = it; push() },
                        valueRange = 20f..220f,
                        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StyleChip("B", bold, Icons.Default.FormatBold) { bold = it; box.bold = it; push() }
                        StyleChip("I", italic, Icons.Default.FormatItalic) { italic = it; box.italic = it; push() }
                        Spacer(modifier = Modifier.weight(1f))
                        AlignButton(align == TextAlignMode.LEFT, Icons.Default.FormatAlignLeft) { align = TextAlignMode.LEFT; box.align = align; push() }
                        AlignButton(align == TextAlignMode.CENTER, Icons.Default.FormatAlignCenter) { align = TextAlignMode.CENTER; box.align = align; push() }
                        AlignButton(align == TextAlignMode.RIGHT, Icons.Default.FormatAlignRight) { align = TextAlignMode.RIGHT; box.align = align; push() }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Font", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Import TTF/OTF", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { onImportFont() })
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(fonts) { (name, _) ->
                            val sel = box.fontName == name
                            Card(
                                modifier = Modifier.clickable {
                                    val found = fonts.find { it.first == name }
                                    if (found != null) {
                                        box.typeface = found.second; box.fontName = name; push()
                                    }
                                },
                                colors = CardDefaults.cardColors(containerColor = if (sel) Accent else PanelLight),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(name, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                            }
                        }
                    }
                } else {
                    ColorRow("Warna teks", colorVal) { colorTarget = 0; showColor = true }
                    ColorRow("Warna outline", outlineColor) { colorTarget = 1; showColor = true }
                    Text("Tebal outline ${outlineW.toInt()} px", color = Color.Gray, fontSize = 12.sp)
                    Slider(
                        value = outlineW, onValueChange = { outlineW = it; box.outlineWidth = it; push() },
                        valueRange = 0f..30f,
                        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Bayangan", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), fontSize = 13.sp)
                        Switch(
                            checked = shadowOn,
                            onCheckedChange = {
                                shadowOn = it
                                box.shadow = if (it) com.grooxtyper.app.model.TextShadowSpec() else null
                                push()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Accent)
                        )
                    }
                    Text("Spasi huruf ${letterSp.toInt()} px", color = Color.Gray, fontSize = 12.sp)
                    Slider(
                        value = letterSp, onValueChange = { letterSp = it; box.letterSpacing = it; push() },
                        valueRange = 0f..40f,
                        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
                    )
                    Text("Spasi baris ${lineSp.toInt()} px", color = Color.Gray, fontSize = 12.sp)
                    Slider(
                        value = lineSp, onValueChange = { lineSp = it; box.lineSpacing = it; push() },
                        valueRange = 0f..80f,
                        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
                    )
                }
            }
        }
    }

    if (showColor) {
        ColorPickerDialog(
            initialColor = when (colorTarget) {
                1 -> outlineColor
                2 -> box.shadow?.color ?: android.graphics.Color.BLACK
                else -> colorVal
            },
            onColorSelected = { c ->
                when (colorTarget) {
                    1 -> { outlineColor = c; box.outlineColor = c }
                    2 -> { box.shadow?.color = c }
                    else -> { colorVal = c; box.color = c }
                }
                push()
            },
            onDismiss = { showColor = false }
        )
    }
}

@Composable
private fun StyleChip(
    label: String,
    active: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onToggle: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Accent else PanelLight)
            .clickable { onToggle(!active) }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AlignButton(
    active: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Accent else PanelLight)
            .size(40.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ColorRow(title: String, colorVal: Int, onPick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(colorVal))
                .border(2.dp, Color.White, CircleShape)
                .clickable { onPick() }
        )
    }
}
