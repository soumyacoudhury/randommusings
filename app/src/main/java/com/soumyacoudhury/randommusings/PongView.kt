package com.soumyacoudhury.randommusings

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.*

class PongView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private var gameThread: Thread? = null
    @Volatile private var running = false
    @Volatile private var paused = false

    private var canvasWidth = 1
    private var canvasHeight = 1

    // Game objects (sizes will be computed when we know view size)
    private var paddleWidth = 24f
    private var paddleHeight = 160f
    private var ballRadius = 12f

    private val leftPaddle = RectF()
    private val rightPaddle = RectF()
    private val ball = PointF()
    private var ballV = PointF()
    private var ballSpeed = 8f

    private var playerScore = 0
    private var computerScore = 0

    private val bgPaint = Paint().apply { color = Color.rgb(15,23,36) }
    private val elementPaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val netPaint = Paint().apply { color = Color.argb(60, 255,255,255) }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val overlayPaint = Paint().apply { color = Color.argb(160, 0,0,0) }

    // Touch handling
    private var activePointerId = INVALID_POINTER
    private var lastTouchY = 0f
    private var touchMoved = false

    // CPU parameters
    private var cpuMaxSpeed = 6f

    companion object {
        private const val INVALID_POINTER = -1
        private const val TARGET_FPS = 60
    }

    init {
        holder.addCallback(this)
        isFocusable = true
        keepScreenOn = true
    }

    private fun resetSizes() {
        // Layout based sizes
        paddleWidth = canvasWidth * 0.03f
        paddleHeight = canvasHeight * 0.28f
        ballRadius = (canvasWidth + canvasHeight) * 0.0065f
        cpuMaxSpeed = canvasHeight * 0.0085f
        ballSpeed = canvasWidth * 0.0105f

        // Place paddles
        leftPaddle.set(
            canvasWidth * 0.03f,
            (canvasHeight - paddleHeight) / 2f,
            canvasWidth * 0.03f + paddleWidth,
            (canvasHeight - paddleHeight) / 2f + paddleHeight
        )
        rightPaddle.set(
            canvasWidth * 0.97f - paddleWidth,
            (canvasHeight - paddleHeight) / 2f,
            canvasWidth * 0.97f,
            (canvasHeight - paddleHeight) / 2f + paddleHeight
        )

        serveBall(ifRight = (Math.random() > 0.5))
    }

    private fun serveBall(ifRight: Boolean) {
        ball.set(canvasWidth / 2f, canvasHeight / 2f)
        val angle = (Math.random() * Math.PI / 3.0) - (Math.PI / 6.0) // -30..30deg
        val vx = (ifRight then 1f else -1f) * (ballSpeed * Math.cos(angle)).toFloat()
        val vy = (ballSpeed * Math.sin(angle)).toFloat()
        ballV.set(vx, vy)
    }

    // small infix helper
    private infix fun Boolean.then(v: Float) = if (this) v else -v

    override fun surfaceCreated(holder: SurfaceHolder) {
        // start thread
        running = true
        paused = false
        gameThread = Thread(gameLoop, "PongThread").also { it.start() }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        canvasWidth = width
        canvasHeight = height
        resetSizes()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        // stop thread cleanly
        running = false
        var retry = true
        while (retry) {
            try {
                gameThread?.join()
                retry = false
            } catch (e: InterruptedException) { /* retry */ }
        }
    }

    fun resume() {
        if (!running) {
            running = true
            gameThread = Thread(gameLoop, "PongThread").also { it.start() }
        }
        paused = false
    }

    fun pause() {
        paused = true
    }

    fun shutdown() {
        running = false
        try {
            gameThread?.join(500)
        } catch (e: InterruptedException) { }
    }

    private val gameLoop = Runnable {
        var lastTime = System.nanoTime()
        val nsPerFrame = 1_000_000_000L / TARGET_FPS
        while (running) {
            val now = System.nanoTime()
            val elapsed = now - lastTime
            if (elapsed >= nsPerFrame) {
                val dt = elapsed / 1_000_000_000f
                lastTime = now
                if (!paused) update(dt)
                drawFrame()
            } else {
                val sleepMs = (nsPerFrame - elapsed) / 1_000_000L
                if (sleepMs > 0) {
                    try { Thread.sleep(sleepMs) } catch (_: InterruptedException) { }
                }
            }
        }
    }

    private fun update(dt: Float) {
        // CPU paddle: move toward ball center
        val targetY = ball.y - paddleHeight / 2f
        val dy = targetY - rightPaddle.top
        if (Math.abs(dy) > 2f) {
            val dir = if (dy > 0) 1 else -1
            val move = dir * Math.min(cpuMaxSpeed, Math.abs(dy))
            rightPaddle.offset(0f, move)
            // clamp
            if (rightPaddle.top < 0f) {
                rightPaddle.offsetTo(rightPaddle.left, 0f)
            } else if (rightPaddle.bottom > canvasHeight.toFloat()) {
                rightPaddle.offsetTo(rightPaddle.left, canvasHeight - paddleHeight)
            }
        }

        // Ball movement
        ball.x += ballV.x
        ball.y += ballV.y

        // Wall collisions
        if (ball.y - ballRadius <= 0f) {
            ball.y = ballRadius
            ballV.y = -ballV.y
        } else if (ball.y + ballRadius >= canvasHeight.toFloat()) {
            ball.y = canvasHeight - ballRadius
            ballV.y = -ballV.y
        }

        // Left paddle collision
        if (ball.x - ballRadius <= leftPaddle.right) {
            if (ball.y >= leftPaddle.top && ball.y <= leftPaddle.bottom) {
                // compute bounce angle based on hit location
                val relative = (leftPaddle.top + paddleHeight / 2f) - ball.y
                val normalized = (relative / (paddleHeight / 2f)).coerceIn(-1f, 1f)
                val maxBounce = Math.toRadians(75.0)
                val bounceAngle = (normalized * maxBounce).toFloat()
                val direction = 1f
                // increase speed a touch
                val speed = (Math.hypot(ballV.x.toDouble(), ballV.y.toDouble()) * 1.03).toFloat().coerceAtMost(canvasWidth * 0.02f)
                ballV.x = direction * speed * Math.cos(bounceAngle.toDouble()).toFloat()
                ballV.y = -speed * Math.sin(bounceAngle.toDouble()).toFloat()
                ball.x = leftPaddle.right + ballRadius + 0.5f
            }
        }

        // Right paddle collision
        if (ball.x + ballRadius >= rightPaddle.left) {
            if (ball.y >= rightPaddle.top && ball.y <= rightPaddle.bottom) {
                val relative = (rightPaddle.top + paddleHeight / 2f) - ball.y
                val normalized = (relative / (paddleHeight / 2f)).coerceIn(-1f, 1f)
                val maxBounce = Math.toRadians(75.0)
                val bounceAngle = (normalized * maxBounce).toFloat()
                val direction = -1f
                val speed = (Math.hypot(ballV.x.toDouble(), ballV.y.toDouble()) * 1.03).toFloat().coerceAtMost(canvasWidth * 0.02f)
                ballV.x = direction * speed * Math.cos(bounceAngle.toDouble()).toFloat()
                ballV.y = -speed * Math.sin(bounceAngle.toDouble()).toFloat()
                ball.x = rightPaddle.left - ballRadius - 0.5f
            }
        }

        // Scoring
        if (ball.x + ballRadius < 0f) {
            // computer scores
            computerScore++
            serveBall(ifRight = true)
        } else if (ball.x - ballRadius > canvasWidth.toFloat()) {
            // player scores
            playerScore++
            serveBall(ifRight = false)
        }
    }

    private fun drawFrame() {
        val c = try { holder.lockCanvas() } catch (e: Exception) { null }
        if (c == null) return
        try {
            // clear background
            c.drawColor(Color.BLACK)
            c.drawRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), bgPaint)

            // draw net
            val step = canvasHeight / 20f
            val netW = canvasWidth * 0.006f
            for (y in 0 until canvasHeight step step.toInt()) {
                c.drawRect(
                    canvasWidth / 2f - netW / 2f, y.toFloat(),
                    canvasWidth / 2f + netW / 2f, (y + step / 2f),
                    netPaint
                )
            }

            // draw paddles
            elementPaint.color = Color.WHITE
            c.drawRoundRect(leftPaddle, paddleWidth / 4f, paddleWidth / 4f, elementPaint)
            c.drawRoundRect(rightPaddle, paddleWidth / 4f, paddleWidth / 4f, elementPaint)

            // draw ball
            c.drawCircle(ball.x, ball.y, ballRadius, elementPaint)

            // draw scores
            textPaint.textSize = canvasHeight * 0.09f
            c.drawText("$playerScore", canvasWidth * 0.25f, canvasHeight * 0.12f + textPaint.textSize / 3f, textPaint)
            c.drawText("$computerScore", canvasWidth * 0.75f, canvasHeight * 0.12f + textPaint.textSize / 3f, textPaint)

            // paused overlay
            if (paused) {
                c.drawRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), overlayPaint)
                textPaint.textSize = canvasHeight * 0.06f
                c.drawText("Paused — tap to resume", canvasWidth / 2f, canvasHeight / 2f, textPaint)
            }
        } finally {
            try { holder.unlockCanvasAndPost(c) } catch (_: Exception) { }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                lastTouchY = event.getY(0)
                touchMoved = false
                // If tap on right half, toggle pause; if left half, begin control
                val x = event.getX(0)
                val gX = x
                if (gX > width / 2f) {
                    // toggle pause/resume quickly
                    paused = !paused
                } else {
                    // move paddle to touch
                    val y = event.getY(0)
                    moveLeftPaddleTo(y)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val idx = event.actionIndex
                val x = event.getX(idx)
                val y = event.getY(idx)
                if (x <= width / 2f) {
                    activePointerId = event.getPointerId(idx)
                    lastTouchY = y
                    moveLeftPaddleTo(y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = event.findPointerIndex(activePointerId)
                if (idx >= 0) {
                    val y = event.getY(idx)
                    if (Math.abs(y - lastTouchY) > 4f) touchMoved = true
                    moveLeftPaddleTo(y)
                    lastTouchY = y
                } else {
                    // fallback: move with primary finger
                    val y = event.getY(0)
                    moveLeftPaddleTo(y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = INVALID_POINTER
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val idx = event.actionIndex
                val id = event.getPointerId(idx)
                if (id == activePointerId) {
                    // switch to another pointer if possible
                    val newIndex = if (idx == 0) 1 else 0
                    if (newIndex < event.pointerCount) {
                        activePointerId = event.getPointerId(newIndex)
                        lastTouchY = event.getY(newIndex)
                    } else activePointerId = INVALID_POINTER
                }
            }
        }
        return true
    }

    private fun moveLeftPaddleTo(screenY: Float) {
        // Convert screen Y into paddle top so paddle center aligns with touch
        val newTop = (screenY / height) * canvasHeight - paddleHeight / 2f
        val clampedTop = newTop.coerceIn(0f, canvasHeight - paddleHeight)
        leftPaddle.offsetTo(leftPaddle.left, clampedTop)
    }
}