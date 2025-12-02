package com.alihan.rlproject

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

    var playerX = (screenWidth * 0.2).toInt()
    var playerY = ((screenHeight - playerHeight) / 2)
    var baseX = 0

    val baseShift = baseWidth - backgroundWidth
    private val gapSize = 100
    private val baseY = (screenHeight * 0.79).toInt()

    private val gapYs = listOf(20, 30, 40, 50, 60, 70, 80, 90)

    val upperPipes = mutableListOf<Pair<Int, Int>>()
    val lowerPipes = mutableListOf<Pair<Int, Int>>()

    var pipeVelX = -4
    var playerVelY = 0
    var playerMaxVelY = 10
    var playerAccY = 1
    var playerFlapAcc = -9
    var playerFlapped = false

    private val HITBOX_INSET = 6

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

        val pipe1 = getRandomPipe()
        val pipe2 = getRandomPipe()

        addPipeCoords(screenWidth, pipe1)
        addPipeCoords(screenWidth + (screenWidth / 2), pipe2)
    }

    private fun getRandomPipe(): Pair<Int, Int> {
        val index = Random.nextInt(gapYs.size)
        var gapY = gapYs[index]
        gapY += (baseY * 0.2).toInt()

        val upperY = gapY - pipeHeight
        val lowerY = gapY + gapSize
        return Pair(upperY, lowerY)
    }

    private fun addPipeCoords(x: Int, yCoords: Pair<Int, Int>) {
        upperPipes.add(Pair(x, yCoords.first))
        lowerPipes.add(Pair(x, yCoords.second))
    }

    fun frameStep(inputActions: IntArray): Pair<Float, Boolean> {
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

        val groundLimit = baseY - playerHeight
        playerY += min(playerVelY, groundLimit - playerY)
        if (playerY < 0) playerY = 0

        for (i in upperPipes.indices) {
            upperPipes[i] = upperPipes[i].copy(first = upperPipes[i].first + pipeVelX)
            lowerPipes[i] = lowerPipes[i].copy(first = lowerPipes[i].first + pipeVelX)
        }

        if (upperPipes.isNotEmpty()) {
            val firstPipeX = upperPipes[0].first
            if (firstPipeX > 0 && firstPipeX < 5) {
                val newPipeY = getRandomPipe()
                addPipeCoords(screenWidth, newPipeY)
            }

            if (upperPipes[0].first < -pipeWidth) {
                upperPipes.removeAt(0)
                lowerPipes.removeAt(0)
            }
        }


        if (playerY + playerHeight >= baseY - 1) {
            terminal = true
            reward = -1f
            reset()
            return Pair(reward, terminal)
        }

        val pRect = android.graphics.Rect(
            playerX + HITBOX_INSET,
            playerY + HITBOX_INSET,
            playerX + playerWidth - HITBOX_INSET,
            playerY + playerHeight - HITBOX_INSET
        )

        for (i in upperPipes.indices) {
            val ux = upperPipes[i].first
            val uy = upperPipes[i].second
            val lx = lowerPipes[i].first
            val ly = lowerPipes[i].second

            val uRect = android.graphics.Rect(ux, uy, ux + pipeWidth, uy + pipeHeight)
            val lRect = android.graphics.Rect(lx, ly, lx + pipeWidth, ly + pipeHeight)

            if (android.graphics.Rect.intersects(pRect, uRect) ||
                android.graphics.Rect.intersects(pRect, lRect)) {
                terminal = true
                reward = -1f
                reset()
                return Pair(reward, terminal)
            }
        }

        return Pair(reward, terminal)
    }
}