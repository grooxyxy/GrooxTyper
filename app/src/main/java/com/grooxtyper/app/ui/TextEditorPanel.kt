package com.grooxtyper.app.ui

import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grooxtyper.app.model.StrokePosition
import com.grooxtyper.app.model.TextAlignMode
import com.grooxtyper.app.model.TextBox
import com.grooxtyper.app.model.TextFillType
import com.grooxtyper.app.model.TextGradientSpec
import com.grooxtyper.app.model.TextGlowSpec
import com.grooxtyper.app.model.TextBevelSpec
import com.grooxtyper.app.model.TextRenderer
import com.grooxtyper.app.model.TextShadowSpec
import com.grooxtyper.app.model.TextStyleManager
import com.grooxtyper.app.model.TextStylePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private val Accent = Color(0xFFFF5722)
private val PanelBg = Color(0xFF1C1C1E)
private val PanelLight = Color(0xFF2C2C2E)

/**
 * Panel teks: semua perubahan LANGSUNG terlihat di kanvas (live).
 * Tab: Tulis | Warna | Efek | Style. Tidak ada tombol Apply.
 */
@Composable
fun TextEditorPanel(
    box: TextBox,
    fonts: List<Pair<String, Typeface>>,
    onImportFont: () -> Unit,
    // Dinaikkan saat geometri box berubah di kanvas (handle SCALE/lebar/perspektif)
    // agar slider ukuran ikut menampilkan ukuran AKTUAL (fontSize * scale).
    geomTick: Int = 0,
    onChange: () -> Unit,
    onPushTextHistory: (TextBox) -> Unit,
    onCheckPrefix: (TextBox) -> Boolean,
    onOpenMultiBubble: () -> Unit,
    onFlatten: () -> Unit,
    onDelete: () -> Unit,
    onOpenPerspectiveGrid: () -> Unit = {},
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val styleManager = remember { TextStyleManager(context) }
    var presets by remember { mutableStateOf(styleManager.list()) }
    // Dinaikkan saat preset diterapkan agar semua state cermin me-reset dari box.
    var styleVersion by remember { mutableIntStateOf(0) }
    // Dinaikkan saat font dipilih (Typeface bukan observable).
    var fontTick by remember { mutableIntStateOf(0) }

    var tab by remember { mutableIntStateOf(0) }
    var text by remember(box.id, styleVersion) { mutableStateOf(box.text) }
    // Ukuran AKTUAL di kanvas = fontSize * scale (handle SCALE tak menyentuh
    // fontSize). Tanpa ini panel selalu menampilkan angka lama (mis. 20)
    // walau teks sudah di-resize mengecil di kanvas.
    var fontSize by remember(box.id, styleVersion) {
        mutableFloatStateOf(box.fontSize * box.scale)
    }
    var colorVal by remember(box.id, styleVersion) { mutableIntStateOf(box.color) }
    var bold by remember(box.id, styleVersion) { mutableStateOf(box.bold) }
    var italic by remember(box.id, styleVersion) { mutableStateOf(box.italic) }
    var align by remember(box.id, styleVersion) { mutableStateOf(box.align) }

    var fillType by remember(box.id, styleVersion) { mutableStateOf(box.fillType) }
    var gradStart by remember(box.id, styleVersion) { mutableIntStateOf(box.gradient.colorStart) }
    var gradEnd by remember(box.id, styleVersion) { mutableIntStateOf(box.gradient.colorEnd) }
    var gradAngle by remember(box.id, styleVersion) { mutableFloatStateOf(box.gradient.angle) }

    var outlineW by remember(box.id, styleVersion) { mutableFloatStateOf(box.outlineWidth) }
    var outlineColor by remember(box.id, styleVersion) { mutableIntStateOf(box.outlineColor) }
    var strokeOpacity by remember(box.id, styleVersion) { mutableFloatStateOf(box.strokeOpacity) }
    var strokePos by remember(box.id, styleVersion) { mutableStateOf(box.strokePosition) }

    var shadowOn by remember(box.id, styleVersion) { mutableStateOf(box.shadow != null) }
    var shadowColor by remember(box.id, styleVersion) { mutableIntStateOf(box.shadow?.color ?: 0x80000000.toInt()) }
    var shadowOpacity by remember(box.id, styleVersion) { mutableFloatStateOf(box.shadow?.opacity ?: 0.75f) }
    var shadowDist by remember(box.id, styleVersion) {
        mutableFloatStateOf(box.shadow?.let { hypot(it.dx, it.dy) } ?: 6f)
    }
    var shadowAngle by remember(box.id, styleVersion) {
        mutableFloatStateOf(box.shadow?.let {
            ((Math.toDegrees(atan2(it.dy.toDouble(), it.dx.toDouble())) + 360) % 360).toFloat()
        } ?: 45f)
    }
    var shadowSize by remember(box.id, styleVersion) { mutableFloatStateOf(box.shadow?.blur ?: 8f) }
    var shadowSpread by remember(box.id, styleVersion) { mutableFloatStateOf(box.shadow?.spread ?: 0f) }

    var letterSp by remember(box.id, styleVersion) { mutableFloatStateOf(box.letterSpacing) }
    var wordSp by remember(box.id, styleVersion) { mutableFloatStateOf(box.wordSpacing) }
    var lineSp by remember(box.id, styleVersion) { mutableFloatStateOf(box.lineSpacing) }
    var scaleX by remember(box.id, styleVersion) { mutableFloatStateOf(box.textScaleX) }
    // Paragraph-text ala Photoshop/IbisPaint: null = point, non-null = wrap.
    var isParagraph by remember(box.id, styleVersion) { mutableStateOf(box.isParagraph()) }
    var boxW by remember(box.id, styleVersion) { mutableFloatStateOf(box.boxWidth ?: 600f) }

    var textOpacity by remember(box.id, styleVersion) { mutableFloatStateOf(box.textOpacity) }
    var perspX by remember(box.id, styleVersion) { mutableFloatStateOf(box.perspX) }
    var perspY by remember(box.id, styleVersion) { mutableFloatStateOf(box.perspY) }
    var uppercase by remember(box.id, styleVersion) { mutableStateOf(box.uppercase) }
    var underline by remember(box.id, styleVersion) { mutableStateOf(box.underline) }
    var strike by remember(box.id, styleVersion) { mutableStateOf(box.strikethrough) }

    var glowOn by remember(box.id, styleVersion) { mutableStateOf(box.glow != null) }
    var glowColor by remember(box.id, styleVersion) { mutableIntStateOf(box.glow?.color ?: 0xFFFFEE58.toInt()) }
    var glowOpacity by remember(box.id, styleVersion) { mutableFloatStateOf(box.glow?.opacity ?: 0.75f) }
    var glowSize by remember(box.id, styleVersion) { mutableFloatStateOf(box.glow?.blur ?: 14f) }
    var glowSpread by remember(box.id, styleVersion) { mutableFloatStateOf(box.glow?.spread ?: 0f) }

    var bevelOn by remember(box.id, styleVersion) { mutableStateOf(box.bevel != null) }
    var bevelSize by remember(box.id, styleVersion) { mutableFloatStateOf(box.bevel?.size ?: 2f) }
    var bevelOpacity by remember(box.id, styleVersion) { mutableFloatStateOf(box.bevel?.opacity ?: 0.8f) }

    // Dinaikkan saat geometri box berubah dari kanvas (handle SCALE/lebar/
    // perspektif): sinkronkan cermin ukuran agar slider menampilkan ukuran
    // AKTUAL (fontSize * scale), bukan angka lama yang tak pernah berubah.
    LaunchedEffect(geomTick, box.id, styleVersion) {
        val actual = box.fontSize * box.scale
        if (abs(actual - fontSize) > 0.01f) fontSize = actual
        val bw = box.boxWidth
        if (bw != null && abs(bw - boxW) > 0.01f) boxW = bw
        if (abs(box.perspX - perspX) > 0.01f) perspX = box.perspX
        if (abs(box.perspY - perspY) > 0.01f) perspY = box.perspY
    }

    var styleName by remember { mutableStateOf("") }
    var stylePrefix by remember { mutableStateOf("") }
    var showColor by remember { mutableStateOf(false) }
    var colorTarget by remember { mutableIntStateOf(0) } // 0 teks, 1 stroke, 2 shadow, 3 grad awal, 4 grad akhir, 5 glow

    // Baseline sesi edit: perubahan pertama mendorong satu langkah undo,
    // seluruh utak-atik sampai ganti teks menyatu dalam langkah itu.
    var sessionBaseline by remember(box.id) { mutableStateOf(box.copy()) }
    var sessionPushed by remember(box.id) { mutableStateOf(false) }

    fun push() {
        if (!sessionPushed && !box.contentEquals(sessionBaseline)) {
            sessionPushed = true
            onPushTextHistory(sessionBaseline)
        }
        onChange()
    }

    fun syncShadowOffset() {
        val s = box.shadow ?: return
        val rad = Math.toRadians(shadowAngle.toDouble())
        s.dx = (cos(rad) * shadowDist).toFloat()
        s.dy = (sin(rad) * shadowDist).toFloat()
    }

    fun applyPreset(preset: TextStylePreset) {
        val found = fonts.find { it.first == preset.fontName }
        preset.applyTo(box, found?.second)
        styleVersion++
        fontTick++
        push()
    }

    // Preview live (teks putih agar selalu terbaca di panel gelap).
    val previewText = text.substringBefore("\n").take(20).ifBlank { "Ag" }
    val previewBmp: Bitmap? = remember(
        previewText, fontSize, bold, italic, box.fontName, fontTick,
        fillType, gradStart, gradEnd, gradAngle,
        outlineW, outlineColor, strokeOpacity, strokePos,
        shadowOn, shadowColor, shadowOpacity, shadowDist, shadowAngle, shadowSize, shadowSpread,
        letterSp, wordSp, lineSp, scaleX, styleVersion,
        textOpacity, uppercase, underline, strike,
        glowOn, glowColor, glowOpacity, glowSize, glowSpread,
        bevelOn, bevelSize, bevelOpacity
    ) {
        try {
            val tmp = TextBox(
                text = previewText,
                fontSize = fontSize,
                color = colorVal,
                bold = bold,
                italic = italic,
                align = TextAlignMode.CENTER,
                outlineWidth = outlineW,
                outlineColor = outlineColor,
                strokeOpacity = strokeOpacity,
                strokePosition = strokePos,
                fillType = fillType,
                gradient = TextGradientSpec(gradStart, gradEnd, gradAngle),
                shadow = if (shadowOn) TextShadowSpec(
                    color = shadowColor, opacity = shadowOpacity, blur = shadowSize, spread = shadowSpread,
                    dx = (cos(Math.toRadians(shadowAngle.toDouble())) * shadowDist).toFloat(),
                    dy = (sin(Math.toRadians(shadowAngle.toDouble())) * shadowDist).toFloat()
                ) else null,
                letterSpacing = letterSp,
                wordSpacing = wordSp,
                lineSpacing = lineSp,
                textScaleX = scaleX,
                textOpacity = textOpacity,
                uppercase = uppercase,
                underline = underline,
                strikethrough = strike,
                glow = if (glowOn) TextGlowSpec(glowColor, glowSize, glowSpread, glowOpacity) else null,
                bevel = if (bevelOn) TextBevelSpec(bevelSize, bevelOpacity) else null,
                fontName = box.fontName,
                typeface = box.typeface
            )
            TextRenderer.renderSampleBox(tmp, previewText, 560, 130, forceWhiteText = true)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(480.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(PanelBg)
            .border(1.dp, Color(0xFF38383A), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            // Panel solid: tap di area kosong tidak tembus ke kanvas.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.Gray)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Teks", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Row {
                    IconButton(onClick = onFlatten) {
                        Icon(Icons.Default.Layers, contentDescription = "Flatten", tint = Color.White)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color(0xFFEF5350))
                    }
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Selesai", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Preview live strip
            if (previewBmp != null) {
                Image(
                    bitmap = previewBmp.asImageBitmap(),
                    contentDescription = "Preview teks",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(PanelLight),
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(PanelLight)
                    .padding(4.dp)
            ) {
                listOf("Tulis", "Warna", "Efek", "Style").forEachIndexed { i, t ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (tab == i) Accent else Color.Transparent)
                            .clickable { tab = i }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(t, color = if (tab == i) Color.White else Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (tab) {
                    0 -> WriteTab(
                        text = text,
                        onText = {
                            text = it; box.text = it.ifEmpty { " " }; push()
                            // Prefix ala TypeR: "[SFX]..." langsung pakai stylenya.
                            if (onCheckPrefix(box)) { styleVersion++; fontTick++ }
                        },
                        fontSize = fontSize,
                        onFontSize = { v ->
                            fontSize = v
                            // Slider mengatur ukuran AKTUAL di kanvas, jadi
                            // fontSize dasar = v / scale agar hasil render v.
                            box.fontSize = if (box.scale > 0.01f) v / box.scale else v
                            push()
                        },
                        bold = bold, italic = italic, align = align,
                        onBold = { bold = it; box.bold = it; push() },
                        onItalic = { italic = it; box.italic = it; push() },
                        onAlign = { align = it; box.align = it; push() },
                        isParagraph = isParagraph,
                        boxWidth = boxW,
                        onToggleParagraph = { para ->
                            isParagraph = para
                            if (para) {
                                box.enableParagraph(boxW)
                                boxW = box.boxWidth ?: boxW
                            } else {
                                box.disableParagraph()
                            }
                            push()
                        },
                        onBoxWidth = { w ->
                            boxW = w
                            box.boxWidth = w
                            push()
                        },
                        fonts = fonts,
                        boxFontName = box.fontName,
                        onPickFont = { name ->
                            val found = fonts.find { it.first == name }
                            if (found != null) {
                                box.typeface = found.second; box.fontName = name
                                fontTick++; push()
                            }
                        },
                        onImportFont = onImportFont,
                        onOpenMultiBubble = onOpenMultiBubble
                    )
                    1 -> ColorTab(
                        fillType = fillType,
                        onFillType = { fillType = it; box.fillType = it; push() },
                        colorVal = colorVal,
                        onPickTextColor = { colorTarget = 0; showColor = true },
                        gradStart = gradStart, gradEnd = gradEnd, gradAngle = gradAngle,
                        onPickGradStart = { colorTarget = 3; showColor = true },
                        onPickGradEnd = { colorTarget = 4; showColor = true },
                        onGradAngle = { gradAngle = it; box.gradient.angle = it; push() },
                        outlineW = outlineW,
                        onOutlineW = { outlineW = it; box.outlineWidth = it; push() },
                        outlineColor = outlineColor,
                        onPickStrokeColor = { colorTarget = 1; showColor = true },
                        strokeOpacity = strokeOpacity,
                        onStrokeOpacity = { strokeOpacity = it; box.strokeOpacity = it; push() },
                        strokePos = strokePos,
                        onStrokePos = { strokePos = it; box.strokePosition = it; push() }
                    )
                    2 -> EffectTab(
                        shadowOn = shadowOn,
                        onShadowOn = {
                            shadowOn = it
                            if (it) {
                                val s = TextShadowSpec(
                                    color = shadowColor, opacity = shadowOpacity,
                                    blur = shadowSize, spread = shadowSpread, dx = 4f, dy = 4f
                                )
                                box.shadow = s
                                shadowDist = hypot(s.dx, s.dy)
                                shadowAngle = ((Math.toDegrees(atan2(s.dy.toDouble(), s.dx.toDouble())) + 360) % 360).toFloat()
                            } else {
                                box.shadow = null
                            }
                            push()
                        },
                        shadowColor = shadowColor,
                        onPickShadowColor = { colorTarget = 2; showColor = true },
                        shadowOpacity = shadowOpacity,
                        onShadowOpacity = {
                            shadowOpacity = it; box.shadow?.opacity = it; push()
                        },
                        shadowDist = shadowDist,
                        onShadowDist = {
                            shadowDist = it; syncShadowOffset(); push()
                        },
                        shadowAngle = shadowAngle,
                        onShadowAngle = {
                            shadowAngle = it; syncShadowOffset(); push()
                        },
                        shadowSize = shadowSize,
                        onShadowSize = {
                            shadowSize = it; box.shadow?.blur = it; push()
                        },
                        shadowSpread = shadowSpread,
                        onShadowSpread = {
                            shadowSpread = it; box.shadow?.spread = it; push()
                        },
                        letterSp = letterSp,
                        onLetterSp = { letterSp = it; box.letterSpacing = it; push() },
                        wordSp = wordSp,
                        onWordSp = { wordSp = it; box.wordSpacing = it; push() },
                        lineSp = lineSp,
                        onLineSp = { lineSp = it; box.lineSpacing = it; push() },
                        scaleX = scaleX,
                        onScaleX = { scaleX = it; box.textScaleX = it; push() },
                        textOpacity = textOpacity,
                        onTextOpacity = { textOpacity = it; box.textOpacity = it; push() },
                        uppercase = uppercase,
                        onUppercase = { uppercase = it; box.uppercase = it; push() },
                        underline = underline,
                        onUnderline = { underline = it; box.underline = it; push() },
                        strike = strike,
                        onStrike = { strike = it; box.strikethrough = it; push() },
                        glowOn = glowOn,
                        onGlowOn = {
                            glowOn = it
                            box.glow = if (it) TextGlowSpec(glowColor, glowSize, glowSpread, glowOpacity) else null
                            push()
                        },
                        glowColor = glowColor,
                        onPickGlowColor = { colorTarget = 5; showColor = true },
                        glowOpacity = glowOpacity,
                        onGlowOpacity = { glowOpacity = it; box.glow?.opacity = it; push() },
                        glowSize = glowSize,
                        onGlowSize = { glowSize = it; box.glow?.blur = it; push() },
                        glowSpread = glowSpread,
                        onGlowSpread = { glowSpread = it; box.glow?.spread = it; push() },
                        bevelOn = bevelOn,
                        onBevelOn = {
                            bevelOn = it
                            box.bevel = if (it) TextBevelSpec(bevelSize, bevelOpacity) else null
                            push()
                        },
                        bevelSize = bevelSize,
                        onBevelSize = { bevelSize = it; box.bevel?.size = it; push() },
                        bevelOpacity = bevelOpacity,
                        onBevelOpacity = { bevelOpacity = it; box.bevel?.opacity = it; push() },
                        // FIX: tombol "Buka Grid Perspektif" berada di tab Efek.
                        // Sebelumnya parameter ini tidak dioper sehingga memakai
                        // nilai default {} → tombol tampak "tidak berfungsi".
                        onOpenPerspectiveGrid = onOpenPerspectiveGrid
                    )
                    3 -> StyleTab(
                        styleName = styleName,
                        onStyleName = { styleName = it },
                        stylePrefix = stylePrefix,
                        onStylePrefix = { stylePrefix = it },
                        onOpenPerspectiveGrid = onOpenPerspectiveGrid,
                        onSave = {
                            val preset = TextStylePreset.fromBox(
                                box, styleName.ifBlank { "Style ${presets.size + 1}" }, stylePrefix
                            )
                            presets = styleManager.save(preset)
                            styleName = ""
                            stylePrefix = ""
                        },
                        presets = presets,
                        fonts = fonts,
                        onApply = { applyPreset(it) },
                        onDeletePreset = { presets = styleManager.delete(it.id) }
                    )
                }
            }
        }
    }

    if (showColor) {
        ColorPickerDialog(
            initialColor = when (colorTarget) {
                1 -> outlineColor
                2 -> shadowColor
                3 -> gradStart
                4 -> gradEnd
                5 -> glowColor
                else -> colorVal
            },
            onColorSelected = { c ->
                when (colorTarget) {
                    1 -> { outlineColor = c; box.outlineColor = c }
                    2 -> { shadowColor = c; box.shadow?.color = c }
                    3 -> { gradStart = c; box.gradient.colorStart = c }
                    4 -> { gradEnd = c; box.gradient.colorEnd = c }
                    5 -> { glowColor = c; box.glow?.color = c }
                    else -> { colorVal = c; box.color = c }
                }
                push()
            },
            onDismiss = { showColor = false }
        )
    }
}

@Composable
private fun WriteTab(
    text: String,
    onText: (String) -> Unit,
    fontSize: Float,
    onFontSize: (Float) -> Unit,
    bold: Boolean,
    italic: Boolean,
    align: TextAlignMode,
    onBold: (Boolean) -> Unit,
    onItalic: (Boolean) -> Unit,
    onAlign: (TextAlignMode) -> Unit,
    isParagraph: Boolean,
    boxWidth: Float,
    onToggleParagraph: (Boolean) -> Unit,
    onBoxWidth: (Float) -> Unit,
    fonts: List<Pair<String, Typeface>>,
    boxFontName: String,
    onPickFont: (String) -> Unit,
    onImportFont: () -> Unit,
    onOpenMultiBubble: () -> Unit
) {
    OutlinedTextField(
        value = text,
        onValueChange = onText,
        label = { Text("Isi teks") },
        minLines = 2,
        maxLines = 5,
        modifier = Modifier.fillMaxWidth(),
        // Kolom selalu terang-di-atas-gelap agar terbaca apa pun warna isi teks.
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = Color(0xFF101012),
            unfocusedContainerColor = Color(0xFF101012),
            cursorColor = Accent,
            focusedLabelColor = Color.Gray,
            unfocusedLabelColor = Color.Gray,
            focusedBorderColor = Accent,
            unfocusedBorderColor = Color(0xFF38383A)
        )
    )
    Text("Ukuran ${fontSize.toInt()} px", color = Color.Gray, fontSize = 12.sp)
    Slider(
        value = fontSize, onValueChange = onFontSize,
        // Min 1px (bukan 20) sesuai permintaan; maks 400 untuk hasil resize besar.
        valueRange = 1f..400f,
        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StyleChip("B", bold, Icons.Default.FormatBold) { onBold(it) }
        StyleChip("I", italic, Icons.Default.FormatItalic) { onItalic(it) }
        Spacer(modifier = Modifier.weight(1f))
        AlignButton(align == TextAlignMode.LEFT, Icons.Default.FormatAlignLeft) { onAlign(TextAlignMode.LEFT) }
        AlignButton(align == TextAlignMode.CENTER, Icons.Default.FormatAlignCenter) { onAlign(TextAlignMode.CENTER) }
        AlignButton(align == TextAlignMode.RIGHT, Icons.Default.FormatAlignRight) { onAlign(TextAlignMode.RIGHT) }
    }
    // Mode paragraph ala Photoshop/IbisPaint: sempitkan box agar teks berbaris.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Paragraph (wrap otomatis)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(
                if (isParagraph) "Sempitkan via kotak oranye di kanvas / slider" else "Point-text: satu baris memanjang",
                color = Color.Gray, fontSize = 11.sp
            )
        }
        Switch(
            checked = isParagraph,
            onCheckedChange = onToggleParagraph,
            colors = SwitchDefaults.colors(checkedThumbColor = Accent)
        )
    }
    if (isParagraph) {
        Text("Lebar box ${boxWidth.toInt()} px", color = Color.Gray, fontSize = 12.sp)
        Slider(
            value = boxWidth, onValueChange = onBoxWidth,
            valueRange = 60f..1600f,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
        )
        Text(
            "Tips: drag handle kotak oranye di sisi kiri/kanan frame teks untuk wrap langsung di kanvas.",
            color = Color.Gray, fontSize = 11.sp
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Font", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text("Import TTF/OTF", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onImportFont() })
    }
    // Daftar font: preview di-render async agar panel langsung muncul.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        fonts.forEach { (name, tf) ->
            val sel = boxFontName == name
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPickFont(name) },
                colors = CardDefaults.cardColors(containerColor = if (sel) Color(0xFF38383A) else PanelLight),
                shape = RoundedCornerShape(10.dp),
                border = if (sel) androidx.compose.foundation.BorderStroke(2.dp, Accent) else null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncSampleImage(
                        key = name,
                        modifier = Modifier.size(width = 72.dp, height = 30.dp),
                        contentDescription = "Preview $name",
                        placeholder = {
                            Box(
                                modifier = Modifier
                                    .size(width = 72.dp, height = 30.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(PanelLight)
                            )
                        },
                        render = {
                            val tmp = TextBox(
                                text = "Ag", fontSize = 64f,
                                color = android.graphics.Color.WHITE,
                                bold = false, italic = false,
                                shadow = null, fontName = name, typeface = tf
                            )
                            TextRenderer.renderSampleBox(tmp, "Ag", 200, 84, forceWhiteText = true)
                        }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        name, color = Color.White, fontSize = 13.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f)
                    )
                    if (sel) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
    Button(
        onClick = onOpenMultiBubble,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = PanelLight),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text("Isi banyak bubble (multi-bubble)", color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun ColorTab(
    fillType: TextFillType,
    onFillType: (TextFillType) -> Unit,
    colorVal: Int,
    onPickTextColor: () -> Unit,
    gradStart: Int,
    gradEnd: Int,
    gradAngle: Float,
    onPickGradStart: () -> Unit,
    onPickGradEnd: () -> Unit,
    onGradAngle: (Float) -> Unit,
    outlineW: Float,
    onOutlineW: (Float) -> Unit,
    outlineColor: Int,
    onPickStrokeColor: () -> Unit,
    strokeOpacity: Float,
    onStrokeOpacity: (Float) -> Unit,
    strokePos: StrokePosition,
    onStrokePos: (StrokePosition) -> Unit
) {
    Text("Isi teks", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FillChip("Solid", fillType == TextFillType.SOLID) { onFillType(TextFillType.SOLID) }
        FillChip("Gradasi", fillType == TextFillType.GRADIENT) { onFillType(TextFillType.GRADIENT) }
    }
    if (fillType == TextFillType.SOLID) {
        ColorRow("Warna teks (color wheel)", colorVal) { onPickTextColor() }
    } else {
        ColorRow("Gradasi awal (color wheel)", gradStart) { onPickGradStart() }
        ColorRow("Gradasi akhir (color wheel)", gradEnd) { onPickGradEnd() }
        Text("Sudut gradasi ${gradAngle.toInt()}°", color = Color.Gray, fontSize = 12.sp)
        Slider(
            value = gradAngle, onValueChange = onGradAngle,
            valueRange = 0f..360f,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
        )
        // Preview bilah gradasi
        val rad = Math.toRadians(gradAngle.toDouble())
        val dx = cos(rad).toFloat()
        val dy = sin(rad).toFloat()
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val half = hypot(size.width, size.height) / 2f
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color(gradStart), Color(gradEnd)),
                    start = Offset(cx - dx * half, cy - dy * half),
                    end = Offset(cx + dx * half, cy + dy * half)
                ),
                size = size
            )
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text("Stroke", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    ColorRow("Warna stroke (color wheel)", outlineColor) { onPickStrokeColor() }
    Text("Tebal stroke ${outlineW.toInt()} px", color = Color.Gray, fontSize = 12.sp)
    Slider(
        value = outlineW, onValueChange = onOutlineW,
        valueRange = 0f..40f,
        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
    )
    Text("Opacity stroke ${(strokeOpacity * 100).toInt()}%", color = Color.Gray, fontSize = 12.sp)
    Slider(
        value = strokeOpacity, onValueChange = onStrokeOpacity,
        valueRange = 0f..1f,
        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
    )
    Text("Posisi stroke", color = Color.Gray, fontSize = 12.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StrokePosChip("Luar", strokePos == StrokePosition.OUTSIDE) { onStrokePos(StrokePosition.OUTSIDE) }
        StrokePosChip("Tengah", strokePos == StrokePosition.CENTER) { onStrokePos(StrokePosition.CENTER) }
        StrokePosChip("Dalam", strokePos == StrokePosition.INSIDE) { onStrokePos(StrokePosition.INSIDE) }
    }
}

@Composable
private fun EffectTab(
    shadowOn: Boolean,
    onShadowOn: (Boolean) -> Unit,
    shadowColor: Int,
    onPickShadowColor: () -> Unit,
    shadowOpacity: Float,
    onShadowOpacity: (Float) -> Unit,
    shadowDist: Float,
    onShadowDist: (Float) -> Unit,
    shadowAngle: Float,
    onShadowAngle: (Float) -> Unit,
    shadowSize: Float,
    onShadowSize: (Float) -> Unit,
    shadowSpread: Float,
    onShadowSpread: (Float) -> Unit,
    letterSp: Float,
    onLetterSp: (Float) -> Unit,
    wordSp: Float,
    onWordSp: (Float) -> Unit,
    lineSp: Float,
    onLineSp: (Float) -> Unit,
    scaleX: Float,
    onScaleX: (Float) -> Unit,
    textOpacity: Float,
    onTextOpacity: (Float) -> Unit,
    uppercase: Boolean,
    onUppercase: (Boolean) -> Unit,
    underline: Boolean,
    onUnderline: (Boolean) -> Unit,
    strike: Boolean,
    onStrike: (Boolean) -> Unit,
    glowOn: Boolean,
    onGlowOn: (Boolean) -> Unit,
    glowColor: Int,
    onPickGlowColor: () -> Unit,
    glowOpacity: Float,
    onGlowOpacity: (Float) -> Unit,
    glowSize: Float,
    onGlowSize: (Float) -> Unit,
    glowSpread: Float,
    onGlowSpread: (Float) -> Unit,
    bevelOn: Boolean,
    onBevelOn: (Boolean) -> Unit,
    bevelSize: Float,
    onBevelSize: (Float) -> Unit,
    bevelOpacity: Float,
    onBevelOpacity: (Float) -> Unit,
    onOpenPerspectiveGrid: () -> Unit = {},
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Bayangan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("Gaya Photoshop", color = Color.Gray, fontSize = 11.sp)
        }
        Switch(
            checked = shadowOn,
            onCheckedChange = onShadowOn,
            colors = SwitchDefaults.colors(checkedThumbColor = Accent)
        )
    }
    if (shadowOn) {
        ColorRow("Warna bayangan (color wheel)", shadowColor) { onPickShadowColor() }
        Text("Opacity ${(shadowOpacity * 100).toInt()}%", color = Color.Gray, fontSize = 12.sp)
        Slider(
            value = shadowOpacity, onValueChange = onShadowOpacity,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
        )
        Text("Jarak ${shadowDist.toInt()} px", color = Color.Gray, fontSize = 12.sp)
        Slider(
            value = shadowDist, onValueChange = onShadowDist,
            valueRange = 0f..60f,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
        )
        Text("Sudut ${shadowAngle.toInt()}°", color = Color.Gray, fontSize = 12.sp)
        Slider(
            value = shadowAngle, onValueChange = onShadowAngle,
            valueRange = 0f..360f,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
        )
        Text("Ukuran/blur ${shadowSize.toInt()} px", color = Color.Gray, fontSize = 12.sp)
        Slider(
            value = shadowSize, onValueChange = onShadowSize,
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
        )
        Text("Spread ${shadowSpread.toInt()}%", color = Color.Gray, fontSize = 12.sp)
        Slider(
            value = shadowSpread, onValueChange = onShadowSpread,
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
        )
    }
    Text("Spasi huruf ${letterSp.toInt()} px", color = Color.Gray, fontSize = 12.sp)
    Slider(
        value = letterSp, onValueChange = onLetterSp,
        valueRange = -20f..40f,
        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
    )
    Text("Spasi kata ${wordSp.toInt()} px", color = Color.Gray, fontSize = 12.sp)
    Slider(
        value = wordSp, onValueChange = onWordSp,
        valueRange = -50f..100f,
        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
    )
    Text("Spasi baris ${lineSp.toInt()} px", color = Color.Gray, fontSize = 12.sp)
    Slider(
        value = lineSp, onValueChange = onLineSp,
        valueRange = -50f..80f,
        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
    )
    Text(
        "Sempitkan teks ${(scaleX * 100).toInt()}%",
        color = Color.Gray, fontSize = 12.sp
    )
    Slider(
        value = scaleX, onValueChange = onScaleX,
        valueRange = 0.5f..1f,
        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
    )
    Text("Opacity teks ${(textOpacity * 100).toInt()}%", color = Color.Gray, fontSize = 12.sp)
    Slider(
        value = textOpacity, onValueChange = onTextOpacity,
        valueRange = 0.1f..1f,
        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
    )
    Text("Perspektif", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    Text("Atur keystone lewat grid perspektif di canvas (seret 4 sudut).", color = Color.Gray, fontSize = 11.sp)
    Button(onClick = onOpenPerspectiveGrid, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("Buka Grid Perspektif", fontSize = 12.sp)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Kapital semua", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = uppercase,
            onCheckedChange = onUppercase,
            colors = SwitchDefaults.colors(checkedThumbColor = Accent)
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Garis bawah", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = underline,
            onCheckedChange = onUnderline,
            colors = SwitchDefaults.colors(checkedThumbColor = Accent)
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Coret tengah", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = strike,
            onCheckedChange = onStrike,
            colors = SwitchDefaults.colors(checkedThumbColor = Accent)
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Outer Glow", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("Cahaya di balik teks", color = Color.Gray, fontSize = 11.sp)
        }
        Switch(
            checked = glowOn,
            onCheckedChange = onGlowOn,
            colors = SwitchDefaults.colors(checkedThumbColor = Accent)
        )
    }
    if (glowOn) {
        ColorRow("Warna glow (color wheel)", glowColor) { onPickGlowColor() }
        Text("Opacity glow ${(glowOpacity * 100).toInt()}%", color = Color.Gray, fontSize = 12.sp)
        Slider(
            value = glowOpacity, onValueChange = onGlowOpacity,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
        )
        Text("Ukuran glow ${glowSize.toInt()} px", color = Color.Gray, fontSize = 12.sp)
        Slider(
            value = glowSize, onValueChange = onGlowSize,
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
        )
        Text("Spread glow ${glowSpread.toInt()}%", color = Color.Gray, fontSize = 12.sp)
        Slider(
            value = glowSpread, onValueChange = onGlowSpread,
            valueRange = 0f..100f,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Bevel / Emboss", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("Efek timbul 3D", color = Color.Gray, fontSize = 11.sp)
        }
        Switch(
            checked = bevelOn,
            onCheckedChange = onBevelOn,
            colors = SwitchDefaults.colors(checkedThumbColor = Accent)
        )
    }
    if (bevelOn) {
        Text("Ukuran bevel ${bevelSize.toInt()} px", color = Color.Gray, fontSize = 12.sp)
        Slider(
            value = bevelSize, onValueChange = onBevelSize,
            valueRange = 0.5f..12f,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
        )
        Text("Kekuatan ${(bevelOpacity * 100).toInt()}%", color = Color.Gray, fontSize = 12.sp)
        Slider(
            value = bevelOpacity, onValueChange = onBevelOpacity,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
        )
    }
}

@Composable
private fun StyleTab(
    onOpenPerspectiveGrid: () -> Unit = {},
    styleName: String,
    onStyleName: (String) -> Unit,
    stylePrefix: String,
    onStylePrefix: (String) -> Unit,
    onSave: () -> Unit,
    presets: List<TextStylePreset>,
    fonts: List<Pair<String, Typeface>>,
    onApply: (TextStylePreset) -> Unit,
    onDeletePreset: (TextStylePreset) -> Unit
) {
    Text("Simpan gaya saat ini", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = styleName,
            onValueChange = onStyleName,
            label = { Text("Nama style") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF101012),
                unfocusedContainerColor = Color(0xFF101012),
                cursorColor = Accent,
                focusedLabelColor = Color.Gray,
                unfocusedLabelColor = Color.Gray,
                focusedBorderColor = Accent,
                unfocusedBorderColor = Color(0xFF38383A)
            )
        )
        OutlinedTextField(
            value = stylePrefix,
            onValueChange = onStylePrefix,
            label = { Text("[SFX]") },
            singleLine = true,
            modifier = Modifier.weight(0.7f),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF101012),
                unfocusedContainerColor = Color(0xFF101012),
                cursorColor = Accent,
                focusedLabelColor = Color.Gray,
                unfocusedLabelColor = Color.Gray,
                focusedBorderColor = Accent,
                unfocusedBorderColor = Color(0xFF38383A)
            )
        )
        Button(
            onClick = onSave,
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Simpan", color = Color.White, fontSize = 12.sp)
        }
    }
    Text("Prefix otomatis: teks diawali [..] langsung pakai stylenya.", color = Color.Gray, fontSize = 11.sp)
    Text("Style tersimpan (${presets.size}) — ketuk untuk pakai", color = Color.Gray, fontSize = 12.sp)
    if (presets.isEmpty()) {
        Text(
            "Belum ada style. Atur font, warna, stroke, bayangan lalu tekan Simpan.",
            color = Color.Gray, fontSize = 12.sp
        )
    }
    presets.forEach { preset ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onApply(preset) },
            colors = CardDefaults.cardColors(containerColor = PanelLight),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                AsyncSampleImage(
                    key = preset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF101012)),
                    contentDescription = "Preview ${preset.name}",
                    placeholder = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF101012))
                        )
                    },
                    render = {
                        val tf = fonts.find { it.first == preset.fontName }?.second
                        val tmp = TextBox(
                            text = "Ag",
                            fontSize = preset.fontSize,
                            color = preset.color,
                            bold = preset.bold,
                            italic = preset.italic,
                            align = TextAlignMode.CENTER,
                            outlineWidth = preset.outlineWidth,
                            outlineColor = preset.outlineColor,
                            strokeOpacity = preset.strokeOpacity,
                            strokePosition = preset.strokePosition,
                            fillType = preset.fillType,
                            gradient = preset.gradient.copy(),
                            shadow = preset.shadow?.copy(),
                            letterSpacing = preset.letterSpacing,
                            wordSpacing = preset.wordSpacing,
                            lineSpacing = preset.lineSpacing,
                            textOpacity = preset.textOpacity,
                            uppercase = preset.uppercase,
                            underline = preset.underline,
                            strikethrough = preset.strikethrough,
                            glow = preset.glow?.copy(),
                            bevel = preset.bevel?.copy(),
                            fontName = preset.fontName,
                            typeface = tf ?: Typeface.DEFAULT_BOLD
                        )
                        TextRenderer.renderSampleBox(tmp, "Ag", 420, 120, forceWhiteText = true)
                    }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(preset.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "${preset.fontName} • ${preset.fontSize.toInt()}px" +
                                if (preset.prefix.isNotBlank()) " • ${preset.prefix}" else "",
                            color = Color.Gray, fontSize = 11.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(preset.color))
                            .border(1.dp, Color.White, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { onDeletePreset(preset) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus style", tint = Color(0xFFEF5350), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/**
 * Preview bitmap yang di-render di background agar panel teks muncul instan
 * (render puluhan preview font/style yang efeknya berat tak lagi memblokir
 * frame pertama). Key stabil → render sekali per key.
 */
@Composable
private fun AsyncSampleImage(
    key: Any,
    modifier: Modifier,
    contentDescription: String?,
    placeholder: @Composable () -> Unit,
    render: () -> Bitmap?
) {
    var bmp by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(key) {
        val rendered = withContext(Dispatchers.Default) {
            runCatching { render() }.getOrNull()
        }
        bmp?.recycle()
        bmp = rendered
    }
    DisposableEffect(Unit) {
        onDispose { runCatching { bmp?.recycle() } }
    }
    val b = bmp
    if (b != null) {
        Image(
            bitmap = b.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
    } else {
        placeholder()
    }
}

@Composable
private fun FillChip(label: String, active: Boolean, onClick: () -> Unit) {    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Accent else PanelLight)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun StrokePosChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Accent else PanelLight)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun StyleChip(
    label: String,
    active: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onToggle: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Accent else PanelLight)
            .clickable { onToggle(!active) }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun AlignButton(
    active: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) Accent else PanelLight)
            .size(40.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ColorRow(title: String, colorVal: Int, onPick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(colorVal))
                .border(2.dp, Color.White, CircleShape)
                .clickable { onPick() }
        )
    }
}
