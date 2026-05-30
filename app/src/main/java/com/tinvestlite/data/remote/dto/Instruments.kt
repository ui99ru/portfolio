package com.tinvestlite.data.remote.dto

import kotlinx.serialization.Serializable

/** Brand block carrying the instrument logo file name (T-Invest CDN). */
@Serializable
data class Brand(
    val logoName: String = "",
    val logoBaseColor: String = "",
    val textColor: String = "",
)

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
    val countryOfRisk: String = "",
    val forQualInvestorFlag: Boolean = false,
    val brand: Brand = Brand(),
)

/** Request for the Shares/Bonds/Etfs catalog endpoints. */
@Serializable
data class InstrumentsRequest(
    val instrumentStatus: String = "INSTRUMENT_STATUS_BASE",
)

/**
 * Response shared by Shares/Bonds/Etfs. Each item carries the common fields
 * of [InstrumentShort]; type-specific extras are ignored by the parser.
 * Note: these catalog items don't include `instrumentType`, so the repository
 * tags it when mapping.
 */
@Serializable
data class InstrumentsListResponse(
    val instruments: List<InstrumentShort> = emptyList(),
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
    val countryOfRisk: String = "",
    val brand: Brand = Brand(),
)
