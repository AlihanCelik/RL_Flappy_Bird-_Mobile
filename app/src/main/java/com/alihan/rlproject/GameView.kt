package com.alihan.rlproject

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.util.concurrent.atomic.AtomicBoolean

class GameView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    var onReady: (() -> Unit)? = null
    private var thread: Thread? = null
    private val running = AtomicBoolean(false)
    private lateinit var gameState: GameState
    private var spritesReady = false

    // Python ile birebir aynı mantıksal çözünürlük
    private val LOGICAL_WIDTH = 288
    private val LOGICAL_HEIGHT = 512

    // Bu bitmap tam olarak 288x512 olacak. Model bunu bekliyor.
    private lateinit var gameBitmap: Bitmap
    private lateinit var gameCanvas: Canvas
    private lateinit var scaleMatrix: Matrix

    private lateinit var bg: Bitmap
    private lateinit var base: Bitmap
    private lateinit var playerFrames: Array<Bitmap>
    private lateinit var pipeTop: Bitmap
    private lateinit var pipeBottom: Bitmap

    init { holder.addCallback(this) }

    fun initGame(viewWidth: Int, viewHeight: Int) {
        val assets = AssetLoader(context)

        // AssetLoader artık resmi scale etmeden (orijinal boyutta) getirecek.
        bg = assets.loadBitmap("sprites/background-black.png")
        base = assets.loadBitmap("sprites/base.png")
        playerFrames = arrayOf(
            assets.loadBitmap("sprites/redbird-upflap.png"),
            assets.loadBitmap("sprites/redbird-midflap.png"),
            assets.loadBitmap("sprites/redbird-downflap.png")
        )

        val rawPipe = assets.loadBitmap("sprites/pipe-green.png")
        pipeBottom = rawPipe

        val matrix = Matrix()
        matrix.postRotate(180f)
        pipeTop = Bitmap.createBitmap(rawPipe, 0, 0, rawPipe.width, rawPipe.height, matrix, false)

        // Tam 288x512 boyutunda sanal ekran
        gameBitmap = Bitmap.createBitmap(LOGICAL_WIDTH, LOGICAL_HEIGHT, Bitmap.Config.ARGB_8888)
        gameCanvas = Canvas(gameBitmap)

        // Bu sadece kullanıcının ekranda görmesi için scale, modele giden veriyi etkilemez.
        val sx = viewWidth.toFloat() / LOGICAL_WIDTH
        val sy = viewHeight.toFloat() / LOGICAL_HEIGHT
        scaleMatrix = Matrix()
        scaleMatrix.setScale(sx, sy)

        spritesReady = true

        gameState = GameState(
            LOGICAL_WIDTH, LOGICAL_HEIGHT,
            playerFrames[0].width, playerFrames[0].height,
            pipeTop.width, pipeTop.height,
            bg.width, base.width
        )

        onReady?.invoke()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running.set(true)
        thread = Thread {
            while (running.get()) {
                val canvas = holder.lockCanvas()
                if (canvas != null) {
                    drawGame()
                    // Ekrana çizerken büyüt (Kullanıcı için)
                    canvas.drawBitmap(gameBitmap, scaleMatrix, null)
                    holder.unlockCanvasAndPost(canvas)
                }
                // Oyunun insan gözü için akıcılığı (Modelden bağımsız)
                try { Thread.sleep(16) } catch (e: InterruptedException) {}
            }
        }.apply { start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running.set(false)
        try { thread?.join() } catch (e: InterruptedException) {}
    }

    private fun drawGame() {
        if (!spritesReady) return

        // Oyunu sanal ekrana (288x512) çiziyoruz.
        gameCanvas.drawBitmap(bg, 0f, 0f, null)

        for (i in gameState.upperPipes.indices) {
            val u = gameState.upperPipes[i]
            val l = gameState.lowerPipes[i]
            gameCanvas.drawBitmap(pipeTop, u.first.toFloat(), u.second.toFloat(), null)
            gameCanvas.drawBitmap(pipeBottom, l.first.toFloat(), l.second.toFloat(), null)
        }

        val baseY = (LOGICAL_HEIGHT * 0.79f)
        gameCanvas.drawBitmap(base, gameState.baseX.toFloat(), baseY, null)

        val frame = playerFrames[gameState.playerIndex % playerFrames.size]
        gameCanvas.drawBitmap(frame, gameState.playerX.toFloat(), gameState.playerY.toFloat(), null)
    }

    // Modelin kullanacağı ham görüntü (288x512)
    fun captureFrame(): Bitmap? {
        if (!spritesReady) return null
        return gameBitmap
    }

    fun step(action: IntArray): Pair<Float, Boolean> {
        if (!::gameState.isInitialized) return Pair(0f, true)
        return gameState.frameStep(action)
    }
}