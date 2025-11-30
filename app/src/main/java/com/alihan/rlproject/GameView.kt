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

    // Sanal Çözünürlük (Orijinal Flappy Bird Boyutları)
    private val LOGICAL_WIDTH = 288
    private val LOGICAL_HEIGHT = 512

    // Off-screen buffer (Çizimi buraya yapıp, ekrana büyüteceğiz)
    private lateinit var gameBitmap: Bitmap
    private lateinit var gameCanvas: Canvas
    private lateinit var scaleMatrix: Matrix

    // Sprite bitmaps
    private lateinit var bg: Bitmap
    private lateinit var base: Bitmap
    private lateinit var playerFrames: Array<Bitmap>
    private lateinit var pipeTop: Bitmap
    private lateinit var pipeBottom: Bitmap

    init { holder.addCallback(this) }

    fun initGame(viewWidth: Int, viewHeight: Int) {
        val assets = AssetLoader(context)

        // Assetleri yükle
        bg = assets.loadBitmap("sprites/background-black.png")
        base = assets.loadBitmap("sprites/base.png")
        playerFrames = arrayOf(
            assets.loadBitmap("sprites/redbird-upflap.png"),
            assets.loadBitmap("sprites/redbird-midflap.png"),
            assets.loadBitmap("sprites/redbird-downflap.png")
        )

        val rawPipe = assets.loadBitmap("sprites/pipe-green.png")
        pipeBottom = rawPipe // Alt boru düz

        // Üst boruyu ŞİMDİ çevir (Render döngüsünde yapma, performans öldürür)
        val matrix = Matrix()
        matrix.postRotate(180f)
        pipeTop = Bitmap.createBitmap(rawPipe, 0, 0, rawPipe.width, rawPipe.height, matrix, false)

        // Sanal ekran buffer'ını oluştur
        gameBitmap = Bitmap.createBitmap(LOGICAL_WIDTH, LOGICAL_HEIGHT, Bitmap.Config.ARGB_8888)
        gameCanvas = Canvas(gameBitmap)

        // Ekrana sığdırmak için ölçekleme matrisi hesapla
        val sx = viewWidth.toFloat() / LOGICAL_WIDTH
        val sy = viewHeight.toFloat() / LOGICAL_HEIGHT
        scaleMatrix = Matrix()
        scaleMatrix.setScale(sx, sy)

        spritesReady = true

        // GameState'e SANAL boyutları gönderiyoruz, gerçek ekran boyutunu değil
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
                    drawGame() // Buffer'a çiz
                    // Buffer'ı ekrana scale ederek çiz
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

    // Tüm çizim işlemleri gameCanvas (288x512) üzerine yapılır
    private fun drawGame() {
        if (!spritesReady) return

        // Arka plan
        gameCanvas.drawBitmap(bg, 0f, 0f, null)

        // Borular
        for (i in gameState.upperPipes.indices) {
            val u = gameState.upperPipes[i]
            val l = gameState.lowerPipes[i]

            // Önceden çevrilmiş pipeTop kullanılıyor
            gameCanvas.drawBitmap(pipeTop, u.first.toFloat(), u.second.toFloat(), null)
            gameCanvas.drawBitmap(pipeBottom, l.first.toFloat(), l.second.toFloat(), null)
        }

        // Zemin
        gameCanvas.drawBitmap(base, gameState.baseX.toFloat(), (LOGICAL_HEIGHT * 0.79f), null)

        // Kuş
        val frame = playerFrames[gameState.playerIndex % playerFrames.size]
        // Kuşun dönüş açısını (rotasyonunu) eklemek isterseniz buraya matrix ekleyebilirsiniz
        gameCanvas.drawBitmap(frame, gameState.playerX.toFloat(), gameState.playerY.toFloat(), null)
    }

    // AI için frame yakalama
    fun captureFrame(): Bitmap? {
        if (!spritesReady) return null
        // Doğrudan sanal buffer'ı (288x512) döndür, model bunu daha iyi işler.
        // Copy oluşturuyoruz çünkü thread çakışması olmasın.
        return gameBitmap.copy(Bitmap.Config.ARGB_8888, false)
    }

    fun step(action: IntArray): Pair<Float, Boolean> {
        if (!::gameState.isInitialized) return Pair(0f, true)
        return gameState.frameStep(action)
    }
}