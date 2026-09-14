package com.grooxtyper.app.native

import android.graphics.Bitmap

object NativeEngine {
    init {
        System.loadLibrary("grooxtyper")
    }

    external fun nativeInpaintTelea(srcBitmap: Bitmap, maskBitmap: Bitmap, radius: Double)
}
