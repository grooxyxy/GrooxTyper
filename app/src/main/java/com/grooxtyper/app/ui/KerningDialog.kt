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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val KernAccent = Color(0xFFFF5722)
private val KernPanel = Color(0xFF1C1C1E)

/**
 * Dialog Kerning per-pasangan (ala Photoshop/TypeR).
 *
 *_pairs_ disimpan per pasangan huruf, jadi user bisa merapatkan hanya
 * kombinasi yang bermasalah (mis. "AV") tanpa menggeser seluruh teks.
 */
@Composable
fun KerningDialog(
    pairs: Map<String, Float>,
    onDismiss: () -> Unit,
    onChange: (Map<String, Float>) -> Unit
) {
    var charA by remember { mutableStateOf("") }
    var charB by remember { mutableStateOf("") }
    var value by remember { mutableFloatStateOf(0f) }
    val draft = pairs.toMutableMap()

    fun keyOf(): String? {
        val a = charA.trim().firstOrNull() ?: return null
        val b = charB.trim().firstOrNull() ?: return null
        return "$a$b"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Kerning Huruf", color = Color.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Masukkan dua huruf berurutan lalu atur jaraknya. " +
                        "Nilai negatif = lebih rapat, positif = lebih renggang.",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = charA,
                        onValueChange = { charA = it.take(2) },
                        label = { Text("Huruf 1") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = charB,
                        onValueChange = { charB = it.take(2) },
                        label = { Text("Huruf 2") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    "Jarak ${value.toInt()} px",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    valueRange = -20f..20f,
                    colors = SliderDefaults.colors(
                        thumbColor = KernAccent,
                        activeTrackColor = KernAccent
                    )
                )
                Button(
                    onClick = {
                        val k = keyOf()
                        if (k != null) {
                            draft[k] = value
                            onChange(draft.toMap())
                        }
                    },
                    enabled = keyOf() != null,
                    colors = ButtonDefaults.buttonColors(containerColor = KernAccent),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Terapkan pasangan", fontSize = 12.sp) }

                if (draft.isNotEmpty()) {
                    Text(
                        "Pasangan tersimpan (${draft.size})",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        draft.forEach { (k, v) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF2C2C2E))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    k,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(38.dp)
                                )
                                Text(
                                    "${v.toInt()} px",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "Ubah",
                                    color = KernAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable {
                                            charA = k.firstOrNull()?.toString() ?: ""
                                            charB = k.getOrNull(1)?.toString() ?: ""
                                            value = v
                                        }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF38383A))
                                        .clickable {
                                            draft.remove(k)
                                            onChange(draft.toMap())
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Hapus pasangan",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Selesai", color = Color.Gray) }
        },
        containerColor = KernPanel
    )
}
