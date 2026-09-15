package com.grooxtyper.app.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grooxtyper.app.R
import com.grooxtyper.app.model.ImageImport
import com.grooxtyper.app.model.ProjectManager
import com.grooxtyper.app.model.SavedProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GalleryScreen(
    onOpenCanvas: (id: String, width: Int, height: Int, initialBitmap: Bitmap?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val projectManager = remember { ProjectManager(context) }
    val savedProjects = remember { mutableStateListOf<SavedProject>() }
    var isImporting by remember { mutableStateOf(false) }
    var showDonatePage by remember { mutableStateOf(false) }

    BackHandler(enabled = showDonatePage) {
        showDonatePage = false
    }

    fun refreshProjects() {
        savedProjects.clear()
        savedProjects.addAll(projectManager.loadProjects())
    }

    LaunchedEffect(Unit) {
        refreshProjects()
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var inputWidth by remember { mutableStateOf("1280") }
    var inputHeight by remember { mutableStateOf("1280") }

    // Import Image directly from Homepage (full-res tanpa resize hingga 720x16000).
    // Lanjaya: buka kanvas SEGERA setelah decode, simpan PNG di background
    // (kompres PNG 11,5MP bisa 3-8 detik — jangan blokir navigasi).
    val homepageImagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                isImporting = true
                try {
                    val decoded = withContext(Dispatchers.IO) {
                        ImageImport.decodeContentUri(context.contentResolver, it)
                    } ?: return@launch
                    // TANPA resize: kanvas = ukuran asli (didukung 720x16000).
                    // Downscale hanya bila melebihi budget absolut.
                    val (projW, projH) = ImageImport.fitImportDimensions(
                        decoded.width, decoded.height
                    )
                    val fitted = if (projW == decoded.width && projH == decoded.height) {
                        decoded
                    } else {
                        ImageImport.scaleTo(decoded, projW, projH)
                    }
                    val projId = "${System.currentTimeMillis()}"
                    val startedAt = System.currentTimeMillis()
                    // Buka dulu → terasa instan. Simpan menyusul di background.
                    onOpenCanvas(projId, projW, projH, fitted)
                    scope.launch(Dispatchers.IO) {
                        // Jangan timpa save editor yang lebih baru (stale-write):
                        // bila editor sudah autosave duluan, lewati file+meta basi.
                        val existing = runCatching { projectManager.getProject(projId) }.getOrNull()
                        if (existing == null || existing.lastModified < startedAt) {
                            runCatching {
                                projectManager.saveProject(projId, "Imported Artwork", projW, projH, fitted)
                            }
                            withContext(Dispatchers.Main) {
                                runCatching { refreshProjects() }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isImporting = false
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(Color(0xFF1F1F1F))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Brush,
                    contentDescription = "Logo",
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "GrooxTyper",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = { homepageImagePicker.launch("image/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C))
                ) {
                    Icon(Icons.Default.Image, contentDescription = "Import Image", tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Picture", color = Color.White, fontSize = 12.sp)
                }
            }

            // Navigation Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF262626))
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFFFF9800))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("My Gallery History", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            if (savedProjects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No saved projects yet. Tap '+' to create a canvas!", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                // Project Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(savedProjects, key = { it.id }) { proj ->
                        // Thumbnail di-decode sampled di IO agar daftar riwayat tetap ringan.
                        // Prioritaskan preview berteks (basis edit tidak lagi memuat teks bakar).
                        var thumbBmp by remember(proj.imagePath) { mutableStateOf<Bitmap?>(null) }
                        LaunchedEffect(proj.imagePath) {
                            thumbBmp = withContext(Dispatchers.IO) {
                                val preview = runCatching { projectManager.previewPathFor(proj.id) }.getOrNull()
                                ImageImport.decodeFileSampled(
                                    preview ?: proj.imagePath, ImageImport.MAX_THUMB_DIM
                                )
                            }
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Buka full-res TANPA resize (thumbnail hanya untuk grid).
                                    // 720x16000 dimuat penuh agar tidak pecah/buram.
                                    scope.launch {
                                        val full = withContext(Dispatchers.IO) {
                                            projectManager.loadProjectBitmap(proj.imagePath)
                                        } ?: thumbBmp
                                        onOpenCanvas(proj.id, proj.width, proj.height, full)
                                    }
                                },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .background(Color(0xFF383838), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val thumb = thumbBmp
                                    if (thumb != null) {
                                        Image(
                                            bitmap = thumb.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(Icons.Default.Brush, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(proj.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text("${proj.width} x ${proj.height} px", color = Color.LightGray, fontSize = 12.sp)
                                    }
                                    IconButton(
                                        onClick = {
                                            projectManager.deleteProject(proj.id)
                                            refreshProjects()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Footer: channel resmi, kredit developer, dan donasi.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1F1F1F))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Telegram",
                    tint = Color(0xFF29A9EB),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { runCatching { uriHandler.openUri("https://t.me/VasiliasPV") } }
                ) {
                    Text(
                        "Official Telegram Channel",
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        "t.me/VasiliasPV",
                        color = Color(0xFF29A9EB), fontSize = 11.sp
                    )
                    Text(
                        "Develop by @AnergiaPV",
                        color = Color.Gray, fontSize = 11.sp,
                        modifier = Modifier.clickable {
                            runCatching { uriHandler.openUri("https://t.me/AnergiaPV") }
                        }
                    )
                }
                Button(
                    onClick = { showDonatePage = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Donate", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // FAB to create new canvas
        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 96.dp),
            containerColor = Color(0xFFFF9800),
            contentColor = Color.Black
        ) {
            Icon(Icons.Default.Add, contentDescription = "New Canvas")
        }

        if (showCreateDialog) {
            AlertDialog(
                onDismissRequest = { showCreateDialog = false },
                title = { Text("Create New Canvas", color = Color.White) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = inputWidth,
                            onValueChange = { inputWidth = it },
                            label = { Text("Width (px)") }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = inputHeight,
                            onValueChange = { inputHeight = it },
                            label = { Text("Height (px)") }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            // Dukung kanvas jangkung 720x16000, clamp 1..16000.
                            val w = (inputWidth.toIntOrNull() ?: 1280).coerceIn(1, 16000)
                            val h = (inputHeight.toIntOrNull() ?: 1280).coerceIn(1, 16000)
                            val newId = "${System.currentTimeMillis()}"
                            val blankBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            projectManager.saveProject(newId, "New Project", w, h, blankBmp)
                            showCreateDialog = false
                            onOpenCanvas(newId, w, h, null)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                    ) {
                        Text("Create", color = Color.Black)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF2A2A2A)
            )
        }

        // Overlay saat mengimpor gambar (decode + simpan di background).
        if (isImporting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xAA000000)),
                contentAlignment = Alignment.Center
            ) {
                Text("Mengimpor gambar…", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Halaman donasi: QR QRIS + channel resmi.
        if (showDonatePage) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF141414))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .background(Color(0xFF1F1F1F))
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showDonatePage = false }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Donate",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Dukung pengembangan GrooxTyper",
                            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Scan QRIS di bawah untuk donasi",
                            color = Color.Gray, fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Image(
                            painter = painterResource(id = R.drawable.qris_donate),
                            contentDescription = "Kode QRIS donasi",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .padding(12.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = null,
                                tint = Color(0xFF29A9EB),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "t.me/VasiliasPV",
                                color = Color(0xFF29A9EB), fontSize = 13.sp,
                                modifier = Modifier.clickable {
                                    runCatching { uriHandler.openUri("https://t.me/VasiliasPV") }
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Develop by @AnergiaPV", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
