package com.grooxtyper.app.ui

import android.graphics.RectF
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grooxtyper.app.ml.BubbleModel
import com.grooxtyper.app.ml.DetectedBubble

private val BubAccent = Color(0xFFFF5722)
private val BubPanel = Color(0xFF2C2C2E)

/**
 * Dialog Bubble Detector: pilih model, jalankan/batalkan deteksi, atur
 * overlay & mode hapus, kelola daftar hasil deteksi. Taruh di file sendiri
 * karena badan [CanvasEditorScreen] sudah menyentuh batas method JVM 64KB.
 */
@Composable
fun BubbleDetectorDialog(
    models: List<BubbleModel>,
    model: BubbleModel,
    onModel: (BubbleModel) -> Unit,
    detected: List<DetectedBubble>,
    detecting: Boolean,
    status: String?,
    showOverlay: Boolean,
    onOverlay: (Boolean) -> Unit,
    eraseMode: Boolean,
    onEraseMode: (Boolean) -> Unit,
    hasSelection: Boolean,
    multiCount: Int,
    onDetect: () -> Unit,
    onCancel: () -> Unit,
    onAddFromSelection: () -> Unit,
    onSelectBox: () -> Unit,
    onClearAll: () -> Unit,
    onFocus: (RectF) -> Unit,
    onRemove: (Int) -> Unit,
    onAutoFill: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Bubble Detector", color = Color.White) },
        text = {
            Column {
                Text(
                    if (detecting) "Mendeteksi bubble..."
                    else "Ditemukan ${detected.size} bubble (mentah, tanpa refine).",
                    color = Color.LightGray, fontSize = 13.sp
                )
                if (status != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(status, color = Color.Gray, fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Model (pilih satu):",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp
                )
                models.forEach { m ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModel(m) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = model == m, onClick = { onModel(m) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                m.displayName, color = Color.White, fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(m.desc, color = Color.Gray, fontSize = 11.sp)
                            Text(m.asset, color = Color.Gray, fontSize = 10.sp)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onDetect,
                        enabled = !detecting,
                        colors = ButtonDefaults.buttonColors(containerColor = BubPanel),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            if (detecting) "Mendeteksi..." else "Deteksi",
                            color = Color.White, fontSize = 12.sp
                        )
                    }
                    if (detecting) {
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = onCancel) {
                            Text("Batal", color = Color.Red, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Overlay", color = Color.White, fontSize = 12.sp)
                    }
                    Switch(
                        checked = showOverlay,
                        onCheckedChange = onOverlay,
                        colors = SwitchDefaults.colors(checkedThumbColor = BubAccent)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mode hapus (ketuk bubble)", color = Color.White, fontSize = 12.sp)
                    }
                    Switch(
                        checked = eraseMode,
                        onCheckedChange = onEraseMode,
                        colors = SwitchDefaults.colors(checkedThumbColor = BubAccent)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onAddFromSelection,
                        enabled = hasSelection,
                        colors = ButtonDefaults.buttonColors(containerColor = BubPanel),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("+ Dari Seleksi", color = Color.White, fontSize = 11.sp) }
                    Button(
                        onClick = onSelectBox,
                        colors = ButtonDefaults.buttonColors(containerColor = BubPanel),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("Kotak Seleksi", color = Color.White, fontSize = 11.sp) }
                    Button(
                        onClick = onClearAll,
                        enabled = detected.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = BubPanel),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Hapus Semua", color = Color.White, fontSize = 11.sp) }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Daftar bubble (ketuk ikon hapus untuk buang):",
                    color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
                if (detected.isEmpty()) {
                    Text(
                        "Belum ada bubble. Jalankan Deteksi atau tambah via Kotak Seleksi.",
                        color = Color.Gray, fontSize = 11.sp
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    ) {
                        itemsIndexed(detected) { idx, b ->
                            val r = b.boundingBox
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "#${idx + 1} (${r.left.toInt()},${r.top.toInt()} ${r.width().toInt()}x${r.height().toInt()})",
                                        color = Color.White, fontSize = 12.sp
                                    )
                                }
                                TextButton(onClick = { onFocus(r) }) {
                                    Text("Fokus", color = BubAccent, fontSize = 11.sp)
                                }
                                IconButton(
                                    onClick = { onRemove(idx) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Hapus bubble ${idx + 1}",
                                        tint = Color.Red,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Ketuk bubble di mode Lasso/Kotak untuk TAMBAH ke seleksi (multi). Ketuk area terseleksi untuk menghapusnya. Drag di tool Kotak Seleksi untuk tambah area persegi.",
                    color = Color.Gray, fontSize = 11.sp
                )
                if (multiCount == 0) {
                    Text(
                        "Isi draft multi-bubble dulu untuk pakai Isi Otomatis.",
                        color = Color.Gray, fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAutoFill,
                enabled = multiCount > 0 && detected.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = BubAccent)
            ) { Text("Isi Otomatis", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onClose) {
                Text("Tutup", color = Color.Gray)
            }
        },
        containerColor = BubPanel
    )
}
