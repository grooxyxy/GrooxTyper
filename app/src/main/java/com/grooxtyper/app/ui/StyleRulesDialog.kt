package com.grooxtyper.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grooxtyper.app.model.StyleRule
import com.grooxtyper.app.model.TextStylePreset

private val RulesAccent = Color(0xFFFF5722)
private val RulesPanel = Color(0xFF2C2C2E)

/**
 * Dialog Style Rules: aturan "kalimat berawalan X -> pakai style Y".
 * Taruh di file sendiri karena badan [CanvasEditorScreen] sudah menyentuh
 * batas method JVM 64KB (build gagal "Method too large").
 */
@Composable
fun StyleRulesDialog(
    presets: List<TextStylePreset>,
    rules: List<StyleRule>,
    prefix: String,
    onPrefix: (String) -> Unit,
    styleId: String,
    onStyleId: (String) -> Unit,
    stripPrefix: Boolean,
    onStripPrefix: (Boolean) -> Unit,
    onSave: (StyleRule) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Style Rules", color = Color.White) },
        text = {
            Column {
                Text(
                    "Jika baris diawali prefix, pakai style tsb dan awalan dihapus saat render. Contoh: '() : ' -> Style A, '\"\": ' -> Style B.",
                    color = Color.Gray, fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = prefix,
                    onValueChange = onPrefix,
                    label = { Text("Awalan, cth: () : ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (presets.isEmpty()) {
                    Text(
                        "Belum ada Style. Buat dulu di panel Teks > tab Style > Simpan.",
                        color = Color.Gray, fontSize = 11.sp
                    )
                } else {
                    Text(
                        "Pilih style:",
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(110.dp)) {
                        itemsIndexed(presets) { _, p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (styleId == p.id) RulesAccent else RulesPanel)
                                    .clickable { onStyleId(p.id) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    p.name, color = Color.White, fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                if (p.prefix.isNotBlank()) {
                                    Text("[${p.prefix}]", color = Color.Gray, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hapus awalan saat render", color = Color.White, fontSize = 12.sp)
                    }
                    Switch(
                        checked = stripPrefix,
                        onCheckedChange = onStripPrefix,
                        colors = SwitchDefaults.colors(checkedThumbColor = RulesAccent)
                    )
                }
                Button(
                    onClick = {
                        if (prefix.isNotEmpty() && styleId.isNotEmpty()) {
                            onSave(
                                StyleRule(
                                    prefix = prefix,
                                    styleId = styleId,
                                    stripPrefix = stripPrefix
                                )
                            )
                            onPrefix("")
                        }
                    },
                    enabled = prefix.isNotEmpty() && styleId.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = RulesAccent),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Tambah Rule", color = Color.White) }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Daftar rules (${rules.size}) — ketuk ikon hapus untuk buang:",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
                if (rules.isEmpty()) {
                    Text("Belum ada rule.", color = Color.Gray, fontSize = 11.sp)
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                        itemsIndexed(rules) { _, r ->
                            val sName = presets.find { it.id == r.styleId }?.name ?: "(style terhapus)"
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "'${r.prefix}' -> $sName",
                                        color = Color.White, fontSize = 12.sp
                                    )
                                    Text(
                                        if (r.stripPrefix) "awalan dihapus" else "awalan dipertahankan",
                                        color = Color.Gray, fontSize = 10.sp
                                    )
                                }
                                IconButton(
                                    onClick = { onDelete(r.id) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Hapus rule",
                                        tint = Color.Red,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = RulesAccent)
            ) { Text("Selesai", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text("Tutup", color = Color.Gray)
            }
        },
        containerColor = RulesPanel
    )
}
