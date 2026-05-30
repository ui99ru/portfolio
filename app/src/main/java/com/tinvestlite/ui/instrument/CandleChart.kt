package com.tinvestlite.ui.instrument

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.tinvestlite.data.remote.dto.Candle
import com.tinvestlite.ui.theme.LossRed
import com.tinvestlite.ui.theme.ProfitGreen
import com.tinvestlite.util.toDouble
import kotlin.math.max

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
                    size = androidx.compose.ui.geometry.Size(bodyWidth, max(1f, bottom - top)),
                )
            }
        }
    }
}
