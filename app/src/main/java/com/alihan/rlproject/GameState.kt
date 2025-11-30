package com.alihan.rlproject

import android.graphics.Rect
import kotlin.math.min
import kotlin.random.Random

class GameState(
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val playerWidth: Int,
    private val playerHeight: Int,
    private val pipeWidth: Int,
    private val pipeHeight: Int,
    private val backgroundWidth: Int,
    private val baseWidth: Int
) {
    var score = 0
    var playerIndex = 0
    var loopIter = 0

    // Orijinal Flappy Bird oranları
    var playerX = (screenWidth * 0.2).toInt()
    var playerY = ((screenHeight - playerHeight) / 2)
    var baseX = 0
    // baseShift pozitif olmalı
    val baseShift = if (baseWidth > backgroundWidth) baseWidth - backgroundWidth else baseWidth

    private val gapSize = 100 // Python ortamındaki gap size ile aynı olmalı

    // Y pozisyonları (sanal çözünürlüğe göre)
    // 288x512 ekran için mantıklı y aralıkları
    private val minY = (screenHeight * 0.2).toInt()
    private val maxY = (screenHeight * 0.6).toInt() - gapSize

    val upperPipes = mutableListOf<Pair<Int, Int>>() // x,y
    val lowerPipes = mutableListOf<Pair<Int, Int>>()

    var pipeVelX = -4
    var playerVelY = 0
    var playerMaxVelY = 10
    var playerMinVelY = -8
    var playerAccY = 1
    var playerFlapAcc = -9
    var playerFlapped = false

    init {
        reset()
    }

    fun reset() {
        score = 0
        playerIndex = 0
        loopIter = 0
        playerX = (screenWidth * 0.2).toInt()
        playerY = ((screenHeight - playerHeight) / 2)
        baseX = 0
        playerVelY = 0
        upperPipes.clear()
        lowerPipes.clear()

        val startX = screenWidth + 100
        val new1 = getRandomPipe(startX)
        val new2 = getRandomPipe(startX + (screenWidth / 2) + (pipeWidth / 2))

        addPipe(new1)
        addPipe(new2)
    }

    private fun addPipe(pos: Pair<Int, Int>) {
        val upperY = pos.first - gapSize - pipeHeight
        val lowerY = pos.first

        val x = pos.second

        upperPipes.add(Pair(x, upperY))
        lowerPipes.add(Pair(x, lowerY))
    }

    fun frameStep(inputActions: IntArray): Pair<Float, Boolean> {
        if (inputActions.sum() != 1) {
        }

        var reward = 0.1f
        var terminal = false

        if (inputActions[1] == 1) {
            if (playerY > -2 * playerHeight) {
                playerVelY = playerFlapAcc
                playerFlapped = true
            }
        }

        val playerMidPos = playerX + playerWidth / 2
        for (pipe in upperPipes) {
            val pipeMidPos = pipe.first + pipeWidth / 2
            if (pipeMidPos <= playerMidPos && playerMidPos < pipeMidPos + 4) {
                score++
                reward = 1.0f
            }
        }

        if ((loopIter + 1) % 3 == 0) {
            playerIndex = (playerIndex + 1) % 4
            if (playerIndex == 3) playerIndex = 1
        }
        loopIter = (loopIter + 1) % 30

        baseX = -((-baseX + 100) % baseShift)

        if (playerVelY < playerMaxVelY && !playerFlapped) {
            playerVelY += playerAccY
        }
        if (playerFlapped) playerFlapped = false

        val groundY = (screenHeight * 0.79).toInt()
        playerY += min(playerVelY, groundY - playerY - playerHeight)

        if (playerY < 0) playerY = 0

        for (i in upperPipes.indices) {
            upperPipes[i] = Pair(upperPipes[i].first + pipeVelX, upperPipes[i].second)
            lowerPipes[i] = Pair(lowerPipes[i].first + pipeVelX, lowerPipes[i].second)
        }

        if (upperPipes.isNotEmpty()) {
            val firstPipeX = upperPipes[0].first
            if (firstPipeX < -pipeWidth) {
                upperPipes.removeAt(0)
                lowerPipes.removeAt(0)
            }

            val lastPipeX = upperPipes.last().first
            if (lastPipeX < screenWidth - (screenWidth / 2) - (pipeWidth / 2)) {
                val newPipe = getRandomPipe(screenWidth + 10)
                addPipe(newPipe)
            }
        } else {
            val newPipe = getRandomPipe(screenWidth + 10)
            addPipe(newPipe)
        }

        if (playerY + playerHeight >= groundY - 1) {
            terminal = true
            reward = -1f
            reset()
            return Pair(reward, terminal)
        }

        val playerRect = Rect(playerX, playerY, playerX + playerWidth, playerY + playerHeight)

        for (i in upperPipes.indices) {
            val u = upperPipes[i]
            val l = lowerPipes[i]

            val uRect = Rect(u.first, u.second, u.first + pipeWidth, u.second + pipeHeight)
            val lRect = Rect(l.first, l.second, l.first + pipeWidth, l.second + pipeHeight)

            if (Rect.intersects(playerRect, uRect) || Rect.intersects(playerRect, lRect)) {
                terminal = true
                reward = -1f
                reset()
                return Pair(reward, terminal)
            }
        }

        return Pair(reward, terminal)
    }
    private fun getRandomPipe(xPos: Int): Pair<Int, Int> {
        val gapY = Random.nextInt(minY + gapSize, maxY)
        return Pair(gapY, xPos)
    }
}