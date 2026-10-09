package com.yalazas.app.ui.admin.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.ceil
import kotlin.math.max

class SalesChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A352F")
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8E9E96")
        textSize = 24f
        textAlign = Paint.Align.RIGHT
    }

    private val xTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8E9E96")
        textSize = 20f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0C4B8")
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }

    // Línea única de Ventas: Color Verde
    private val greenLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val greenDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }

    private val dotCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1C2320")
        style = Paint.Style.FILL
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var xLabels = listOf("ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SET", "OCT", "NOV", "DIC")
    private var periodLabel = "Meses"
    private var yValues = listOf(60, 40, 20, 0)
    private var currentMaxY = 60f

    // Puntos de datos de ventas (en Soles)
    private var salesData = listOf<Float>()

    fun setChartData(
        labels: List<String>,
        period: String,
        sales: List<Float>
    ) {
        if (labels.isNotEmpty()) {
            this.xLabels = labels
        }
        this.periodLabel = period
        this.salesData = sales

        val maxVal = salesData.maxOrNull() ?: 0f
        val calculatedMax = if (maxVal <= 0f) 50f else ceil(maxVal * 1.25f / 10f) * 10f
        this.currentMaxY = max(calculatedMax, 40f)

        val step = this.currentMaxY / 3f
        this.yValues = listOf(
            this.currentMaxY.toInt(),
            (step * 2).toInt(),
            step.toInt(),
            0
        )

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        if (w <= 0 || h <= 0) return

        val paddingLeft = 85f
        val paddingRight = 35f
        val paddingTop = 40f
        val paddingBottom = 75f

        val plotWidth = w - paddingLeft - paddingRight
        val plotHeight = h - paddingTop - paddingBottom

        // 1. Dibujar líneas de cuadrícula horizontales y valores del eje Y
        for (i in yValues.indices) {
            val yVal = yValues[i]
            val yPos = paddingTop + (i.toFloat() / (yValues.size - 1)) * plotHeight

            canvas.drawText("S/ $yVal", paddingLeft - 12f, yPos + 8f, textPaint)
            canvas.drawLine(paddingLeft, yPos, w - paddingRight, yPos, gridPaint)
        }

        // 2. Etiqueta del Eje Y ("Ventas (S/)" rotado a la izquierda)
        canvas.save()
        canvas.rotate(-90f, 30f, h / 2f)
        canvas.drawText("Ventas (S/)", 30f, h / 2f, labelPaint)
        canvas.restore()

        // 3. Etiquetas del Eje X
        if (xLabels.isNotEmpty()) {
            val divisor = if (xLabels.size > 1) xLabels.size - 1 else 1
            for (i in xLabels.indices) {
                val xPos = paddingLeft + (i.toFloat() / divisor) * plotWidth
                canvas.drawText(xLabels[i], xPos, h - 35f, xTextPaint)
            }
        }

        // 4. Etiqueta de Periodo ("Días", "Meses", "Años")
        canvas.drawText(periodLabel, paddingLeft + plotWidth / 2f, h - 8f, labelPaint)

        // 5. Dibujar la serie única de Ventas (Línea Verde)
        if (salesData.isNotEmpty()) {
            drawSalesSeries(canvas, salesData, paddingLeft, paddingTop, plotWidth, plotHeight)
        }
    }

    private fun drawSalesSeries(
        canvas: Canvas,
        data: List<Float>,
        left: Float,
        top: Float,
        pWidth: Float,
        pHeight: Float
    ) {
        val path = Path()
        val fillPath = Path()
        val points = mutableListOf<PointF>()
        val divisor = if (data.size > 1) data.size - 1 else 1

        val baselineY = top + pHeight

        for (i in data.indices) {
            val x = left + (i.toFloat() / divisor) * pWidth
            val normalizedY = (data[i] / currentMaxY).coerceIn(0f, 1f)
            val y = top + pHeight - (normalizedY * pHeight)
            points.add(PointF(x, y))

            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, baselineY)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        // Cerrar el path para el sombreado degradado suave debajo de la línea verde
        if (points.isNotEmpty()) {
            fillPath.lineTo(points.last().x, baselineY)
            fillPath.close()

            fillPaint.shader = LinearGradient(
                0f, top, 0f, baselineY,
                Color.parseColor("#444CAF50"),
                Color.parseColor("#054CAF50"),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(fillPath, fillPaint)
        }

        // Dibujar la línea verde
        canvas.drawPath(path, greenLinePaint)

        // Dibujar los puntos circulares en cada vértice
        val dotRadius = 7.5f
        for (pt in points) {
            canvas.drawCircle(pt.x, pt.y, dotRadius, dotCenterPaint)
            canvas.drawCircle(pt.x, pt.y, dotRadius, greenDotPaint)
        }
    }
}
