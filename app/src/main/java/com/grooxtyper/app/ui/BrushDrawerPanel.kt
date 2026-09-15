package com.grooxtyper.app.ui

import androidx.activity.compose.BackHandler
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grooxtyper.app.model.BrushEngine
import com.grooxtyper.app.model.BrushType
import com.grooxtyper.app.model.DrawingLayer
import com.grooxtyper.app.model.LayerBlendMode
import com.grooxtyper.app.model.LayerItem
import com.grooxtyper.app.model.LayerManager
import com.grooxtyper.app.model.LayerProps
import com.grooxtyper.app.model.TextLayer
import com.grooxtyper.app.model.TextRenderer
import com.grooxtyper.app.model.UndoRedoManager

private val Accent = Color(0xFFFF5722)
private val PanelBg = Color(0xFF1C1C1E)
private val PanelBgLight = Color(0xFF2C2C2E)
private val Divider = Color(0xFF38383A)

@Composable
fun BrushPanel(
    brushEngine: BrushEngine,
    onClose: () -> Unit
) {
    // Tombol back sistem harus menutup panel (sebelumnya onClose tidak pernah dipakai).
    BackHandler(onBack = onClose)

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Brush", "Stabilizer", "Fade")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(PanelBg)
            .border(1.dp, Divider, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            // Panel solid: tap di area kosong tidak tembus ke kanvas.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Handle bar (tap untuk tutup) + tombol close eksplisit.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.Gray)
                        .clickable { onClose() }
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup panel brush", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            // Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(PanelBgLight)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                tabs.forEachIndexed { index, title ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedTab == index) Accent else Color.Transparent)
                            .clickable { selectedTab = index }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            title,
                            color = if (selectedTab == index) Color.White else Color.Gray,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                when (selectedTab) {
                    0 -> BrushListTab(brushEngine)
                    1 -> StabilizerTab(brushEngine)
                    2 -> FadeTab(brushEngine)
                }
            }
        }
    }
}

@Composable
private fun BrushListTab(brushEngine: BrushEngine) {
    Column {
        // Category chips
        val categories = BrushType.values().map { it.category }.distinct()
        var selectedCategory by remember { mutableStateOf("Pen") }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { cat ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selectedCategory == cat) Accent else PanelBgLight)
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        cat,
                        color = if (selectedCategory == cat) Color.White else Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = if (selectedCategory == cat) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // Brush grid for selected category
        val brushes = BrushType.values().filter { it.category == selectedCategory }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            brushes.forEach { brush ->
                val isSelected = brushEngine.brushType == brush
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) Color(0xFF38383A) else PanelBgLight)
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) Accent else Color.Transparent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { brushEngine.brushType = brush }
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Accent else Color.DarkGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                brush.displayName.first().toString(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            brush.displayName,
                            color = if (isSelected) Color.White else Color.LightGray,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Size slider
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Size", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(44.dp))
            Slider(
                value = brushEngine.size,
                onValueChange = { brushEngine.size = it },
                valueRange = 1f..120f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
            )
            Text(
                "${brushEngine.size.toInt()}px",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(40.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }

        // Opacity slider
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Alpha", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(44.dp))
            Slider(
                value = brushEngine.opacity,
                onValueChange = { brushEngine.opacity = it },
                valueRange = 0.05f..1.0f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
            )
            Text(
                "${(brushEngine.opacity * 100).toInt()}%",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(40.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
    }
}

@Composable
private fun StabilizerTab(brushEngine: BrushEngine) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Stabilizer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Smooth your strokes", color = Color.Gray, fontSize = 12.sp)
            }
            Switch(
                checked = brushEngine.stabilizer.isEnabled,
                onCheckedChange = { brushEngine.stabilizer.isEnabled = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Accent, checkedTrackColor = Accent.copy(alpha = 0.5f))
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Strength: ${(brushEngine.stabilizer.strength * 100).toInt()}%", color = Color.LightGray, fontSize = 12.sp)
        Slider(
            value = brushEngine.stabilizer.strength,
            onValueChange = { brushEngine.stabilizer.strength = it },
            valueRange = 0f..0.95f,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Higher values create smoother lines but add slight lag.",
            color = Color.Gray,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun FadeTab(brushEngine: BrushEngine) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Force Fade", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("Taper stroke start and end", color = Color.Gray, fontSize = 12.sp)
            }
            Switch(
                checked = brushEngine.forceFade.isEnabled,
                onCheckedChange = { brushEngine.forceFade.isEnabled = it },
                colors = SwitchDefaults.colors(checkedThumbColor = Accent, checkedTrackColor = Accent.copy(alpha = 0.5f))
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Start Taper: ${(brushEngine.forceFade.startFade * 100).toInt()}%", color = Color.LightGray, fontSize = 12.sp)
        Slider(
            value = brushEngine.forceFade.startFade,
            onValueChange = { brushEngine.forceFade.startFade = it },
            valueRange = 0.05f..0.8f,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
        )

        Text("End Taper: ${(brushEngine.forceFade.endFade * 100).toInt()}%", color = Color.LightGray, fontSize = 12.sp)
        Slider(
            value = brushEngine.forceFade.endFade,
            onValueChange = { brushEngine.forceFade.endFade = it },
            valueRange = 0.05f..0.8f,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
        )
    }
}

@Composable
fun LayerPanel(
    layerManager: LayerManager,
    undoManager: UndoRedoManager,
    refreshTick: Int,
    onClose: () -> Unit,
    onRefresh: () -> Unit
) {
    BackHandler(onBack = onClose)

    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<LayerItem?>(null) }
    var newName by remember { mutableStateOf("") }
    var blendMenuFor by remember { mutableStateOf<String?>(null) }

    // Ubah properti display dengan satu langkah undo.
    fun mutateProps(layer: LayerItem, change: () -> Unit) {
        val before = LayerProps.of(layer)
        change()
        undoManager.pushLayerProps(layer.id, before, LayerProps.of(layer))
        onRefresh()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(PanelBg)
            .border(1.dp, Divider, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            // Panel solid: tap di area kosong tidak tembus ke kanvas.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.Gray)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Layers", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row {
                    Button(
                        onClick = {
                            val created = layerManager.addLayer()
                            undoManager.pushLayerAdd(created.id)
                            onRefresh()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New", color = Color.White, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val id = layerManager.activeLayerId
                            if (id.isNotEmpty()) {
                                val idx = layerManager.indexOfLayer(id)
                                layerManager.findLayerById(id)?.let { target ->
                                    undoManager.pushLayerRemove(target, idx)
                                    layerManager.removeLayerById(id)
                                }
                                onRefresh()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(layerManager.layers.size) { index ->
                    val layer = layerManager.layers[index]
                    val isSelected = layer.id == layerManager.activeLayerId
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                layerManager.activeLayerId = layer.id
                                if (layer is TextLayer) {
                                    // handled externally
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFF38383A) else PanelBgLight
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Preview isi layer.
                            LayerThumbnail(layer = layer, refreshTick = refreshTick)
                            Spacer(modifier = Modifier.width(10.dp))
                            if (layer is TextLayer) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Accent)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("T", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                layer.name,
                                color = Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )

                            // Reorder: naik/turun (index 0 = paling atas).
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        undoManager.pushLayerMove(layer.id, index, index - 1)
                                        layerManager.moveLayerTo(layer.id, index - 1)
                                        onRefresh()
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Pindah ke atas", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    if (index < layerManager.layers.size - 1) {
                                        undoManager.pushLayerMove(layer.id, index, index + 1)
                                        layerManager.moveLayerTo(layer.id, index + 1)
                                        onRefresh()
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Pindah ke bawah", tint = Color.White, modifier = Modifier.size(18.dp))
                            }

                            IconButton(
                                onClick = {
                                    renameTarget = layer
                                    newName = layer.name
                                    showRenameDialog = true
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }

                            IconButton(
                                onClick = {
                                    mutateProps(layer) { layer.isVisible = !layer.isVisible }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Visibility",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Opacity + blend mode (berlaku untuk semua jenis layer).
                        var opacityBaseline by remember(layer.id) { mutableStateOf<Float?>(null) }
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${(layer.opacity * 100).toInt()}%",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                modifier = Modifier.width(38.dp)
                            )
                            Slider(
                                value = layer.opacity,
                                onValueChange = {
                                    if (opacityBaseline == null) opacityBaseline = layer.opacity
                                    layer.opacity = it
                                    onRefresh()
                                },
                                onValueChangeFinished = {
                                    opacityBaseline?.let { before ->
                                        undoManager.pushLayerProps(
                                            layer.id,
                                            LayerProps.of(layer).copy(opacity = before),
                                            LayerProps.of(layer)
                                        )
                                        onRefresh()
                                    }
                                    opacityBaseline = null
                                },
                                valueRange = 0.1f..1f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PanelBgLight)
                                    .clickable { blendMenuFor = layer.id }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(layer.blendMode.displayName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            DropdownMenu(
                                expanded = blendMenuFor == layer.id,
                                onDismissRequest = { blendMenuFor = null }
                            ) {
                                LayerBlendMode.values().forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.displayName) },
                                        onClick = {
                                            mutateProps(layer) { layer.blendMode = mode }
                                            blendMenuFor = null
                                        }
                                    )
                                }
                            }
                        }

                        if (layer is DrawingLayer) {
                            Row(
                                modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        mutateProps(layer) { layer.isAlphaLocked = !layer.isAlphaLocked }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        if (layer.isAlphaLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = "Alpha Lock",
                                        tint = if (layer.isAlphaLocked) Accent else Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = {
                                        mutateProps(layer) { layer.isClippingMask = !layer.isClippingMask }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.FlipToBack,
                                        contentDescription = "Clip",
                                        tint = if (layer.isClippingMask) Accent else Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                IconButton(
                                    onClick = {
                                        undoManager.saveSnapshot(layer)
                                        layer.flipHorizontal()
                                        onRefresh()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Flip, contentDescription = "Flip H", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PanelBgLight),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Close", color = Color.White)
            }
        }
    }

    if (showRenameDialog && renameTarget != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Layer", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Layer Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        renameTarget?.let { target ->
                            mutateProps(target) { target.name = newName }
                        }
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) { Text("Save", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel", color = Color.Gray) }
            },
            containerColor = PanelBgLight
        )
    }
}

/** Preview kecil isi layer (gambar diskala / contoh render teks). */
@Composable
private fun LayerThumbnail(layer: LayerItem, refreshTick: Int) {
    val bmp: Bitmap? = remember(layer.id, refreshTick) {
        try {
            when (layer) {
                is DrawingLayer -> {
                    val src = layer.getBitmap()
                    if (src.width <= 0 || src.height <= 0) {
                        null
                    } else {
                        val s = 112f / maxOf(src.width, src.height).toFloat()
                        Bitmap.createScaledBitmap(
                            src,
                            maxOf(1, (src.width * s).toInt()),
                            maxOf(1, (src.height * s).toInt()),
                            true
                        )
                    }
                }
                is TextLayer -> {
                    val sample = layer.box.text.substringBefore("\n").take(16).ifBlank { "T" }
                    TextRenderer.renderSampleBox(layer.box, sample, 168, 96, forceWhiteText = true)
                }
                else -> null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Preview ${layer.name}",
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF101012))
                .border(1.dp, Divider, RoundedCornerShape(8.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF101012))
                .border(1.dp, Divider, RoundedCornerShape(8.dp))
        )
    }
}
