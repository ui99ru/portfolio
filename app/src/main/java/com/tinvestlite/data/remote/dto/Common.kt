package com.tinvestlite.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * T-Invest API numeric value with arbitrary precision.
 * Real value = units + nano / 1_000_000_000.
 */
@Serializable
data class Quotation(
    val units: Long = 0,
    val nano: Int = 0,
)

/** Monetary amount in a given currency. */
@Serializable
data class MoneyValue(
    val currency: String = "",
    val units: Long = 0,
    val nano: Int = 0,
)
