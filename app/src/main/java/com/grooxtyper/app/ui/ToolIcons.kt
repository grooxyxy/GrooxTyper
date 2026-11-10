package com.grooxtyper.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ikon SVG kustom untuk tiap tool GrooxTyper.
 *
 * ## Kenapa bukan Material Icons?
 *
 * Ikon bawaan Material tak punya makna untuk tool lettering manga
 * (gelembung yang dirapikan, tukang pahat, pemisah teks). Jadi tiap tool
 * punya ikon sendiri yang digambar tangan.
 *
 * ## Format & sumber kebenaran
 *
 * Setiap ikon = daftar path **gaya garis** (data path SVG) + opsional
 * path terisi. Bentuknya Defining di
 * `app/src/main/assets/tool-icons/` (satu file .svg per ikon) — file SVG itu
 * DIHASILKAN dari
 * data yang sama di sini lewat `scripts/gen-tool-icons.mjs`, jadi tak
 * mungkin berbeda antara kode dan aset.
 *
 * Format path: absolut, perintah `M`/`L`/`C`/`Z`, koordinat dipisah
 * spasi (desimal pakai titik). Ini dibaca `PathParser` bawaan Compose.
 *
 * Kotak viewBox 24x24.
 */
object ToolIcons {

    /** Lebar garis ikon (dalam unit viewBox 24). */
    private const val STROKE = 1.7f

    /** Definisi satu ikon: path garis + path terisi. */
    class Def(
        val strokes: List<String>,
        val fills: List<String> = emptyList()
    )

    // ------------------------------------------------------------------
    // Data path (absolut, M/L/C/Z)
    // ------------------------------------------------------------------

    val brush = Def(
        strokes = listOf(
            "M13.5 4.5 L19.5 10.5 L10 20 L4 20 L4 14 Z",
            "M13.5 4.5 L17 1 L23 7 L19.5 10.5",
            "M4 20 L8.5 15.5"
        )
    )

    val eraser = Def(
        strokes = listOf(
            "M8 20 L3.5 15.5 L12 7 L19 14 L13 20 Z",
            "M8 20 L20 20",
            "M12 7 L17 12"
        )
    )

    val pen = Def(
        strokes = listOf(
            "M4 20 L6.5 15 L16 5.5 L18.5 8 L9 17.5 Z",
            "M16 5.5 L18.5 3 L21 5.5 L18.5 8"
        )
    )

    val clone = Def(
        strokes = listOf(
            "M9 9 L9 4 L20 4 L20 15 L15 15",
            "M4 9 L15 9 L15 20 L4 20 Z"
        )
    )

    val heal = Def(
        strokes = listOf(
            "M3 12 L12 3 L21 12 L12 21 Z",
            "M12 7 L12 17",
            "M7 12 L17 12"
        )
    )

    val bubble = Def(
        strokes = listOf(
            "M4 13 C4 8.5 7.5 5 12 5 C16.5 5 20 8.5 20 13 C20 17 17 19.5 13 20 L8 21.5 L9.5 18.5 C6.3 17.8 4 15.8 4 13 Z"
        )
    )

    val bubbleClean = Def(
        strokes = listOf(
            "M4 12 C4 8 7 5 11.5 5 C16 5 19 8 19 12 C19 15.5 16.5 18 13 18.5 L9 20 L10 17.5 C6.5 17 4 15 4 12 Z",
            "M14.5 20.5 L20.5 14.5",
            "M17.5 17.5 L15.5 15.5",
            "M19.5 19.5 L17.5 17.5"
        )
    )

    val reshape = Def(
        strokes = listOf(
            "M4 12 C4 8 7 5 11.5 5 C16 5 19 8 19 12 C19 15.5 16.5 18 13 18.5 L9 20 L10 17.5 C6.5 17 4 15 4 12 Z"
        ),
        fills = listOf(
            "M12 10.5 C12.83 10.5 13.5 11.17 13.5 12 C13.5 12.83 12.83 13.5 12 13.5 C11.17 13.5 10.5 12.83 10.5 12 C10.5 11.17 11.17 10.5 12 10.5 Z",
            "M8 10.5 C8.83 10.5 9.5 11.17 9.5 12 C9.5 12.83 8.83 13.5 8 13.5 C7.17 13.5 6.5 12.83 6.5 12 C6.5 11.17 7.17 10.5 8 10.5 Z",
            "M16 10.5 C16.83 10.5 17.5 11.17 17.5 12 C17.5 12.83 16.83 13.5 16 13.5 C15.17 13.5 14.5 12.83 14.5 12 C14.5 11.17 15.17 10.5 16 10.5 Z"
        )
    )

    val gradient = Def(
        strokes = listOf(
            "M4 5 L20 5 L20 19 L4 19 Z",
            "M4 9 L20 9",
            "M4 13 L20 13",
            "M4 17 L20 17"
        )
    )

    val wand = Def(
        strokes = listOf(
            "M4 20 L14 10",
            "M12.5 6.5 L17.5 11.5",
            "M6 2 L6 7 M3.5 4.5 L8.5 4.5",
            "M17 2 L17 5 M15.5 3.5 L18.5 3.5",
            "M19 14 L19 19 M16.5 16.5 L21.5 16.5"
        )
    )

    val marquee = Def(
        strokes = listOf(
            "M4 4 L9 4 M13 4 L20 4 M4 4 L4 9 M4 13 L4 20 M4 20 L9 20 M13 20 L20 20 M20 20 L20 13 M20 9 L20 4"
        )
    )

    val ellipse = Def(
        strokes = listOf(
            "M4 12 C4 8 7.5 5 12 5 C16.5 5 20 8 20 12 C20 16 16.5 19 12 19 C7.5 19 4 16 4 12 Z"
        )
    )

    val lasso = Def(
        strokes = listOf(
            "M12 4 C6.5 4 3 7 3 10.5 C3 13.5 5.5 15.5 8.5 16 L8 19.5 C8 20.5 9 21 10 20.5 L12 19.5",
            "M12 4 C17.5 4 21 7 21 10.5 C21 13 19.5 14.5 17.5 15.5"
        )
    )

    val polygon = Def(
        strokes = listOf(
            "M12 3 L20 9 L17 19 L7 19 L4 9 Z",
            "M12 3 L12 3.01 M20 9 L20 9.01 M17 19 L17 19.01 M7 19 L7 19.01 M4 9 L4 9.01"
        )
    )

    val text = Def(
        strokes = listOf(
            "M5 5 L19 5",
            "M12 5 L12 19",
            "M8.5 19 L15.5 19"
        )
    )

    val eyedropper = Def(
        strokes = listOf(
            "M15 4 L20 9 L18 11 L13 6 Z",
            "M13.5 5.5 L4 15 L3 21 L9 20 L18.5 10.5"
        )
    )

    val image = Def(
        strokes = listOf(
            "M4 5 L20 5 L20 19 L4 19 Z",
            "M4 16 L9 11 L13 15 L16 12 L20 16"
        ),
        fills = listOf("M8.5 7 C9.33 7 10 7.67 10 8.5 C10 9.33 9.33 10 8.5 10 C7.67 10 7 9.33 7 8.5 C7 7.67 7.67 7 8.5 7 Z")
    )

    val pan = Def(
        strokes = listOf(
            "M8 13 L8 5.5 C8 4.5 9.5 4.5 9.5 5.5 L9.5 11 L9.5 4 C9.5 3 11 3 11 4 L11 11 L11 5 C11 4 12.5 4 12.5 5 L12.5 11 L12.5 6.5 C12.5 5.5 14 5.5 14 6.5 L14 12 C15 11 16.5 11.5 16.5 13.5 C16.5 16.5 14.5 19.5 11.5 19.5 C8.5 19.5 6.5 17.5 5 15.5 L4 13.5 C3.5 12.5 4.5 11.5 5.5 12.5 L8 14"
        )
    )

    val layers = Def(
        strokes = listOf(
            "M12 3 L21 8 L12 13 L3 8 Z",
            "M3 12 L12 17 L21 12",
            "M3 16 L12 21 L21 16"
        )
    )

    val script = Def(
        strokes = listOf(
            "M6 3 L14 3 L18 7 L18 21 L6 21 Z",
            "M14 3 L14 7 L18 7",
            "M9 12 L15 12 M9 16 L15 16"
        )
    )

    val color = Def(
        strokes = listOf(
            "M12 3 C12 3 5 11 5 15 C5 18.5 8 21 11 21 C11 21 11 18 13 18 C15 18 15 21 15 21 C18.5 21 21 18 21 15 C21 11 12 3 12 3 Z"
        )
    )

    val inpaint = Def(
        strokes = listOf(
            "M12 3 L12 21",
            "M3 12 L21 12",
            "M6 6 L18 18",
            "M18 6 L6 18"
        )
    )

    /** Semua ikon, untuk generate file SVG. */
    val all: Map<String, Def> = linkedMapOf(
        "brush" to brush,
        "eraser" to eraser,
        "pen" to pen,
        "clone" to clone,
        "heal" to heal,
        "bubble" to bubble,
        "bubbleClean" to bubbleClean,
        "reshape" to reshape,
        "gradient" to gradient,
        "wand" to wand,
        "marquee" to marquee,
        "ellipse" to ellipse,
        "lasso" to lasso,
        "polygon" to polygon,
        "text" to text,
        "eyedropper" to eyedropper,
        "image" to image,
        "pan" to pan,
        "layers" to layers,
        "script" to script,
        "color" to color,
        "inpaint" to inpaint
    )

    private val cache = HashMap<String, ImageVector>()

    /**
     * Bangun (dan cache) ImageVector untuk ikon bernama [name].
     * Null bila nama tak dikenal.
     */
    fun of(name: String): ImageVector? {
        cache[name]?.let { return it }
        val def = all[name] ?: return null
        val built = build(def)
        cache[name] = built
        return built
    }

    /** Bangun ImageVector dari definisi ikon. */
    private fun build(def: Def): ImageVector {
        val strokes = def.strokes.map { parsePath(it) }.filter { it.isNotEmpty() }
        val fills = def.fills.map { parsePath(it) }.filter { it.isNotEmpty() }
        return ImageVector.Builder(
            name = "GrooxTool",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            fills.forEach { d ->
                addPath(
                    pathData = d,
                    fill = SolidColor(Color.White),
                    stroke = null
                )
            }
            strokes.forEach { d ->
                addPath(
                    pathData = d,
                    fill = null,
                    stroke = SolidColor(Color.White),
                    strokeLineWidth = STROKE,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round
                )
            }
        }.build()
    }

    /**
     * Parser data path SVG sederhana: perintah `M`, `L`, `C`, `Z` (absolut),
     * koordinat dipisah spasi.
     *
     * `PathParser` bawaan Compose sebenarnya internal, jadi parser sendiri
     * dipakai. Format ikon di sini sengaja dibatasi supaya parsingnya
     * sederhana dan pasti benar.
     */
    private fun parsePath(d: String): List<PathNode> {
        val tokens = d.trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
        val nodes = mutableListOf<PathNode>()
        var i = 0
        var cmd = ' '
        var subStartX = 0f
        var subStartY = 0f
        fun num(): Float {
            val t = tokens.getOrNull(i) ?: return 0f
            i++
            return t.toFloatOrNull() ?: 0f
        }
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.length == 1 && t[0].isLetter()) {
                cmd = t[0]
                i++
                continue
            }
            when (cmd) {
                'M' -> {
                    val x = num()
                    val y = num()
                    nodes.add(PathNode.MoveTo(x, y))
                    subStartX = x
                    subStartY = y
                    cmd = 'L'
                }
                'L' -> {
                    val x = num()
                    val y = num()
                    nodes.add(PathNode.LineTo(x, y))
                }
                'C' -> {
                    val x1 = num(); val y1 = num()
                    val x2 = num(); val y2 = num()
                    val x3 = num(); val y3 = num()
                    nodes.add(PathNode.CurveTo(x1, y1, x2, y2, x3, y3))
                }
                // PathNode.Close tak terlihat dari Kotlin, jadi subpath
                // ditutup dengan garis balik ke titik awal - untuk bentuk
                // tertutup di ikon ini hasilnya sama persis.
                'Z' -> nodes.add(PathNode.LineTo(subStartX, subStartY))
                else -> i++
            }
        }
        return nodes
    }
}
