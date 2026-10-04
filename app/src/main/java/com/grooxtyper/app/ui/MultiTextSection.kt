package com.grooxtyper.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val MultiAccent = Color(0xFFFF5722)
private val MultiFieldBg = Color(0xFF38383A)

/**
 * Tab "Multi" di panel teks: ketik/tempel banyak baris sekaligus, satu
 * baris menjadi satu kotak teks. Gaya (font, warna, ukuran) mengikuti
 * kotak teks yang sedang dipilih, jadi multi-teks menyatu dengan fitur
 * teks biasa, bukan alur terpisah.
 *
 * File sendiri (bukan di TextEditorPanel) karena batas method JVM 64KB.
 */
@Composable
fun MultiTextTab(
    onCreateLines: (List<String>) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    val lines = remember(draft) {
        draft.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Multi teks: satu baris = satu kotak teks. Gaya mengikuti kotak yang dipilih.",
            color = Color.Gray, fontSize = 11.sp
        )
        TextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = { Text("Tulis tiap dialog di baris sendiri…", color = Color.Gray, fontSize = 12.sp) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 220.dp),
            shape = RoundedCornerShape(10.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MultiFieldBg,
                unfocusedContainerColor = MultiFieldBg,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = MultiAccent,
                focusedIndicatorColor = MultiAccent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            maxLines = 20
        )
        Button(
            onClick = { if (lines.isNotEmpty()) { onCreateLines(lines); draft = "" } },
            enabled = lines.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = MultiAccent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (lines.isEmpty()) "Buat kotak teks" else "Buat ${lines.size} kotak teks",
                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}
