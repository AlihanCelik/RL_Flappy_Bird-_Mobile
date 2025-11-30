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

        try {
            val modelFile = Utils.copyAssetToFile(this, "pretrained_model/model.pt")
            model = DQNModel(DQNModel.loadFromAssets(modelFile.absolutePath).module)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(factory = { context ->
                    val gameView = GameView(context)

                    gameView.onReady = {
                        if (job == null) {
                            job = scope.launch {
                                while (isActive) {
                                    val loopStart = System.currentTimeMillis()

                                    val bmp = gameView.captureFrame()
                                    if (bmp != null && model != null) {
                                        val actionIndex = model!!.predictAction(bmp)
                                        val actionArray = if (actionIndex == 0) intArrayOf(1, 0) else intArrayOf(0, 1)
                                        gameView.step(actionArray)
                                    }

                                    // İşlem ne kadar sürdüyse, 33ms'den (30 FPS) kalanı kadar bekle
                                    val loopTime = System.currentTimeMillis() - loopStart
                                    val waitTime = 33 - loopTime
                                    if (waitTime > 0) {
                                        delay(waitTime)
                                    }
                                }
                            }
                        }
                    }

                    gameView.doOnLayout {
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
