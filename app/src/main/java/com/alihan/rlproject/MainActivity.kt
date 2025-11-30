package com.alihan.rlproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    private var model: DQNModel? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Model dosyasını kopyala ve yükle
        try {
            val modelFile = Utils.copyAssetToFile(this, "pretrained_model/model.pt")
            model = DQNModel(DQNModel.loadFromAssets(modelFile.absolutePath).module)
        } catch (e: Exception) {
            e.printStackTrace() // Logcat'e hata bas
        }

        setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(factory = { context ->
                    val gameView = GameView(context)

                    gameView.onReady = {
                        if (job == null) {
                            job = scope.launch {
                                while (isActive) {
                                    // captureFrame artık 288x512 dönüyor, bu DQN için çok daha temiz.
                                    val bmp = gameView.captureFrame()

                                    if (bmp != null && model != null) {
                                        // Bitmap'i modelin istediği formata DQNModel içinde preprocess çeviriyor zaten
                                        val actionIndex = model!!.predictAction(bmp)
                                        val actionArray = if (actionIndex == 0) intArrayOf(1, 0) else intArrayOf(0, 1)

                                        // UI Thread dışında step çağrısı yapıyoruz, GameState thread-safe olmalı
                                        // veya basitçe senkronize edilmeli. Mevcut basit yapıda sorun olmayabilir.
                                        gameView.step(actionArray)
                                    }
                                    delay(33) // ~30 FPS
                                }
                            }
                        }
                    }

                    gameView.doOnLayout {
                        // Burada view'in gerçek boyutlarını gönderiyoruz
                        gameView.initGame(it.width, it.height)
                    }

                    gameView
                })
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
        scope.cancel()
    }
}
