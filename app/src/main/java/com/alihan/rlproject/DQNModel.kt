package com.alihan.rlproject

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor

class DQNModel(val module: Module) {

    private val frameStack = ArrayDeque<FloatArray>(4)

    private val processBitmap = Bitmap.createBitmap(84, 84, Bitmap.Config.ARGB_8888)
    private val processCanvas = Canvas(processBitmap)
    private val scaleMatrix = Matrix()
    private val paint = Paint()
    private val pixels = IntArray(84 * 84)

    init {
        val cm = ColorMatrix()
        cm.setSaturation(0f) // Grayscale
        paint.colorFilter = ColorMatrixColorFilter(cm)
    }

    companion object {
        fun loadFromAssets(assetPath: String): DQNModel {
            val module = Module.load(assetPath)
            return DQNModel(module)
        }
    }

    fun preprocess(bitmap: Bitmap): FloatArray {
        // 1. Matris Ayarı:
        // Kaynak (Src): 288x404 (Zemini kestik) -> Hedef (Dst): 84x84
        // scaleX = 84 / 288
        // scaleY = 84 / 404
        val sx = 84f / 288f
        val sy = 84f / 404f
        scaleMatrix.setScale(sx, sy)

        // 2. Çizim (Crop + Resize + Grayscale tek seferde)
        // Canvas, gelen bitmap'in sadece 0..404 yüksekliğini alıp 84x84'e sıkıştırıp çizer.
        processCanvas.drawBitmap(bitmap, scaleMatrix, paint)

        // 3. Pixel Okuma
        processBitmap.getPixels(pixels, 0, 84, 0, 0, 84, 84)

        val w = 84
        val h = 84
        val arr = FloatArray(w * h)

        // 4. Threshold (Eşikleme)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xff

            arr[i] = if (r > 1) 255.0f else 0.0f
        }
        return arr
    }

    fun appendFrame(fr: FloatArray) {
        if (frameStack.size == 4) frameStack.removeFirst()
        frameStack.addLast(fr)
    }

    private fun getInputTensor(): Tensor {
        val channels = 4
        val w = 84
        val h = 84
        val data = FloatArray(channels * w * h)
        val list = frameStack.toList()
        for (c in 0 until channels) {
            val frame = if (c < list.size) list[c] else list.last()
            System.arraycopy(frame, 0, data, c * w * h, frame.size)
        }
        return Tensor.fromBlob(data, longArrayOf(1, channels.toLong(), h.toLong(), w.toLong()))
    }

    fun predictAction(rawScreenBitmap: Bitmap): Int {
        val processed = preprocess(rawScreenBitmap)

        if (frameStack.isEmpty()) {
            repeat(4) { frameStack.addLast(processed) }
        } else {
            appendFrame(processed)
        }

        val input = getInputTensor()
        val outputs = module.forward(IValue.from(input)).toTensor()
        val scores = outputs.dataAsFloatArray

        var best = 0
        var bestVal = scores[0]
        for (i in 1 until scores.size) {
            if (scores[i] > bestVal) {
                bestVal = scores[i]
                best = i
            }
        }
        return best
    }
}