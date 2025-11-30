package com.alihan.rlproject

import android.graphics.Bitmap
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor

class DQNModel(val module: Module) {

    private val frameStack = ArrayDeque<FloatArray>(4)

    companion object {
        fun loadFromAssets(assetPath: String): DQNModel {
            val module = Module.load(assetPath)
            return DQNModel(module)
        }
    }

    fun preprocess(bitmap: Bitmap): FloatArray {
        // 1. ADIM: KIRPMA (CROP)
        // Python'daki image[0:288, 0:404] mantığı.
        // Ekranın altındaki zemini (yaklaşık son 108 pikseli) atıyoruz.
        // Bitmap 288x512 boyutunda geliyor varsayıyoruz (GameView'daki LOGICAL boyutlar).

        val croppedWidth = bitmap.width
        // Yükseklik 404 piksel olacak (veya oransal olarak yaklaşık %79'u)
        val croppedHeight = if (bitmap.height > 404) 404 else bitmap.height

        val cropped = Bitmap.createBitmap(bitmap, 0, 0, croppedWidth, croppedHeight)

        // 2. ADIM: YENİDEN BOYUTLANDIRMA (RESIZE)
        // Şimdi kırpılmış görüntüyü 84x84 yapıyoruz.
        val resized = Bitmap.createScaledBitmap(cropped, 84, 84, false)

        val w = 84
        val h = 84
        val arr = FloatArray(w * h)
        val pixels = IntArray(w * h)

        resized.getPixels(pixels, 0, w, 0, 0, w, h)

        for (i in pixels.indices) {
            val p = pixels[i]

            // RGB -> Gray conversion
            val r = (p shr 16) and 0xff
            val g = (p shr 8) and 0xff
            val b = p and 0xff
            val gray = ((0.299 * r + 0.587 * g + 0.114 * b)).toInt()

            // Python: image_data[image_data > 0] = 255
            // Arka plan siyah (0), borular ve kuş aydınlık (255)
            arr[i] = if (gray > 0) 255.0f else 0.0f
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
        val inputTensor = Tensor.fromBlob(data, longArrayOf(1, channels.toLong(), h.toLong(), w.toLong()))
        return inputTensor
    }

    fun predictAction(bitmapFrame: Bitmap): Int {
        val processed = preprocess(bitmapFrame)
        appendFrame(processed)
        if (frameStack.size < 4) {
            while (frameStack.size < 4) frameStack.addLast(processed)
        }
        val input = getInputTensor()
        val outputs = module.forward(IValue.from(input)).toTensor()
        val scores = outputs.dataAsFloatArray
        // argmax
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