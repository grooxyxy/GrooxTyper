package com.grooxtyper.app.model.selection

/**
 * Mode gabungan seleksi ala Photoshop.
 *
 * NEW mengganti seleksi lama, ADD menambah (gabungan), SUBTRACT
 * menghapus irisan dari seleksi lama, INTERSECT hanya menyisakan
 * bagian yang tumpang tindih.
 */
enum class SelectionMode {
    NEW,
    ADD,
    SUBTRACT,
    INTERSECT
}
