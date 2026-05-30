package com.tinvestlite.ui.instrument

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.tinvestlite.data.remote.dto.Candle
import com.tinvestlite.ui.theme.LossRed
import com.tinvestlite.ui.theme.ProfitGreen
import com.tinvestlite.util.MoneyFormat
import com.tinvestlite.util.toDouble
import kotlin.math.max

/** Chart rendering style chosen by the user. */
enum class ChartType { Candles, Line }

private const val CHART_HEIGHT_DP = 280
private const val PRICE_AXIS_WIDTH_DP = 56
private const val GRID_LINES = 5

/**
 * Candlestick chart with a price axis (labels + grid) on the right and a dotted
 * line at the latest price — drawn directly on a Compose [Canvas].
 */
@Composable
fun CandleChart(candles: List<Candle>, modifier: Modifier = Modifier) {
    val labelPx = with(LocalDensity.current) { 11.dp.toPx() }
    val axisPx = with(LocalDensity.current) { PRICE_AXIS_WIDTH_DP.dp.toPx() }
    val gridColor = MaterialTheme.colorScheme.surfaceVariant
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier.fillMaxWidth().height(CHART_HEIGHT_DP.dp)) {
        Canvas(Modifier.fillMaxWidth().height(CHART_HEIGHT_DP.dp)) {
            if (candles.isEmpty()) return@Canvas

            val maxPrice = candles.maxOf { it.high.toDouble() }
            val minPrice = candles.minOf { it.low.toDouble() }
            val range = (maxPrice - minPrice).takeIf { it > 0.0 } ?: 1.0

            val plotWidth = size.width - axisPx
            val paddingV = 10f
            val usableHeight = size.height - paddingV * 2
            val slot = plotWidth / candles.size
            val bodyWidth = max(1f, slot * 0.6f)

            fun yFor(price: Double): Float =
                paddingV + ((maxPrice - price) / range * usableHeight).toFloat()

            drawPriceAxis(minPrice, maxPrice, plotWidth, paddingV, usableHeight, gridColor, axisTextColor, labelPx)

            candles.forEachIndexed { index, candle ->
                val centerX = slot * index + slot / 2
                val openY = yFor(candle.open.toDouble())
                val closeY = yFor(candle.close.toDouble())
                val bullish = candle.close.toDouble() >= candle.open.toDouble()
                val color = if (bullish) ProfitGreen else LossRed

                drawLine(
                    color = color,
                    start = Offset(centerX, yFor(candle.high.toDouble())),
                    end = Offset(centerX, yFor(candle.low.toDouble())),
                    strokeWidth = max(1f, bodyWidth * 0.18f),
                    cap = StrokeCap.Round,
                )
                val top = minOf(openY, closeY)
                val bottom = maxOf(openY, closeY)
                drawRect(
                    color = color,
                    topLeft = Offset(centerX - bodyWidth / 2, top),
                    size = Size(bodyWidth, max(1f, bottom - top)),
                )
            }

            drawLastPriceMarker(
                price = candles.last().close.toDouble(),
                min = minPrice, max = maxPrice,
                plotWidth = plotWidth, paddingV = paddingV, usableHeight = usableHeight,
                rising = candles.last().close.toDouble() >= candles.last().open.toDouble(),
                axisTextColor = axisTextColor, labelPx = labelPx,
            )
        }
    }
}

/**
 * Line chart of close prices with a price axis (labels + grid), a filled area
 * under the line, and a dotted marker at the latest price.
 */
@Composable
fun LineChart(candles: List<Candle>, modifier: Modifier = Modifier) {
    val labelPx = with(LocalDensity.current) { 11.dp.toPx() }
    val axisPx = with(LocalDensity.current) { PRICE_AXIS_WIDTH_DP.dp.toPx() }
    val gridColor = MaterialTheme.colorScheme.surfaceVariant
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(modifier.fillMaxWidth().height(CHART_HEIGHT_DP.dp)) {
        Canvas(Modifier.fillMaxWidth().height(CHART_HEIGHT_DP.dp)) {
            if (candles.size < 2) return@Canvas

            val closes = candles.map { it.close.toDouble() }
            val maxPrice = closes.max()
            val minPrice = closes.min()
            val range = (maxPrice - minPrice).takeIf { it > 0.0 } ?: 1.0

            val plotWidth = size.width - axisPx
            val paddingV = 10f
            val usableHeight = size.height - paddingV * 2
            val stepX = plotWidth / (closes.size - 1)

            fun yFor(price: Double): Float =
                paddingV + ((maxPrice - price) / range * usableHeight).toFloat()

            drawPriceAxis(minPrice, maxPrice, plotWidth, paddingV, usableHeight, gridColor, axisTextColor, labelPx)

            val rising = closes.last() >= closes.first()
            val lineColor = if (rising) ProfitGreen else LossRed

            val line = Path()
            closes.forEachIndexed { index, price ->
                val x = stepX * index
                val y = yFor(price)
                if (index == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }

            // Soft fill under the line.
            val fill = Path().apply {
                addPath(line)
                lineTo(stepX * (closes.size - 1), paddingV + usableHeight)
                lineTo(0f, paddingV + usableHeight)
                close()
            }
            drawPath(fill, color = lineColor.copy(alpha = 0.12f))
            drawPath(line, color = lineColor, style = Stroke(width = 3f, cap = StrokeCap.Round))

            drawLastPriceMarker(
                price = closes.last(),
                min = minPrice, max = maxPrice,
                plotWidth = plotWidth, paddingV = paddingV, usableHeight = usableHeight,
                rising = rising,
                axisTextColor = axisTextColor, labelPx = labelPx,
            )
        }
    }
}

/** Horizontal grid + price labels along the right axis. */
private fun DrawScope.drawPriceAxis(
    min: Double,
    max: Double,
    plotWidth: Float,
    paddingV: Float,
    usableHeight: Float,
    gridColor: Color,
    textColor: Color,
    labelPx: Float,
) {
    val paint = android.graphics.Paint().apply {
        color = textColor.toArgb()
        textSize = labelPx
        isAntiAlias = true
    }
    for (i in 0..GRID_LINES) {
        val frac = i.toFloat() / GRID_LINES
        val y = paddingV + usableHeight * frac
        val price = max - (max - min) * frac

        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(plotWidth, y),
            strokeWidth = 1f,
        )
        drawContext.canvas.nativeCanvas.drawText(
            MoneyFormat.price(java.math.BigDecimal.valueOf(price)),
            plotWidth + 8f,
            y + labelPx / 3,
            paint,
        )
    }
}

/** Dotted horizontal line at the latest price with a label in the axis gutter. */
private fun DrawScope.drawLastPriceMarker(
    price: Double,
    min: Double,
    max: Double,
    plotWidth: Float,
    paddingV: Float,
    usableHeight: Float,
    rising: Boolean,
    axisTextColor: Color,
    labelPx: Float,
) {
    val range = (max - min).takeIf { it > 0.0 } ?: 1.0
    val y = paddingV + ((max - price) / range * usableHeight).toFloat()
    val color = if (rising) ProfitGreen else LossRed

    drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(plotWidth, y),
        strokeWidth = 2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
    )
    val paint = android.graphics.Paint().apply {
        this.color = color.toArgb()
        textSize = labelPx
        isAntiAlias = true
        isFakeBoldText = true
    }
    drawContext.canvas.nativeCanvas.drawText(
        MoneyFormat.price(java.math.BigDecimal.valueOf(price)),
        plotWidth + 8f,
        y + labelPx / 3,
        paint,
    )
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(),
)
