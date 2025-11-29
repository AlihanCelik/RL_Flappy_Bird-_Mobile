package com.alihan.rlproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    private var model: DQNModel? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // copy model from assets to file and load
        val modelFile = Utils.copyAssetToFile(this, "pretrained_model/model.pt")
        model = DQNModel(DQNModel.loadFromAssets(modelFile.absolutePath).module) // adjust

        setContent {
            var frameBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

            // Compose UI
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(factory = { context ->
                    GameView(context).apply {
                        post {
                            initGame(width, height)
                        }
                    }
                }, update = { gameView ->
                    // Coroutine loop to run AI frames
                    if (job == null) {
                        job = scope.launch {
                            while (isActive) {
                                val bmp = (gameView as GameView).captureFrame()
                                if (bmp != null && model != null) {
                                    val actionIndex = model!!.predictAction(bmp)
                                    val actionArray = if (actionIndex == 0) intArrayOf(1, 0) else intArrayOf(0, 1)
                                    val (reward, terminal) = (gameView as GameView).step(actionArray)
                                }
                                delay(33) // ~30 FPS
                            }
                        }
                    }
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
