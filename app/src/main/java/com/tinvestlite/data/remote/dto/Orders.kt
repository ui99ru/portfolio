package com.tinvestlite.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class PostOrderRequest(
    val accountId: String,
    val instrumentId: String,
    val quantity: Long,
    val direction: String,
    val orderType: String,
    val orderId: String,
    val price: Quotation? = null,
)

@Serializable
data class PostOrderResponse(
    val orderId: String = "",
    val executionReportStatus: String = "",
    val lotsRequested: Long = 0,
    val lotsExecuted: Long = 0,
    val initialOrderPrice: MoneyValue = MoneyValue(),
    val executedOrderPrice: MoneyValue = MoneyValue(),
    val totalOrderAmount: MoneyValue = MoneyValue(),
    val initialCommission: MoneyValue = MoneyValue(),
    val figi: String = "",
    val direction: String = "",
    val orderType: String = "",
    val message: String = "",
)

@Serializable
data class GetOrdersRequest(
    val accountId: String,
)

@Serializable
data class GetOrdersResponse(
    val orders: List<OrderState> = emptyList(),
)

@Serializable
data class OrderState(
    val orderId: String = "",
    val executionReportStatus: String = "",
    val lotsRequested: Long = 0,
    val lotsExecuted: Long = 0,
    val initialOrderPrice: MoneyValue = MoneyValue(),
    val totalOrderAmount: MoneyValue = MoneyValue(),
    val direction: String = "",
    val orderType: String = "",
    val figi: String = "",
    val instrumentUid: String = "",
)

@Serializable
data class CancelOrderRequest(
    val accountId: String,
    val orderId: String,
)

@Serializable
data class CancelOrderResponse(
    val time: String = "",
)

@Serializable
data class WithdrawLimitsRequest(
    val accountId: String,
)

@Serializable
data class WithdrawLimitsResponse(
    val money: List<MoneyValue> = emptyList(),
    val blocked: List<MoneyValue> = emptyList(),
)
