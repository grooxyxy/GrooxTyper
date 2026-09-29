package com.grooxtyper.app.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.grooxtyper.app.model.CanvasViewState
import com.grooxtyper.app.model.RulerGuide
import com.grooxtyper.app.model.RulerType
import com.grooxtyper.app.model.TextBox

/**
 * Gestur mode overlay: seret handle perspektif (4 sudut bebas) + atur
 * penggaris. Dipisah dari badan [CanvasEditorScreen] sebagai Modifier
 * tersendiri karena fungsi utama sudah menyentuh batas method JVM 64KB.
 */
fun Modifier.perspectiveRulerGestures(
    perspGridMode: Boolean,
    rulerAdjustMode: Boolean,
    selectedBox: TextBox?,
    guide: RulerGuide,
    viewState: CanvasViewState,
    screenToCanvas: (Float, Float) -> Offset,
    onPerspUndo: (TextBox) -> Unit,
    onPerspDrag: (TextBox) -> Unit,
    onRulerDrag: () -> Unit
): Modifier = pointerInput(perspGridMode, rulerAdjustMode, selectedBox) {
    if (perspGridMode && selectedBox == null) return@pointerInput
    if (!perspGridMode) {
        if (!rulerAdjustMode) return@pointerInput
        if (guide.type == RulerType.OFF) return@pointerInput
    }
    // 0=tidak ada, 1=ujung A, 2=ujung B, 3=geser, 4=radius lingkaran, 5=sudut perspektif
    var dragMode = 0
    // Handle perspektif yang DIKUNCI saat drag mulai (0=none,
    // 1=sisi atas, 2=sisi bawah, 3=tepi kiri, 4=tepi kanan).
    var perspHandle = 0
    detectDragGestures(
        onDragStart = { pos ->
            dragMode = 0
            val cp = screenToCanvas(pos.x, pos.y)
            val rg = guide
            if (perspGridMode) {
                // Handle perspektif dikunci SEKALI saat drag mulai.
                // Handle = 4 SUDUT trapesium (bukan tepi tengah),
                // sehingga arah perspektif bisa digeser leluasa.
                perspHandle = 0
                val pbox = selectedBox
                if (pbox != null) {
                    val sc = viewState.scale.coerceAtLeast(0.05f)
                    val corners = com.grooxtyper.app.model.PerspectiveGrid.cornersCanvas(pbox)
                    val grab = 96f / sc
                    var bestD = Float.MAX_VALUE
                    for (ci in 0..3) {
                        val d = kotlin.math.hypot(
                            cp.x - corners[ci].x, cp.y - corners[ci].y
                        )
                        if (d < bestD) {
                            bestD = d
                            perspHandle = ci + 1
                        }
                    }
                    if (bestD > grab) perspHandle = 0
                    if (perspHandle != 0) {
                        // Satu langkah undo untuk seluruh gestur perspektif.
                        onPerspUndo(pbox)
                    }
                }
                dragMode = if (perspHandle != 0) 5 else 0
            } else {
                val grab = 48f / viewState.scale.coerceAtLeast(0.05f)
                when (rg.type) {
                    RulerType.STRAIGHT_LINE -> {
                        val dA = (cp - rg.startPos).getDistance()
                        val dB = (cp - rg.endPos).getDistance()
                        val dM = (cp - rg.midPoint()).getDistance()
                        dragMode = when {
                            dA <= grab && dA <= dB -> 1
                            dB <= grab -> 2
                            dM <= grab * 1.4f -> 3
                            else -> 0
                        }
                    }
                    RulerType.CIRCLE -> {
                        val dC = (cp - rg.circleCenter).getDistance()
                        dragMode = when {
                            dC <= grab -> 3
                            kotlin.math.abs(dC - rg.circleRadius) <= grab -> 4
                            else -> 0
                        }
                    }
                    RulerType.OFF -> dragMode = 0
                }
            }
        },
        onDrag = { change, drag ->
            val cp = screenToCanvas(change.position.x, change.position.y)
            val rg = guide
            if (perspGridMode) {
                val box = selectedBox ?: return@detectDragGestures
                if (perspHandle == 0) return@detectDragGestures
                val sc = viewState.scale.coerceAtLeast(0.05f)
                val area = com.grooxtyper.app.model.PerspectiveGrid.contentRect(box)
                // Area tak wajar (NaN atau gigantic) = state rusak: batalkan
                // gestur daripada mengirim angka aneh ke renderer.
                if (!area.width().isFinite() || !area.height().isFinite()) {
                    return@detectDragGestures
                }
                if (area.width() > 2_000_000f || area.height() > 2_000_000f) {
                    return@detectDragGestures
                }
                val hw = (area.width() / 2f).coerceAtLeast(1f)
                val hh = (area.height() / 2f).coerceAtLeast(1f)
                // Delta-based: sudut mengikuti GERAKAN jari (bukan
                // posisi absolut) → halus, tanpa lompatan, responsif
                // sejauh apa pun jari diseret.
                val dx = drag.x / sc / hw
                val dy = drag.y / sc / hh
                val p = box.persp ?: com.grooxtyper.app.model.PerspSpec
                    .fromKeystone(box.perspX, box.perspY)
                when (perspHandle) {
                    1 -> { p.tlX += dx; p.tlY += dy }
                    2 -> { p.trX += dx; p.trY += dy }
                    3 -> { p.brX += dx; p.brY += dy }
                    4 -> { p.blX += dx; p.blY += dy }
                }
                p.clampAll()
                box.persp = p
                onPerspDrag(box)
                change.consume()
            } else {
                val dvx = drag.x / viewState.scale.coerceAtLeast(0.05f)
                val dvy = drag.y / viewState.scale.coerceAtLeast(0.05f)
                when (dragMode) {
                    1 -> rg.startPos = cp
                    2 -> rg.endPos = cp
                    3 -> rg.move(dvx, dvy)
                    4 -> rg.circleRadius =
                        (cp - rg.circleCenter).getDistance().coerceIn(8f, 8000f)
                    else -> {}
                }
                if (dragMode != 0) {
                    onRulerDrag()
                    change.consume()
                }
            }
        }
    )
}
