package com.alihan.rlproject

import kotlin.math.min
import kotlin.random.Random

class GameState(
    private val screenWidth: Int,   // 288 gelmeli
    private val screenHeight: Int,  // 512 gelmeli
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

    // Python: self.baseShift = IMAGES['base'].get_width() - BACKGROUND_WIDTH
    val baseShift = baseWidth - backgroundWidth

    // Python: PIPEGAPSIZE = 100
    private val gapSize = 100
    // Python: BASEY = SCREENHEIGHT * 0.79
    private val baseY = (screenHeight * 0.79).toInt()

    // Python mantığıyla sabit boşluk pozisyonları
    private val gapYs = listOf(20, 30, 40, 50, 60, 70, 80, 90)

    // Boru koordinatlarını tutan listeler (x, y)
    val upperPipes = mutableListOf<Pair<Int, Int>>()
    val lowerPipes = mutableListOf<Pair<Int, Int>>()

    var pipeVelX = -4
    var playerVelY = 0
    var playerMaxVelY = 10
    var playerMinVelY = -8 // Python kodunda comment -8 diyor ama yukarı çıkış hızı
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

        // Python: newPipe1 = getRandomPipe()
        // self.upperPipes = [{'x': SCREENWIDTH, ...}]
        val pipe1 = getRandomPipe()
        val pipe2 = getRandomPipe()

        // İlk boru tam ekranın sağ ucunda
        addPipeCoords(screenWidth, pipe1)
        // İkinci boru 1.5 ekran genişliği ötede
        addPipeCoords(screenWidth + (screenWidth / 2), pipe2)
    }

    // Python: returns [ {'x':..., 'y': gapY - PIPE_HEIGHT}, {'x':..., 'y': gapY + gapSize} ]
    // Biz burada sadece Y değerlerini (UpperY, LowerY) döndürüyoruz
    private fun getRandomPipe(): Pair<Int, Int> {
        val index = Random.nextInt(gapYs.size)
        var gapY = gapYs[index]

        // Python: gapY += int(BASEY * 0.2)
        gapY += (baseY * 0.2).toInt()

        // Upper Pipe Y: Boşluk başı - Boru Boyu (Negatif çıkması normal, yukarı taşar)
        val upperY = gapY - pipeHeight
        // Lower Pipe Y: Boşluk başı + Boşluk boyu
        val lowerY = gapY + gapSize

        return Pair(upperY, lowerY)
    }

    private fun addPipeCoords(x: Int, yCoords: Pair<Int, Int>) {
        upperPipes.add(Pair(x, yCoords.first))
        lowerPipes.add(Pair(x, yCoords.second))
    }

    fun frameStep(inputActions: IntArray): Pair<Float, Boolean> {
        // Python: pygame.event.pump() equivalent check handled by system
        var reward = 0.1f
        var terminal = false

        if (inputActions[1] == 1) {
            if (playerY > -2 * playerHeight) {
                playerVelY = playerFlapAcc
                playerFlapped = true
            }
        }

        // Skor Kontrolü
        val playerMidPos = playerX + playerWidth / 2
        for (pipe in upperPipes) {
            val pipeMidPos = pipe.first + pipeWidth / 2
            if (pipeMidPos <= playerMidPos && playerMidPos < pipeMidPos + 4) {
                score++
                reward = 1.0f
            }
        }

        // Animasyon ve Zemin
        if ((loopIter + 1) % 3 == 0) {
            playerIndex = (playerIndex + 1) % 4
            if (playerIndex == 3) playerIndex = 1
        }
        loopIter = (loopIter + 1) % 30
        baseX = -((-baseX + 100) % baseShift)

        // Fizik (Player Hareketi)
        if (playerVelY < playerMaxVelY && !playerFlapped) {
            playerVelY += playerAccY
        }
        if (playerFlapped) playerFlapped = false

        // Zemin çarpma sınırı
        val groundLimit = baseY - playerHeight
        playerY += min(playerVelY, groundLimit - playerY)

        if (playerY < 0) playerY = 0

        // Boru Hareketi
        for (i in upperPipes.indices) {
            // Python: uPipe['x'] += self.pipeVelX
            upperPipes[i] = upperPipes[i].copy(first = upperPipes[i].first + pipeVelX)
            lowerPipes[i] = lowerPipes[i].copy(first = lowerPipes[i].first + pipeVelX)
        }

        // Yeni Boru Ekleme
        if (upperPipes.isNotEmpty()) {
            // Python: if 0 < self.upperPipes[0]['x'] < 5
            val firstPipeX = upperPipes[0].first
            if (firstPipeX > 0 && firstPipeX < 5) {
                val newPipeY = getRandomPipe()
                addPipeCoords(screenWidth, newPipeY)
            }

            // Ekrandan çıkanı silme
            // Python: if self.upperPipes[0]['x'] < -PIPE_WIDTH
            if (upperPipes[0].first < -pipeWidth) {
                upperPipes.removeAt(0)
                lowerPipes.removeAt(0)
            }
        }

        // Çarpışma Kontrolü (Crash Check)
        // 1. Zemin
        if (playerY + playerHeight >= baseY - 1) {
            terminal = true
            reward = -1f
            reset()
            return Pair(reward, terminal)
        }

        // 2. Borular
        // Python'daki pixelCollision yerine Rect collision kullanıyoruz (Daha hızlı)
        // Python: pygame.Rect(uPipe['x'], uPipe['y'], PIPE_WIDTH, PIPE_HEIGHT)
        val pRect = android.graphics.Rect(playerX, playerY, playerX + playerWidth, playerY + playerHeight)

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