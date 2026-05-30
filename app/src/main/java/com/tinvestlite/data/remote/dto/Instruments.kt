package com.tinvestlite.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class FindInstrumentRequest(
    val query: String,
    val instrumentKind: String? = null,
    val apiTradeAvailableFlag: Boolean = true,
)

@Serializable
data class FindInstrumentResponse(
    val instruments: List<InstrumentShort> = emptyList(),
)

@Serializable
data class InstrumentShort(
    val isin: String = "",
    val figi: String = "",
    val ticker: String = "",
    val classCode: String = "",
    val instrumentType: String = "",
    val name: String = "",
    val uid: String = "",
    val lot: Int = 1,
    val apiTradeAvailableFlag: Boolean = false,
    val currency: String = "",
)

@Serializable
data class InstrumentRequest(
    val idType: String = "INSTRUMENT_ID_TYPE_UID",
    val id: String,
)

@Serializable
data class InstrumentResponse(
    val instrument: InstrumentDetail = InstrumentDetail(),
)

@Serializable
data class InstrumentDetail(
    val figi: String = "",
    val ticker: String = "",
    val name: String = "",
    val uid: String = "",
    val lot: Int = 1,
    val currency: String = "",
    val instrumentType: String = "",
    val minPriceIncrement: Quotation = Quotation(),
    val tradingStatus: String = "",
    val apiTradeAvailableFlag: Boolean = false,
)
