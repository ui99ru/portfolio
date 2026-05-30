package com.tinvestlite.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class OperationsRequest(
    val accountId: String,
    val from: String? = null,
    val to: String? = null,
    val state: String? = null,
    val figi: String? = null,
)

@Serializable
data class OperationsResponse(
    val operations: List<Operation> = emptyList(),
)

@Serializable
data class Operation(
    val id: String = "",
    val figi: String = "",
    val instrumentType: String = "",
    val date: String = "",
    val type: String = "",
    val operationType: String = "",
    val state: String = "",
    val payment: MoneyValue = MoneyValue(),
    val price: MoneyValue = MoneyValue(),
    val quantity: Long = 0,
    val currency: String = "",
)
