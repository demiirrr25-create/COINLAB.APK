package com.coinlab.app.ui.components

import com.coinlab.app.domain.model.OhlcData
import kotlin.math.abs
import kotlin.math.sqrt

object TechnicalIndicators {

    // Simple Moving Average
    fun sma(data: List<Double>, period: Int): List<Double?> {
        return data.mapIndexed { index, _ ->
            if (index < period - 1) null
            else data.subList(index - period + 1, index + 1).average()
        }
    }

    // Exponential Moving Average
    fun ema(data: List<Double>, period: Int): List<Double?> {
        if (data.size < period) return data.map { null }
        val multiplier = 2.0 / (period + 1)
        val result = MutableList<Double?>(data.size) { null }
        result[period - 1] = data.subList(0, period).average()
        for (i in period until data.size) {
            val prev = result[i - 1] ?: continue
            result[i] = (data[i] - prev) * multiplier + prev
        }
        return result
    }

    // Relative Strength Index
    fun rsi(data: List<Double>, period: Int = 14): List<Double?> {
        if (data.size < period + 1) return data.map { null }
        val changes = data.zipWithNext { a, b -> b - a }
        val gains = changes.map { if (it > 0) it else 0.0 }
        val losses = changes.map { if (it < 0) abs(it) else 0.0 }

        val result = MutableList<Double?>(data.size) { null }
        var avgGain = gains.subList(0, period).average()
        var avgLoss = losses.subList(0, period).average()

        if (avgLoss == 0.0) {
            result[period] = 100.0
        } else {
            result[period] = 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
        }

        for (i in period until changes.size) {
            avgGain = (avgGain * (period - 1) + gains[i]) / period
            avgLoss = (avgLoss * (period - 1) + losses[i]) / period
            result[i + 1] = if (avgLoss == 0.0) 100.0
            else 100.0 - (100.0 / (1.0 + avgGain / avgLoss))
        }
        return result
    }

    // MACD
    data class MacdResult(
        val macdLine: List<Double?>,
        val signalLine: List<Double?>,
        val histogram: List<Double?>
    )

    fun macd(data: List<Double>, short: Int = 12, long: Int = 26, signal: Int = 9): MacdResult {
        val shortEma = ema(data, short)
        val longEma = ema(data, long)
        val macdLine = shortEma.zip(longEma) { s, l ->
            if (s != null && l != null) s - l else null
        }
        val macdValues = macdLine.filterNotNull()
        val signalEma = ema(macdValues, signal)

        val signalLine = MutableList<Double?>(macdLine.size) { null }
        var signalIdx = 0
        for (i in macdLine.indices) {
            if (macdLine[i] != null) {
                if (signalIdx < signalEma.size) {
                    signalLine[i] = signalEma[signalIdx]
                }
                signalIdx++
            }
        }

        val histogram = macdLine.zip(signalLine) { m, s ->
            if (m != null && s != null) m - s else null
        }

        return MacdResult(macdLine, signalLine, histogram)
    }

    // Bollinger Bands
    data class BollingerBands(
        val upper: List<Double?>,
        val middle: List<Double?>,
        val lower: List<Double?>
    )

    fun bollingerBands(data: List<Double>, period: Int = 20, deviations: Double = 2.0): BollingerBands {
        val middle = sma(data, period)
        val upper = MutableList<Double?>(data.size) { null }
        val lower = MutableList<Double?>(data.size) { null }

        for (i in data.indices) {
            val mid = middle[i] ?: continue
            if (i < period - 1) continue
            val window = data.subList(i - period + 1, i + 1)
            val mean = window.average()
            val variance = window.sumOf { (it - mean) * (it - mean) } / period
            val stdDev = sqrt(variance)
            upper[i] = mid + deviations * stdDev
            lower[i] = mid - deviations * stdDev
        }

        return BollingerBands(upper, middle, lower)
    }

    // Volume Weighted Average Price (VWAP) simplified
    fun vwap(ohlcData: List<OhlcData>, volumes: List<Double>): List<Double?> {
        if (ohlcData.size != volumes.size) return ohlcData.map { null }
        val result = MutableList<Double?>(ohlcData.size) { null }
        var cumulativeVP = 0.0
        var cumulativeVolume = 0.0

        for (i in ohlcData.indices) {
            val typicalPrice = (ohlcData[i].high + ohlcData[i].low + ohlcData[i].close) / 3
            cumulativeVP += typicalPrice * volumes[i]
            cumulativeVolume += volumes[i]
            result[i] = if (cumulativeVolume > 0) cumulativeVP / cumulativeVolume else null
        }
        return result
    }

    // Stochastic RSI
    fun stochasticRsi(data: List<Double>, period: Int = 14, kPeriod: Int = 3, dPeriod: Int = 3): Pair<List<Double?>, List<Double?>> {
        val rsiValues = rsi(data, period)
        val rsiNonNull = rsiValues.filterNotNull()
        if (rsiNonNull.size < period) return Pair(data.map { null }, data.map { null })

        val kValues = MutableList<Double?>(rsiValues.size) { null }
        var rsiIdx = 0
        for (i in rsiValues.indices) {
            if (rsiValues[i] != null) {
                if (rsiIdx >= period - 1) {
                    val window = rsiNonNull.subList(
                        (rsiIdx - period + 1).coerceAtLeast(0),
                        rsiIdx + 1
                    )
                    val minRsi = window.min()
                    val maxRsi = window.max()
                    kValues[i] = if (maxRsi != minRsi) ((rsiValues[i]!! - minRsi) / (maxRsi - minRsi)) * 100 else 50.0
                }
                rsiIdx++
            }
        }

        // D line = SMA of K
        val kNonNull = kValues.filterNotNull()
        val dSma = sma(kNonNull, dPeriod)
        val dValues = MutableList<Double?>(kValues.size) { null }
        var kIdx = 0
        for (i in kValues.indices) {
            if (kValues[i] != null) {
                if (kIdx < dSma.size) dValues[i] = dSma[kIdx]
                kIdx++
            }
        }

        return Pair(kValues, dValues)
    }
}
