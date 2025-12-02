package com.alihan.rlproject

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

class AssetLoader(private val ctx: Context) {
    fun loadBitmap(pathInAssets: String): Bitmap {
        val opts = BitmapFactory.Options()
        opts.inScaled = false

        ctx.assets.open(pathInAssets).use { stream ->
            return BitmapFactory.decodeStream(stream, null, opts)!!
        }
    }
}