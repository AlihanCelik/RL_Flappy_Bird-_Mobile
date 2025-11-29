package com.alihan.rlproject

import android.content.Context
import java.io.File
import java.io.FileOutputStream
object Utils {
    fun copyAssetToFile(ctx: Context, assetPath: String): File {
        val outFile = File(ctx.filesDir, assetPath.substringAfterLast("/"))
        outFile.parentFile?.mkdirs()
        ctx.assets.open(assetPath).use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return outFile
    }
}
