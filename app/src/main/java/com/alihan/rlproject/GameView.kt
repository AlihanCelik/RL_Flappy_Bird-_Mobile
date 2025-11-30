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

    // Python: 288x512 sabit çözünürlük
    private val LOGICAL_WIDTH = 288
    private val LOGICAL_HEIGHT = 512

    private lateinit var gameBitmap: Bitmap
    private lateinit var gameCanvas: Canvas
    private lateinit var scaleMatrix: Matrix

    private lateinit var bg: Bitmap
    private lateinit var base: Bitmap
    private lateinit var playerFrames: Array<Bitmap>
    private lateinit var pipeTop: Bitmap    // Rotated (Üst Boru)
    private lateinit var pipeBottom: Bitmap // Normal (Alt Boru)

    init { holder.addCallback(this) }

    fun initGame(viewWidth: Int, viewHeight: Int) {
        val assets = AssetLoader(context)

        bg = assets.loadBitmap("sprites/background-black.png")
        base = assets.loadBitmap("sprites/base.png")
        playerFrames = arrayOf(
            assets.loadBitmap("sprites/redbird-upflap.png"),
            assets.loadBitmap("sprites/redbird-midflap.png"),
            assets.loadBitmap("sprites/redbird-downflap.png")
        )

        val rawPipe = assets.loadBitmap("sprites/pipe-green.png")

        // Python: IMAGES['pipe'][1] -> Normal (Alt Boru)
        pipeBottom = rawPipe

        // Python: IMAGES['pipe'][0] -> Rotated 180 (Üst Boru)
        val matrix = Matrix()
        matrix.postRotate(180f)
        pipeTop = Bitmap.createBitmap(rawPipe, 0, 0, rawPipe.width, rawPipe.height, matrix, false)

        // Sanal ekran buffer
        gameBitmap = Bitmap.createBitmap(LOGICAL_WIDTH, LOGICAL_HEIGHT, Bitmap.Config.ARGB_8888)
        gameCanvas = Canvas(gameBitmap)

        // Ölçekleme matrisi (Telefona sığdırmak için)
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
                    canvas.drawBitmap(gameBitmap, scaleMatrix, null)
                    holder.unlockCanvasAndPost(canvas)
                }
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

        // 1. Arka plan
        gameCanvas.drawBitmap(bg, 0f, 0f, null)

        // 2. Borular
        // Python: for uPipe, lPipe in zip(upperPipes, lowerPipes):
        for (i in gameState.upperPipes.indices) {
            val u = gameState.upperPipes[i] // (x, y) Pair
            val l = gameState.lowerPipes[i] // (x, y) Pair

            // Üst boru: Koordinat negatiftir (yukarı taşar), pipeTop çizilir
            gameCanvas.drawBitmap(pipeTop, u.first.toFloat(), u.second.toFloat(), null)

            // Alt boru: pipeBottom çizilir
            gameCanvas.drawBitmap(pipeBottom, l.first.toFloat(), l.second.toFloat(), null)
        }

        // 3. Zemin (Python: BASEY = SCREENHEIGHT * 0.79 -> 512 * 0.79 = 404)
        // Zemin boruların üstüne çizilmeli
        val baseY = (LOGICAL_HEIGHT * 0.79f)
        gameCanvas.drawBitmap(base, gameState.baseX.toFloat(), baseY, null)

        // 4. Kuş
        val frame = playerFrames[gameState.playerIndex % playerFrames.size]
        gameCanvas.drawBitmap(frame, gameState.playerX.toFloat(), gameState.playerY.toFloat(), null)
    }

    fun captureFrame(): Bitmap? {
        if (!spritesReady) return null

        // --- KRİTİK DÜZELTME ---
        // Python kodu: image = image[0:288, 0:404]
        // Pygame'de array yapısı (Width, Height) şeklindedir.
        // Yani Python, genişliğin tamamını (288), yüksekliğin ise sadece üst kısmını (0-404) alıyor.
        // Zemini (Base) görüntüden atıyor.

        // Senin 'gameBitmap'in şu an 288x512.
        // Yapay zekaya göndermeden önce bunu 288x404 olarak KIRPMALIYIZ.
        // Eğer bunu yapmazsak, görüntü 84x84'e sıkıştırıldığında kuşun ve boruların boyu basıklaşır,
        // modelin kafası karışır.

        // Sadece üstteki 404 piksellik kısmı al (Zemini at)
        return Bitmap.createBitmap(gameBitmap, 0, 0, 288, 404)
    }

    fun step(action: IntArray): Pair<Float, Boolean> {
        if (!::gameState.isInitialized) return Pair(0f, true)
        return gameState.frameStep(action)
    }
}