package com.tinvestlite.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
class GetAccountsRequest

@Serializable
data class GetAccountsResponse(
    val accounts: List<Account> = emptyList(),
)

@Serializable
data class Account(
    val id: String = "",
    val type: String = "",
    val name: String = "",
    val status: String = "",
    val openedDate: String? = null,
    val accessLevel: String? = null,
)

@Serializable
class OpenSandboxAccountRequest

@Serializable
data class OpenSandboxAccountResponse(
    val accountId: String = "",
)

@Serializable
data class CloseSandboxAccountRequest(
    val accountId: String,
)

@Serializable
class CloseSandboxAccountResponse

/** Top up a sandbox account with virtual money. */
@Serializable
data class SandboxPayInRequest(
    val accountId: String,
    val amount: MoneyValue,
)

@Serializable
data class SandboxPayInResponse(
    val balance: MoneyValue = MoneyValue(),
)
