package com.tinvestlite.ui.instrument

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.tinvestlite.data.remote.dto.Candle
import com.tinvestlite.ui.theme.LossRed
import com.tinvestlite.ui.theme.ProfitGreen
import com.tinvestlite.util.toDouble
import kotlin.math.max

/** Chart rendering style chosen by the user. */
enum class ChartType { Candles, Line }

/**
 * Minimal candlestick chart drawn directly on a Compose [Canvas] — no third
 * party charting dependency. Renders the open/high/low/close of each candle.
 */
@Composable
fun CandleChart(
    candles: List<Candle>,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(240.dp)) {
        Canvas(Modifier.fillMaxWidth().height(240.dp)) {
            if (candles.isEmpty()) return@Canvas

            val highs = candles.map { it.high.toDouble() }
            val lows = candles.map { it.low.toDouble() }
            val maxPrice = highs.max()
            val minPrice = lows.min()
            val range = (maxPrice - minPrice).takeIf { it > 0.0 } ?: 1.0

            val paddingV = 8f
            val usableHeight = size.height - paddingV * 2
            val slot = size.width / candles.size
            val bodyWidth = max(1f, slot * 0.6f)

            fun yFor(price: Double): Float =
                paddingV + ((maxPrice - price) / range * usableHeight).toFloat()

            candles.forEachIndexed { index, candle ->
                val centerX = slot * index + slot / 2
                val openY = yFor(candle.open.toDouble())
                val closeY = yFor(candle.close.toDouble())
                val highY = yFor(candle.high.toDouble())
                val lowY = yFor(candle.low.toDouble())
                val bullish = candle.close.toDouble() >= candle.open.toDouble()
                val color = if (bullish) ProfitGreen else LossRed

                // Wick
                drawLine(
                    color = color,
                    start = Offset(centerX, highY),
                    end = Offset(centerX, lowY),
                    strokeWidth = max(1f, bodyWidth * 0.18f),
                    cap = StrokeCap.Round,
                )
                // Body
                val top = minOf(openY, closeY)
                val bottom = maxOf(openY, closeY)
                drawRect(
                    color = color,
                    topLeft = Offset(centerX - bodyWidth / 2, top),
                    size = Size(bodyWidth, max(1f, bottom - top)),
                )
            }
        }
    }
}

/**
 * Simple line chart of candle close prices. Green when the period closed up,
 * red when down — matching the rest of the app's color language.
 */
@Composable
fun LineChart(
    candles: List<Candle>,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(240.dp)) {
        Canvas(Modifier.fillMaxWidth().height(240.dp)) {
            if (candles.size < 2) return@Canvas

            val closes = candles.map { it.close.toDouble() }
            val maxPrice = closes.max()
            val minPrice = closes.min()
            val range = (maxPrice - minPrice).takeIf { it > 0.0 } ?: 1.0

            val paddingV = 8f
            val usableHeight = size.height - paddingV * 2
            val stepX = size.width / (closes.size - 1)

            fun yFor(price: Double): Float =
                paddingV + ((maxPrice - price) / range * usableHeight).toFloat()

            val path = Path()
            closes.forEachIndexed { index, price ->
                val x = stepX * index
                val y = yFor(price)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            val rising = closes.last() >= closes.first()
            drawPath(
                path = path,
                color = if (rising) ProfitGreen else LossRed,
                style = Stroke(width = 3f, cap = StrokeCap.Round),
            )
        }
    }
}
