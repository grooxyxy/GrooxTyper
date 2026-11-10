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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grooxtyper.app.model.FontFinder
import kotlinx.coroutines.launch

private val FinderAccent = Color(0xFFFF5722)
private val FinderPanel = Color(0xFF1C1C1E)

/**
 * Dialog Font Finder.
 *
 * Dua mode:
 *  - **Cari** : ketik nama font, hasil diurutkan dari yang paling mirip.
 *  - **Mirip** : cari font yang mirip font yang sedang dipakai (paling
 *    berguna: user sudah suka gaya satu font, mau yang lain sepadan).
 *
 * Tombol unduh mengunduh TTF dari katalog Google Fonts (OFL) lalu
 * memasangnya lewat [FontRegistry] supaya langsung muncul di daftar font.
 */
@Composable
fun FontFinderDialog(
    finder: FontFinder,
    currentFontName: String,
    onDismiss: () -> Unit,
    onDownloaded: (bytes: ByteArray, fileName: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var useSimilar by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<FontFinder.Result>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var busyFamily by remember { mutableStateOf<String?>(null) }
    var installed by remember { mutableStateOf<String?>(null) }

    fun run(block: suspend () -> List<FontFinder.Result>) {
        if (loading) return
        loading = true
        errorMsg = null
        scope.launch {
            try {
                results = block()
                if (results.isEmpty()) errorMsg = "Tak ada font yang cocok. Coba kata kunci lain."
            } catch (e: Exception) {
                errorMsg = "Gagal mencari: ${e.message ?: "error"}"
            } finally {
                loading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, contentDescription = null, tint = FinderAccent, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Font Finder", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Nama font (mis. Bangers, comic)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { useSimilar = false; run { finder.search(query) } },
                        enabled = !loading && query.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = FinderAccent),
                        modifier = Modifier.weight(1f)
                    ) { Text("Cari", fontSize = 12.sp) }
                    Button(
                        onClick = {
                            useSimilar = true
                            run { finder.similarTo(if (query.isBlank()) currentFontName else query) }
                        },
                        enabled = !loading && (currentFontName.isNotBlank() || query.isNotBlank()),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3A3C)),
                        modifier = Modifier.weight(1f)
                    ) { Text("Mirip", fontSize = 12.sp) }
                }
                if (currentFontName.isNotBlank()) {
                    Text(
                        "Font aktif: $currentFontName",
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                }
                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = FinderAccent,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mengambil katalog font...", color = Color.Gray, fontSize = 11.sp)
                    }
                }
                errorMsg?.let {
                    Text(it, color = Color(0xFFFF6B6B), fontSize = 11.sp)
                }
                installed?.let {
                    Text(
                        "Font \"$it\" terpasang. Pilih di daftar font.",
                        color = Color(0xFF4CAF50),
                        fontSize = 11.sp
                    )
                }
                if (results.isNotEmpty()) {
                    Text(
                        "${results.size} font ditemukan" +
                            (if (useSimilar) " (mode mirip)" else ""),
                        color = Color.Gray,
                        fontSize = 10.sp
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(results) { r ->
                            FontResultRow(
                                result = r,
                                busy = busyFamily == r.family.family,
                                onDownload = {
                                    busyFamily = r.family.family
                                    scope.launch {
                                        val bytes = finder.download(r.family)
                                        if (bytes == null) {
                                            errorMsg = "Gagal mengunduh ${r.family.family}"
                                        } else {
                                            val fileName = r.family.fileName.ifBlank {
                                                r.family.family.replace(' ', '_') + "-Regular.ttf"
                                            }
                                            onDownloaded(bytes, fileName)
                                            installed = r.family.family
                                        }
                                        busyFamily = null
                                    }
                                }
                            )
                        }
                    }
                }
                Text(
                    "Katalog: Google Fonts (lisensi OFL). Font terpasang tersimpan di perangkat.",
                    color = Color.Gray,
                    fontSize = 9.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup", color = Color.Gray) }
        },
        containerColor = FinderPanel
    )
}

@Composable
private fun FontResultRow(
    result: FontFinder.Result,
    busy: Boolean,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF2C2C2E))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                result.family.displayName.ifBlank { result.family.family },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                buildString {
                    if (result.family.category.isNotBlank()) {
                        append(result.family.category)
                        append(" - ")
                    }
                    append("skor ${result.score}")
                },
                color = Color.Gray,
                fontSize = 10.sp
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(if (busy) Color(0xFF38383A) else FinderAccent)
                .clickable(enabled = !busy) { onDownload() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.Download, contentDescription = "Unduh", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}
