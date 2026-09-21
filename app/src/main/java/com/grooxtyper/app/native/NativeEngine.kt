package com.grooxtyper.app.native

import android.graphics.Bitmap

object NativeEngine {
    init {
        System.loadLibrary("grooxtyper")
    }

    external fun nativeInpaintTelea(srcBitmap: Bitmap, maskBitmap: Bitmap, radius: Double)

    /**
     * Pyramid push-pull inpainting (inpaint seleksi): mengisi lubang dari
     * konteks multi-skala (gradasi tersambung mulus pada mask besar) lalu
     * mensintesis grain dari statistik cincin sekitar lubang.
     */
    external fun nativeInpaintPyramid(srcBitmap: Bitmap, maskBitmap: Bitmap, maxLevels: Int, grainScale: Double)
}
