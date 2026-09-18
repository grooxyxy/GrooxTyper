package com.grooxtyper.app.native

import android.graphics.Bitmap

object NativeEngine {
    init {
        System.loadLibrary("grooxtyper")
    }

    external fun nativeInpaintTelea(srcBitmap: Bitmap, maskBitmap: Bitmap, radius: Double)

    /** Navier-Stokes isophote transport (opsi heal STRUKTUR). iterations<=0 = otomatis. */
    external fun nativeInpaintNS(srcBitmap: Bitmap, maskBitmap: Bitmap, radius: Double, iterations: Int)
}
