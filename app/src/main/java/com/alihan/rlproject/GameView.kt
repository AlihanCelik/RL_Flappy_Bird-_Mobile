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

    private var thread: Thread? = null
    private val running = AtomicBoolean(false)

    private lateinit var gameState: GameState
    private var spritesReady = false

    // sprite bitmaps
    private lateinit var bg: Bitmap
    private lateinit var base: Bitmap
    private lateinit var playerFrames: Array<Bitmap>
    private lateinit var pipeTop: Bitmap
    private lateinit var pipeBottom: Bitmap

    init {
        holder.addCallback(this)
    }

    fun initGame(screenW: Int, screenH: Int) {
        // load assets (use helper)
        val assets = AssetLoader(context)
        bg = assets.loadBitmap("sprites/background-black.png")
        base = assets.loadBitmap("sprites/base.png")
        playerFrames = arrayOf(
            assets.loadBitmap("sprites/redbird-upflap.png"),
            assets.loadBitmap("sprites/redbird-midflap.png"),
            assets.loadBitmap("sprites/redbird-downflap.png")
        )
        pipeTop = assets.loadBitmap("sprites/pipe-green.png") // rotate when draw
        pipeBottom = assets.loadBitmap("sprites/pipe-green.png")
        spritesReady = true

        gameState = GameState(
            screenW, screenH,
            playerFrames[0].width, playerFrames[0].height,
            pipeTop.width, pipeTop.height,
            bg.width, base.width
        )
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        running.set(true)
        thread = Thread {
            while (running.get()) {
                val canvas = holder.lockCanvas()
                if (canvas != null) {
                    render(canvas)
                    holder.unlockCanvasAndPost(canvas)
                }
                try { Thread.sleep(16) } catch (e: InterruptedException) {}
            }
        }
        thread?.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) {
        running.set(false)
        thread?.join()
    }

    private fun render(canvas: Canvas) {
        if (!spritesReady) return
        // draw background
        canvas.drawBitmap(bg, 0f, 0f, null)

        // draw pipes
        for (i in gameState.upperPipes.indices) {
            val u = gameState.upperPipes[i]
            val l = gameState.lowerPipes[i]
            // draw top pipe (rotated)
            val topRect = Rect(u.first, u.second, u.first + pipeTop.width, u.second + pipeTop.height)
            val matrix = Matrix()
            matrix.postRotate(180f)
            val rotated = Bitmap.createBitmap(pipeTop, 0, 0, pipeTop.width, pipeTop.height, matrix, false)
            canvas.drawBitmap(rotated, u.first.toFloat(), u.second.toFloat(), null)
            // draw bottom
            canvas.drawBitmap(pipeBottom, l.first.toFloat(), l.second.toFloat(), null)
        }

        // draw base
        canvas.drawBitmap(base, gameState.baseX.toFloat(), (height * 0.79f), null)

        // draw player
        val frame = playerFrames[gameState.playerIndex % playerFrames.size]
        canvas.drawBitmap(frame, gameState.playerX.toFloat(), gameState.playerY.toFloat(), null)
    }

    // Produce a Bitmap of current frame for model inference
    fun captureFrame(): Bitmap? {
        // Render to an offscreen bitmap
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        render(c)
        return bmp
    }

    // convenience: call GameState.frameStep with action
    fun step(action: IntArray): Pair<Float, Boolean> {
        return gameState.frameStep(action)
    }

}