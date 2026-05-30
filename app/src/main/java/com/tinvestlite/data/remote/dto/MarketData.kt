package com.tinvestlite.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class GetCandlesRequest(
    val instrumentId: String,
    val from: String,
    val to: String,
    val interval: String,
)

@Serializable
data class GetCandlesResponse(
    val candles: List<Candle> = emptyList(),
)

@Serializable
data class Candle(
    val open: Quotation = Quotation(),
    val high: Quotation = Quotation(),
    val low: Quotation = Quotation(),
    val close: Quotation = Quotation(),
    val volume: Long = 0,
    val time: String = "",
    val isComplete: Boolean = false,
)

@Serializable
data class GetLastPricesRequest(
    val instrumentId: List<String> = emptyList(),
)

@Serializable
data class GetLastPricesResponse(
    val lastPrices: List<LastPrice> = emptyList(),
)

@Serializable
data class LastPrice(
    val figi: String = "",
    val price: Quotation = Quotation(),
    val time: String = "",
    val instrumentUid: String = "",
)

@Serializable
data class GetClosePricesRequest(
    val instruments: List<ClosePriceInstrument> = emptyList(),
)

@Serializable
data class ClosePriceInstrument(
    val instrumentId: String,
)

@Serializable
data class GetClosePricesResponse(
    val closePrices: List<InstrumentClosePrice> = emptyList(),
)

@Serializable
data class InstrumentClosePrice(
    val figi: String = "",
    val instrumentUid: String = "",
    val price: Quotation = Quotation(),
    val eveningSessionPrice: Quotation = Quotation(),
)

@Serializable
data class GetOrderBookRequest(
    val instrumentId: String,
    val depth: Int = 10,
)

@Serializable
data class GetOrderBookResponse(
    val figi: String = "",
    val depth: Int = 0,
    val bids: List<OrderBookEntry> = emptyList(),
    val asks: List<OrderBookEntry> = emptyList(),
    val lastPrice: Quotation = Quotation(),
    val closePrice: Quotation = Quotation(),
    val instrumentUid: String = "",
)

@Serializable
data class OrderBookEntry(
    val price: Quotation = Quotation(),
    val quantity: Long = 0,
)
