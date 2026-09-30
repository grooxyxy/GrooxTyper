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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grooxtyper.app.model.BubbleAreaPipeline

/**
 * Panel area bubble: daftar area bernomor hasil ketukan wand.
 *
 * Alurnya mengikuti video md typer: satu gambar, beberapa area bernomor dalam
 * urutan baca, lalu satu tombol untuk mengisi semuanya dari script dialog.
 * Panel ini sengaja file terpisah karena `CanvasEditorScreen` sudah mendekati
 * batas metode JVM 64KB.
 */
@Composable
fun BubbleAreaPanel(
    areas: List<BubbleAreaPipeline.Area>,
    filled: List<String>,
    panelMode: Boolean,
    busy: Boolean,
    onPanelMode: (Boolean) -> Unit,
    onFillFromScript: () -> Unit,
    onRemoveAt: (Int) -> Unit,
    onClear: () -> Unit,
    onClose: () -> Unit
) {
    if (areas.isEmpty() && !panelMode) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(PanelBg)
            .clickable(
                interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
                indication = null,
                onClick = {}
            )
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Area Bubble",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    if (areas.isEmpty()) "Ketuk gelembung di kanvas untuk menambah area"
                    else "${areas.size} area - bernomor urutan baca manga",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
            Text(
                "Tutup",
                color = Color.LightGray,
                fontSize = 12.sp,
                modifier = Modifier.clickable { onClose() }.padding(6.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Mode Area Panel: satu area dari wilayah yang diketuk, tanpa pemecahan.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (panelMode) Accent else Color(0xFF2C2C2E))
                    .clickable { onPanelMode(!panelMode) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    "Area Panel",
                    color = if (panelMode) Color.White else Color.LightGray,
                    fontSize = 11.sp,
                    fontWeight = if (panelMode) FontWeight.Bold else FontWeight.Normal
                )
            }
            Text(
                if (panelMode) "Mode panel: 1 area per ketukan, untuk kotak narasi"
                else "Mode bubble: gelembung bersinggungan dipisah otomatis",
                color = Color.Gray,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            areas.forEachIndexed { i, a ->
                val text = filled.getOrElse(i) { "" }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (a.kind == BubbleAreaPipeline.KIND_PANEL) Color(0xFF3A5A40) else Accent
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${i + 1}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (text.isBlank()) "belum terisi" else text,
                            color = if (text.isBlank()) Color.Gray else Color.White,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${a.bounds.width().toInt()} x ${a.bounds.height().toInt()} px" +
                                if (a.kind == BubbleAreaPipeline.KIND_PANEL) " - area panel" else "",
                            color = Color(0xFF7A7A7A),
                            fontSize = 10.sp
                        )
                    }
                    Text(
                        "Hapus",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .clickable { onRemoveAt(i) }
                            .padding(6.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onFillFromScript,
                enabled = areas.isNotEmpty() && !busy,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Accent)
            ) {
                Text(
                    if (busy) "Memproses..." else "Isi Area",
                    fontSize = 12.sp,
                    color = Color.White
                )
            }
            Button(
                onClick = onClear,
                enabled = areas.isNotEmpty(),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2E))
            ) {
                Text("Hapus Semua", fontSize = 12.sp, color = Color.White)
            }
        }
    }
}
