package com.grooxtyper.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val FamilyAccent = Color(0xFFFF5722)
private val FamilyPanel = Color(0xFF1C1C1E)

/**
 * Tool Rail ala TypeR/Photoshop: tool dikelompokkan jadi "famili".
 *
 *Alih-alih 5 ikon seleksi terpisah (kotak/elips/lasso/poligon/wand) di
 * toolbar, ada SATU tombol yang membuka popup anggota keluarga. Toolbar
 * jadi lega dan tool aktif selalu kelihatan lewat ikon tombolnya.
 */
@Composable
fun SelectionFamilyButton(
    activeTool: ActiveTool,
    current: ActiveTool,
    onPick: (ActiveTool) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        FamilyButton(
            icon = selectionToolIcon(current),
            label = selectionToolShortLabel(current),
            active = isSelectionTool(activeTool),
            onClick = { menuOpen = true }
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Marquee Kotak") },
                leadingIcon = { Icon(Icons.Default.OpenInFull, null) },
                onClick = {
                    menuOpen = false
                    onPick(ActiveTool.SELECT_BOX)
                }
            )
            DropdownMenuItem(
                text = { Text("Marquee Elips") },
                leadingIcon = { Icon(Icons.Default.Circle, null) },
                onClick = {
                    menuOpen = false
                    onPick(ActiveTool.SELECT_ELLIPSE)
                }
            )
            DropdownMenuItem(
                text = { Text("Lasso Bebas") },
                leadingIcon = { Icon(Icons.Default.Gesture, null) },
                onClick = {
                    menuOpen = false
                    onPick(ActiveTool.LASSO)
                }
            )
            DropdownMenuItem(
                text = { Text("Lasso Poligon") },
                leadingIcon = { Icon(Icons.Default.Polyline, null) },
                onClick = {
                    menuOpen = false
                    onPick(ActiveTool.POLYGON_LASSO)
                }
            )
            DropdownMenuItem(
                text = { Text("Magic Wand") },
                leadingIcon = { Icon(Icons.Default.AutoFixHigh, null) },
                onClick = {
                    menuOpen = false
                    onPick(ActiveTool.SELECT_WAND)
                }
            )
        }
    }
}

/**
 * Keluarga retouch: hapus objek (content-aware), heal on-device, dan
 * clone stamp dalam satu tombol.
 */
@Composable
fun RetouchFamilyButton(
    activeTool: ActiveTool,
    current: RetouchKind,
    onPick: (RetouchKind) -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        FamilyButton(
            icon = retouchIcon(current),
            label = retouchShortLabel(current),
            active = activeTool == ActiveTool.INPAINT ||
                activeTool == ActiveTool.CLONE_STAMP,
            onClick = { menuOpen = true }
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Spot Healing (isi sekitar)") },
                leadingIcon = { Icon(Icons.Default.Delete, null) },
                onClick = {
                    menuOpen = false
                    onPick(RetouchKind.OBJECT_ERASER)
                }
            )
            DropdownMenuItem(
                text = { Text("Heal MiGAN") },
                leadingIcon = { Icon(Icons.Default.Healing, null) },
                onClick = {
                    menuOpen = false
                    onPick(RetouchKind.HEAL_MIGAN)
                }
            )
            DropdownMenuItem(
                text = { Text("Clone Stamp") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                onClick = {
                    menuOpen = false
                    onPick(RetouchKind.CLONE_STAMP)
                }
            )
            DropdownMenuItem(
                text = { Text("Magic Eraser") },
                leadingIcon = { Icon(Icons.Default.AutoFixHigh, null) },
                onClick = {
                    menuOpen = false
                    onPick(RetouchKind.MAGIC_ERASER)
                }
            )
            DropdownMenuItem(
                text = { Text("Bubble Eraser") },
                leadingIcon = { Icon(Icons.Default.BubbleChart, null) },
                onClick = {
                    menuOpen = false
                    onPick(RetouchKind.BUBBLE_ERASER)
                }
            )
        }
    }
}

/** Tombol rail satu keluarga (ikon + label kecil). */
@Composable
private fun FamilyButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) FamilyAccent else FamilyPanel)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

/** Jenis tool retouch dalam keluarga retouch. */
enum class RetouchKind {
    /** Hapus objek tanpa model (PatchMatch content-aware fill). */
    OBJECT_ERASER,
    /** Heal MiGAN on-device (lokal, tanpa cloud). */
    HEAL_MIGAN,
    /** Clone stamp: salin piksel dari sumber yang ditetapkan. */
    CLONE_STAMP,
    /** Magic eraser: ketuk = langsung hapus area warna mirip. */
    MAGIC_ERASER,
    /** Bubble eraser: ketuk dalam gelembung = teks hilang, warna bubble. */
    BUBBLE_ERASER
}

/** True bila tool ini anggota keluarga seleksi. */
fun isSelectionTool(tool: ActiveTool): Boolean = tool == ActiveTool.SELECT_BOX ||
    tool == ActiveTool.SELECT_ELLIPSE ||
    tool == ActiveTool.LASSO ||
    tool == ActiveTool.POLYGON_LASSO ||
    tool == ActiveTool.SELECT_WAND

/** Ikon untuk tool seleksi. */
fun selectionToolIcon(tool: ActiveTool): ImageVector = when (tool) {
    ActiveTool.SELECT_BOX -> Icons.Default.OpenInFull
    ActiveTool.SELECT_ELLIPSE -> Icons.Default.Circle
    ActiveTool.LASSO -> Icons.Default.Gesture
    ActiveTool.POLYGON_LASSO -> Icons.Default.Polyline
    ActiveTool.SELECT_WAND -> Icons.Default.AutoFixHigh
    else -> Icons.Default.Gesture
}

/** Label pendek untuk rail. */
fun selectionToolShortLabel(tool: ActiveTool): String = when (tool) {
    ActiveTool.SELECT_BOX -> "Kotak"
    ActiveTool.SELECT_ELLIPSE -> "Elips"
    ActiveTool.LASSO -> "Lasso"
    ActiveTool.POLYGON_LASSO -> "Poligon"
    ActiveTool.SELECT_WAND -> "Wand"
    else -> "Seleksi"
}

/** Ikon untuk anggota keluarga retouch. */
fun retouchIcon(kind: RetouchKind): ImageVector = when (kind) {
    RetouchKind.OBJECT_ERASER -> Icons.Default.Delete
    RetouchKind.HEAL_MIGAN -> Icons.Default.Healing
    RetouchKind.CLONE_STAMP -> Icons.Default.ContentCopy
    RetouchKind.MAGIC_ERASER -> Icons.Default.AutoFixHigh
    RetouchKind.BUBBLE_ERASER -> Icons.Default.BubbleChart
}

/** Label pendek untuk anggota keluarga retouch. */
fun retouchShortLabel(kind: RetouchKind): String = when (kind) {
    RetouchKind.OBJECT_ERASER -> "Hapus"
    RetouchKind.HEAL_MIGAN -> "Heal"
    RetouchKind.CLONE_STAMP -> "Clone"
    RetouchKind.MAGIC_ERASER -> "Eraser"
    RetouchKind.BUBBLE_ERASER -> "Bubble"
}
