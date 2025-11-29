package com.alihan.rlproject

import kotlin.math.min
import kotlin.random.Random

data class Pipe(val x: Int, val y: Int)

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
    private val gapYs = listOf(20, 30, 40, 50, 60, 70, 80, 90)

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
        upperPipes.clear()
        lowerPipes.clear()
        val new1 = getRandomPipe()
        val new2 = getRandomPipe()
        upperPipes.add(Pair(screenWidth, new1.first))
        upperPipes.add(Pair(screenWidth + screenWidth / 2, new2.first))
        lowerPipes.add(Pair(screenWidth, new1.second))
        lowerPipes.add(Pair(screenWidth + screenWidth / 2, new2.second))
    }

    /**
     * inputActions: IntArray of size 2. [1,0] = nothing, [0,1] = flap
     * returns Triple(imageBitmapNeededFlag:Boolean, reward:Float, terminal:Boolean)
     * We won't return image here — rendering handled separately.
     */
    fun frameStep(inputActions: IntArray): Pair<Float, Boolean> {
        if (inputActions.sum() != 1) throw IllegalArgumentException("Multiple input actions!")

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
            playerIndex = (playerIndex + 1) % 4 // simple cycle (0,1,2,1 -> approximate)
            if (playerIndex == 3) playerIndex = 1
        }
        loopIter = (loopIter + 1) % 30
        baseX = -(( -baseX + 100) % baseShift)

        if (playerVelY < playerMaxVelY && !playerFlapped) {
            playerVelY += playerAccY
        }
        if (playerFlapped) playerFlapped = false

        playerY += min(playerVelY, (screenHeight * 0.79 - playerY - playerHeight).toInt())
        if (playerY < 0) playerY = 0

        // move pipes
        for (i in upperPipes.indices) {
            upperPipes[i] = Pair(upperPipes[i].first + pipeVelX, upperPipes[i].second)
            lowerPipes[i] = Pair(lowerPipes[i].first + pipeVelX, lowerPipes[i].second)
        }

        // add new pipe
        if (upperPipes.isNotEmpty()) {
            if (0 < upperPipes[0].first && upperPipes[0].first < 5) {
                val newPipe = getRandomPipe()
                upperPipes.add(Pair(screenWidth, newPipe.first))
                lowerPipes.add(Pair(screenWidth, newPipe.second))
            }
            // remove if out
            if (upperPipes[0].first < -pipeWidth) {
                upperPipes.removeAt(0)
                lowerPipes.removeAt(0)
            }
        }

        // collision check - simple AABB check here; for pixel-perfect you'd port hitmask logic
        if (playerY + playerHeight >= (screenHeight * 0.79).toInt() - 1) {
            terminal = true
            reset()
            reward = -1f
            return Pair(reward, terminal)
        }

        // pixel-perfect collision omitted here — implement via bitmaps & masks if needed.

        return Pair(reward, terminal)
    }

    private fun getRandomPipe(): Pair<Int, Int> {
        val index = Random.nextInt(0, gapYs.size)
        var gapY = gapYs[index]
        gapY += (screenHeight * 0.2).toInt()
        val pipeX = screenWidth + 10
        return Pair(gapY - pipeHeight, gapY + gapSize)
    }
}