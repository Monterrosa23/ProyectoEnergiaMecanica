package com.example.simuladorarco

import android.animation.ValueAnimator
import android.os.Bundle
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.simuladorarco.databinding.ActivityMainBinding
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Simulador de conservación de la energía mecánica con catapulta:
 *
 *  1) Al tensar la catapulta se almacena energía potencial elástica:  EPE = ½·k·x²
 *  2) Al soltar, toda esa energía se convierte en energía cinética:  EC = ½·m·v²
 *     →  v = x·√(k/m)
 *  3) En el vuelo (sin fricción del aire) la EC se transforma en energía
 *     potencial gravitatoria EPG = m·g·h hasta detenerse en el punto más alto:
 *     ½·m·v²·sin²θ = m·g·h   →   h = (v·sinθ)² / (2·g)
 *
 *  Nota: El ángulo de lanzamiento (θ) se calcula automáticamente del ángulo de
 *  tensión (φ) para simular el comportamiento real de una catapulta.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // ---- Parámetros de la simulación ----
    private var k = 10f            // constante elástica (N/m)
    private var x = 0.5f           // ángulo de tensión (0..1, donde 1 = máxima torsión)
    private var masa = 0.10f       // masa del proyectil (kg)

    // ---- Resultados calculados ----
    private var epe = 0f           // energía potencial elástica (J)
    private var velocidad = 0f     // velocidad inicial (m/s)
    private var alturaMax = 0f     // altura máxima (m)
    private var tiempoVuelo = 0f   // tiempo total de vuelo (s)
    private var anguloLanzamiento = 45f  // ángulo de lanzamiento (calculado automáticamente)

    private var animator: ValueAnimator? = null
    private val g = 9.81f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        //constante elastica
        setupSeekBar(
            binding.seekK, binding.tvK, 10, 490, 10, " N/m"
        ) { progress ->
            k = progress.toFloat()
            actualizarCalculos()
        }
        //angulo de tension
        setupSeekBar(
            binding.seekX, binding.tvX, 5, 100, 50, ""
        ) { progress ->
            x = progress / 100f
            binding.tvX.text = "%.2f".format(x)
            actualizarCalculos()
        }
        //masa
        setupSeekBar(
            binding.seekMasa, binding.tvMasa, 5, 50, 10, " kg"
        ) { progress ->
            masa = progress / 100f
            binding.tvMasa.text = "%.2f kg".format(masa)
            actualizarCalculos()
        }

        binding.btnDisparar.setOnClickListener { disparar() }
        binding.btnReiniciar.setOnClickListener { reiniciar() }

        actualizarCalculos()
    }

    // -----------------------------------------------------------------
    // Configuración de cada SeekBar con su etiqueta de valor
    // -----------------------------------------------------------------
    private fun setupSeekBar(
        seek: SeekBar,
        label: TextView,
        min: Int,
        max: Int,
        initial: Int,
        unidad: String,
        onChange: (Int) -> Unit
    ) {
        seek.max = max - min
        seek.progress = initial - min
        label.text = "$initial$unidad"
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val valor = progress + min
                label.text = "$valor$unidad"
                onChange(valor)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    // -----------------------------------------------------------------
    // Cálculos de física y actualización de la interfaz
    // -----------------------------------------------------------------
    private fun actualizarCalculos() {
        // El ángulo de lanzamiento se calcula del ángulo de tensión
        // A mayor tensión, mayor ángulo de lanzamiento (como en una catapulta real)
        anguloLanzamiento = 30f + x * 40f  // 30° a 70° según la tensión

        // Energía potencial elástica almacenada en la catapulta
        epe = 0.5f * k * x * x

        // Conservación: toda la EPE se convierte en energía cinética
        // ½·k·x² = ½·m·v²  →  v = x·√(k/m)
        velocidad = if (masa > 0f) x * sqrt(k / masa) else 0f

        // Altura máxima del tiro parabólico: h = (v·sinθ)² / (2·g)
        val vy = velocidad * sin(Math.toRadians(anguloLanzamiento.toDouble())).toFloat()
        alturaMax = (vy * vy) / (2f * g)

        // Tiempo total de vuelo (caída al mismo nivel): T = 2·v·sinθ / g
        tiempoVuelo = (2f * vy) / g

        // Tamaño del proyectil según la masa (visual)
        val projectileSize = 0.5f + masa * 2f

        // Reflejar en pantalla - ANTES DE LANZAR
        binding.tvEPE.text = "EPE de la catapulta: %.2f J".format(epe)
        binding.tvEC.text = "EC del proyectil: 0.00 J"
        binding.tvEPG.text = "EPG del proyectil: 0.00 J"
        binding.tvEnergiaTotal.text = "Energía total: %.2f J".format(epe)
        binding.tvVelocidad.text = "Velocidad inicial: %.2f m/s".format(velocidad)
        binding.tvAltura.text = "Altura máxima: %.2f m".format(alturaMax)
        binding.tvTiempo.text = "Tiempo de vuelo: %.2f s".format(tiempoVuelo)

        // Actualizar la vista de la catapulta tensada
        binding.arrowView.mode = ArrowView.Mode.CATAPULT
        binding.arrowView.drawLength = x
        binding.arrowView.projectileSize = projectileSize
        binding.arrowView.invalidate()
    }

    // -----------------------------------------------------------------
    // Disparo: animación del vuelo con las ecuaciones reales
    // -----------------------------------------------------------------
    private fun disparar() {
        if (x <= 0f) return

        animator?.cancel()

        val vista = binding.arrowView
        vista.mode = ArrowView.Mode.FLIGHT
        vista.velocity = velocidad
        vista.angleDeg = anguloLanzamiento
        vista.maxHeight = alturaMax
        vista.flightTime = tiempoVuelo
        vista.flightProgress = 0f
        vista.projectileSize = 0.5f + masa * 2f

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (tiempoVuelo * 1000).toLong().coerceAtLeast(1L)
            addUpdateListener { anim ->
                val p = anim.animatedValue as Float
                vista.flightProgress = p
                vista.invalidate()

                // Lecturas en vivo durante el vuelo
                val t = p * tiempoVuelo
                val vy = velocidad * sin(Math.toRadians(anguloLanzamiento.toDouble())).toFloat()
                val h = (vy * t - 0.5f * g * t * t).coerceAtLeast(0f)
                val epg = masa * g * h
                val ec = (epe - epg).coerceAtLeast(0f)

                binding.tvEC.text = "EC del proyectil: %.2f J".format(ec)
                binding.tvEPG.text = "EPG del proyectil: %.2f J".format(epg)
                binding.tvEnergiaTotal.text = "Energía total: %.2f J".format(ec + epg)
            }
            start()
        }
    }

    // -----------------------------------------------------------------
    // Reinicio: vuelve a la catapulta tensada con los valores actuales
    // -----------------------------------------------------------------
    private fun reiniciar() {
        animator?.cancel()
        actualizarCalculos()
    }
}
