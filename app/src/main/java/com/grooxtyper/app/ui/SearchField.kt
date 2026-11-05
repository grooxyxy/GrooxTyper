package com.grooxtyper.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SearchAccent = Color(0xFFFF5722)

/**
 * Pencocokan teks untuk kolom cari: tidak peduli huruf besar/kecil, spasi,
 * strip, atau titik. "patrickhand" tetap menemukan "PatrickHand-Regular",
 * dan beberapa kata ("comic bold") harus cocok semuanya.
 */
fun matchesSearchQuery(text: String, query: String): Boolean {
    val q = query.trim()
    if (q.isEmpty()) return true
    val haystack = text.lowercase()
    val normalized = haystack.filter { it.isLetterOrDigit() }
    val tokens = q.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return true
    return tokens.all { token ->
        val t = token.filter { it.isLetterOrDigit() }
        if (t.isEmpty()) haystack.contains(token) else normalized.contains(t)
    }
}

/** Kolom cari seragam untuk panel font/style. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(placeholder, color = Color.Gray, fontSize = 12.sp) },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Hapus pencarian",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = Color(0xFF101012),
            unfocusedContainerColor = Color(0xFF101012),
            cursorColor = SearchAccent,
            focusedBorderColor = SearchAccent,
            unfocusedBorderColor = Color(0xFF38383A),
            focusedLabelColor = Color.Gray,
            unfocusedLabelColor = Color.Gray
        )
    )
}
