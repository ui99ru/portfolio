package com.tinvestlite.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PortfolioRequest(
    val accountId: String,
    val currency: String = "RUB",
)

@Serializable
data class PortfolioResponse(
    val totalAmountShares: MoneyValue = MoneyValue(),
    val totalAmountBonds: MoneyValue = MoneyValue(),
    val totalAmountEtf: MoneyValue = MoneyValue(),
    val totalAmountCurrencies: MoneyValue = MoneyValue(),
    val totalAmountFutures: MoneyValue = MoneyValue(),
    val totalAmountPortfolio: MoneyValue = MoneyValue(),
    val expectedYield: Quotation = Quotation(),
    val positions: List<PortfolioPosition> = emptyList(),
)

@Serializable
data class PortfolioPosition(
    val figi: String = "",
    val instrumentType: String = "",
    val quantity: Quotation = Quotation(),
    val averagePositionPrice: MoneyValue = MoneyValue(),
    val expectedYield: Quotation = Quotation(),
    val currentNkd: MoneyValue = MoneyValue(),
    val currentPrice: MoneyValue = MoneyValue(),
    val averagePositionPriceFifo: MoneyValue = MoneyValue(),
    val quantityLots: Quotation = Quotation(),
    val instrumentUid: String = "",
    val ticker: String? = null,
    val currentPriceCurrency: String? = null,
)
