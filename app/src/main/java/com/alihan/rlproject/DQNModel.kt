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

    // 84x84 boyutunda işlem bitmap'i
    private val processBitmap = Bitmap.createBitmap(84, 84, Bitmap.Config.ARGB_8888)
    private val processCanvas = Canvas(processBitmap)
    private val paint = Paint()
    private val pixels = IntArray(84 * 84)

    // Python: image[0:288, 0:404] -> (Genişlik: 288, Yükseklik: 404)
    private val srcRect = Rect(0, 0, 288, 404)
    private val dstRect = Rect(0, 0, 84, 84)

    init {
        val cm = ColorMatrix()
        cm.setSaturation(0f) // Siyah-Beyaz yap
        paint.colorFilter = ColorMatrixColorFilter(cm)
        paint.isFilterBitmap = true // Yumuşak geçiş
    }

    companion object {
        fun loadFromAssets(assetPath: String): DQNModel {
            val module = Module.load(assetPath)
            return DQNModel(module)
        }
    }

    fun preprocess(rawBitmap: Bitmap): FloatArray {
        // 1. Resmi Kırp ve Küçült (Crop & Resize)
        processCanvas.drawBitmap(rawBitmap, srcRect, dstRect, paint)

        // 2. Pikselleri Oku
        processBitmap.getPixels(pixels, 0, 84, 0, 0, 84, 84)

        val w = 84
        val h = 84
        val arr = FloatArray(w * h)
        var arrIndex = 0

        // --- KRİTİK DÜZELTME: TRANSPOZE OKUMA ---
        // Python'daki Pygame array yapısı (Width, Height) şeklindedir.
        // Flatten yapıldığında önce Y eksenini (Sütunları), sonra X eksenini tarar.
        // Android Bitmap ise (Height, Width) şeklindedir.
        // Python ile aynı veri sırasını yakalamak için döngüleri TERS kuruyoruz.

        // Dış döngü X (Genişlik), İç döngü Y (Yükseklik) olmalı.
        for (x in 0 until w) {
            for (y in 0 until h) {
                // Bitmap pixel array'i her zaman "row-major"dır (y * w + x).
                // Biz sadece okuma sırasını değiştiriyoruz.
                val pixelIndex = y * w + x
                val p = pixels[pixelIndex]

                // Sadece Kırmızı kanalına bak (Zaten siyah-beyaz)
                val r = (p shr 16) and 0xff

                // Python: image_data[image_data > 0] = 255
                // Eşikleme (Thresholding)
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

        // Tensor Boyutları: [Batch, Channel, Height, Width]
        // Not: Burada height/width isimlendirmesi PyTorch standardıdır,
        // ama içerik olarak Python'daki transpoze veriyi taşıyoruz.
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

        // En yüksek skoru seç (Argmax)
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