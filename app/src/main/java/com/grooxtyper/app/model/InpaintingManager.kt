package com.grooxtyper.app.model

import android.graphics.Bitmap
import com.grooxtyper.app.native.NativeEngine

class InpaintingManager {

    fun inpaintLayerArea(
        layer: DrawingLayer,
        maskBitmap: Bitmap,
        inpaintRadius: Double = 5.0
    ) {
        val srcBitmap = layer.getBitmap()
        NativeEngine.nativeInpaintTelea(srcBitmap, maskBitmap, inpaintRadius)
        layer.tileMap.importFromBitmap(srcBitmap)
        layer.markDirty()
    }
}
