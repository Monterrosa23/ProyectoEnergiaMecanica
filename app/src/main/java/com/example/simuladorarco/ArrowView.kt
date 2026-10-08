package com.example.simuladorarco

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Vista personalizada que dibuja:
 *  - La catapulta tensada con el proyectil (modo CATAPULT)
 *  - La trayectoria parabólica y el proyectil en vuelo (modo FLIGHT)
 *
 * Toda la cinemática usa las ecuaciones reales del movimiento parabólico
 * (sin fricción del aire):
 *   x(t) = v·cos(θ)·t
 *   y(t) = v·sin(θ)·t − ½·g·t²
 */
class ArrowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode { CATAPULT, FLIGHT }

    // ---- Estado de la simulación (lo fija MainActivity) ----
    var mode: Mode = Mode.CATAPULT
    var drawLength: Float = 0.5f      // ángulo de tensión (0..1, donde 1 = máxima torsión)
    var flightProgress: Float = 0f    // 0..1 durante el vuelo
    var flightTime: Float = 0f        // duración total del vuelo en segundos
    var maxHeight: Float = 0f         // altura máxima esperada (metros)
    var velocity: Float = 0f          // velocidad inicial (m/s)
    var angleDeg: Float = 45f         // ángulo de lanzamiento (grados)
    var projectileSize: Float = 1f    // tamaño del proyectil (1 = normal)

    private val g = 9.81f             // gravedad (m/s²)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 30f
        isFakeBoldText = true
    }

    // Geometría del escenario
    private val groundY: Float get() = height - 70f
    private val originX: Float get() = 110f
    private val scale: Float get() = (height - 300f) / max(maxHeight * 1.2f, 1f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Cielo
        canvas.drawColor(Color.rgb(179, 217, 255))

        // Suelo
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(101, 67, 33)
        canvas.drawRect(0f, groundY, width.toFloat(), height.toFloat(), paint)
        paint.color = Color.rgb(76, 153, 0)
        canvas.drawRect(0f, groundY, width.toFloat(), groundY + 14f, paint)

        when (mode) {
            Mode.CATAPULT -> drawCatapult(canvas)
            Mode.FLIGHT -> drawFlight(canvas)
        }
    }

    // -----------------------------------------------------------------
    // Dibuja la catapulta tensada con el proyectil
    // -----------------------------------------------------------------
    private fun drawCatapult(canvas: Canvas) {
        val baseX = originX + 100f
        val baseY = groundY
        val armLength = 150f


        // Base de la catapulta (más grande)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(101, 67, 33)
        canvas.drawRect(baseX - 60f, baseY - 25f, baseX + 60f, baseY, paint)

        // Ruedas
        paint.color = Color.rgb(60, 40, 20)
        canvas.drawCircle(baseX - 40f, baseY - 8f, 18f, paint)
        canvas.drawCircle(baseX + 40f, baseY - 8f, 18f, paint)

        // Eje de las ruedas
        paint.color = Color.rgb(40, 25, 10)
        canvas.drawCircle(baseX - 40f, baseY - 8f, 6f, paint)
        canvas.drawCircle(baseX + 40f, baseY - 8f, 6f, paint)

        // Brazo de la catapulta (tensado hacia atrás y arriba)
        val armAngle = 120f + drawLength * 60f  // ángulo del brazo (más vertical)
        val armRad = Math.toRadians(armAngle.toDouble())
        val armEndX = baseX + (armLength * cos(armRad)).toFloat()
        val armEndY = baseY - 25f - (armLength * sin(armRad)).toFloat()

        // Brazo (más grueso)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 14f
        paint.color = Color.rgb(139, 90, 43)
        canvas.drawLine(baseX, baseY - 25f, armEndX, armEndY, paint)

        // Cuerda de tensión (desde el extremo del brazo hacia atrás)
        paint.strokeWidth = 3f
        paint.color = Color.rgb(60, 40, 20)


        // Proyectil (bola) en el extremo del brazo
        val ballRadius = 25f * projectileSize
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(80, 80, 80)
        canvas.drawCircle(armEndX, armEndY - ballRadius, ballRadius, paint)

        // Brillo de la bola
        paint.color = Color.rgb(150, 150, 150)
        canvas.drawCircle(armEndX - ballRadius * 0.3f, armEndY - ballRadius * 1.3f, ballRadius * 0.3f, paint)

        // Etiqueta de la tensión
        textPaint.textSize = 26f
        canvas.drawText("φ = %.2f".format(drawLength), armEndX - 80f, armEndY - 50f, textPaint)
    }

    // -----------------------------------------------------------------
    // Dibuja la trayectoria punteada y el proyectil en pleno vuelo
    // -----------------------------------------------------------------
    private fun drawFlight(canvas: Canvas) {
        val theta = Math.toRadians(angleDeg.toDouble())
        val vx = velocity * cos(theta).toFloat()
        val vy0 = velocity * sin(theta).toFloat()

        // Trayectoria prevista (línea punteada)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.rgb(90, 90, 90)
        paint.pathEffect = DashPathEffect(floatArrayOf(12f, 10f), 0f)
        val traj = Path()
        val steps = 60
        for (i in 0..steps) {
            val t = flightTime * i / steps
            val x = originX + vx * t * scale
            val y = groundY - (vy0 * t - 0.5f * g * t * t) * scale
            if (i == 0) traj.moveTo(x, y) else traj.lineTo(x, y)
        }
        canvas.drawPath(traj, paint)
        paint.pathEffect = null

        // Posición actual del proyectil
        val t = flightProgress * flightTime
        val x = originX + vx * t * scale
        val y = groundY - (vy0 * t - 0.5f * g * t * t) * scale

        // Dibujar el proyectil (bola)
        val ballRadius = 25f * projectileSize
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(80, 80, 80)
        canvas.drawCircle(x, y, ballRadius, paint)

        // Brillo de la bola
        paint.color = Color.rgb(150, 150, 150)
        canvas.drawCircle(x - ballRadius * 0.3f, y - ballRadius * 0.3f, ballRadius * 0.3f, paint)

        // Marcador de altura máxima
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.rgb(200, 30, 30)
        val apexX = originX + vx * (vy0 / g) * scale
        val apexY = groundY - maxHeight * scale
        canvas.drawLine(apexX, groundY, apexX, apexY, paint)
        textPaint.textSize = 24f
        canvas.drawText("h máx = %.2f m".format(maxHeight), apexX + 8f, apexY + 24f, textPaint)
    }
}
