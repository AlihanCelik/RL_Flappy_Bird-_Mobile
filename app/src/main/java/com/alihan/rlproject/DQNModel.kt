package com.alihan.rlproject

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor

class DQNModel(val module: Module) {

    private val frameStack = ArrayDeque<FloatArray>(4)

    private val processBitmap = Bitmap.createBitmap(84, 84, Bitmap.Config.ARGB_8888)
    private val processCanvas = Canvas(processBitmap)
    private val paint = Paint()
    private val pixels = IntArray(84 * 84)

    private val srcRect = Rect(0, 0, 288, 404)
    private val dstRect = Rect(0, 0, 84, 84)

    init {
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        paint.isFilterBitmap = true
    }

    companion object {
        fun loadFromAssets(assetPath: String): DQNModel {
            val module = Module.load(assetPath)
            return DQNModel(module)
        }
    }

    fun preprocess(rawBitmap: Bitmap): FloatArray {
        processCanvas.drawBitmap(rawBitmap, srcRect, dstRect, paint)

        processBitmap.getPixels(pixels, 0, 84, 0, 0, 84, 84)

        val w = 84
        val h = 84
        val arr = FloatArray(w * h)
        var arrIndex = 0

        for (x in 0 until w) {
            for (y in 0 until h) {
                val pixelIndex = y * w + x
                val p = pixels[pixelIndex]

                val r = (p shr 16) and 0xff

                arr[arrIndex++] = if (r > 1) 255.0f else 0.0f
            }
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