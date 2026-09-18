package com.grooxtyper.app.native

import android.graphics.Bitmap

object NativeEngine {
    init {
        System.loadLibrary("grooxtyper")
    }

    external fun nativeInpaintTelea(srcBitmap: Bitmap, maskBitmap: Bitmap, radius: Double)

    /** Navier-Stokes isophote onion-peel (opsi heal STRUKTUR). iterations<=0 = otomatis. */
    external fun nativeInpaintNS(srcBitmap: Bitmap, maskBitmap: Bitmap, radius: Double, iterations: Int)

    /**
     * Pyramid push-pull inpainting (opsi heal TEKSTUR/GRADASI + seleksi):
     * mengisi lubang dari konteks multi-skala (gradasi tersambung mulus pada
     * mask besar) lalu mensintesis grain dari statistik cincin sekitar lubang.
     */
    external fun nativeInpaintPyramid(srcBitmap: Bitmap, maskBitmap: Bitmap, maxLevels: Int, grainScale: Double)
}
