package com.tinvestlite.data.repository

import com.tinvestlite.data.ApiResult
import com.tinvestlite.data.local.TokenStore
import com.tinvestlite.data.remote.NetworkModule
import com.tinvestlite.data.remote.TInvestApi
import com.tinvestlite.data.remote.dto.Account
import com.tinvestlite.data.remote.dto.CancelOrderRequest
import com.tinvestlite.data.remote.dto.FindInstrumentRequest
import com.tinvestlite.data.remote.dto.GetAccountsRequest
import com.tinvestlite.data.remote.dto.GetCandlesRequest
import com.tinvestlite.data.remote.dto.GetLastPricesRequest
import com.tinvestlite.data.remote.dto.GetOrderBookRequest
import com.tinvestlite.data.remote.dto.GetOrderBookResponse
import com.tinvestlite.data.remote.dto.GetOrdersRequest
import com.tinvestlite.data.remote.dto.InstrumentRequest
import com.tinvestlite.data.remote.dto.InstrumentDetail
import com.tinvestlite.data.remote.dto.InstrumentShort
import com.tinvestlite.data.remote.dto.InstrumentsRequest
import com.tinvestlite.data.remote.dto.MoneyValue
import com.tinvestlite.data.remote.dto.OpenSandboxAccountRequest
import com.tinvestlite.data.remote.dto.Operation
import com.tinvestlite.data.remote.dto.OperationsRequest
import com.tinvestlite.data.remote.dto.OrderState
import com.tinvestlite.data.remote.dto.PortfolioRequest
import com.tinvestlite.data.remote.dto.PortfolioResponse
import com.tinvestlite.data.remote.dto.PostOrderRequest
import com.tinvestlite.data.remote.dto.PostOrderResponse
import com.tinvestlite.data.remote.dto.Quotation
import com.tinvestlite.data.remote.dto.SandboxPayInRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID

/** Sandbox account name created on first launch. */
private const val SANDBOX_ACCOUNT_NAME = "TInvestLite Sandbox"

class InvestRepository(
    private val tokenStore: TokenStore,
    private val enableLogging: Boolean,
) {

    // Recreated whenever needed; the interceptor reads the token live.
    private val api: TInvestApi by lazy { NetworkModule.create(tokenStore, enableLogging) }

    // ---- Accounts ----

    suspend fun listAccounts(): ApiResult<List<Account>> = safeCall {
        api.getSandboxAccounts(GetAccountsRequest()).accounts
    }

    /** Returns an existing sandbox account or opens a fresh one. */
    suspend fun ensureSandboxAccount(): ApiResult<String> = safeCall {
        val existing = api.getSandboxAccounts(GetAccountsRequest()).accounts
            .firstOrNull { it.status == "ACCOUNT_STATUS_OPEN" || it.status.isBlank() }
        val id = existing?.id ?: api.openSandboxAccount(OpenSandboxAccountRequest()).accountId
        tokenStore.accountId = id
        id
    }

    suspend fun payIn(accountId: String, units: Long, currency: String = "rub"): ApiResult<Unit> =
        safeCall {
            api.sandboxPayIn(
                SandboxPayInRequest(
                    accountId = accountId,
                    amount = MoneyValue(currency = currency, units = units, nano = 0),
                ),
            )
            Unit
        }

    // ---- Portfolio ----

    suspend fun getPortfolio(accountId: String): ApiResult<PortfolioResponse> = safeCall {
        api.getSandboxPortfolio(PortfolioRequest(accountId = accountId))
    }

    // ---- Operations ----

    suspend fun getOperations(
        accountId: String,
        from: String,
        to: String,
    ): ApiResult<List<Operation>> = safeCall {
        api.getSandboxOperations(
            OperationsRequest(accountId = accountId, from = from, to = to),
        ).operations.sortedByDescending { it.date }
    }

    // ---- Instruments & market data ----

    suspend fun findInstruments(query: String): ApiResult<List<InstrumentShort>> = safeCall {
        api.findInstrument(FindInstrumentRequest(query = query)).instruments
    }

    suspend fun getInstrument(uid: String): ApiResult<InstrumentDetail> = safeCall {
        api.getInstrumentBy(InstrumentRequest(id = uid)).instrument
    }

    /** Tradable shares, tagged with instrumentType="share" and sorted by ticker. */
    suspend fun getShares(): ApiResult<List<InstrumentShort>> = safeCall {
        api.getShares(InstrumentsRequest()).instruments
            .filter { it.apiTradeAvailableFlag }
            .map { it.copy(instrumentType = "share") }
            .sortedBy { it.ticker }
    }

    /** Tradable bonds, tagged with instrumentType="bond" and sorted by name. */
    suspend fun getBonds(): ApiResult<List<InstrumentShort>> = safeCall {
        api.getBonds(InstrumentsRequest()).instruments
            .filter { it.apiTradeAvailableFlag }
            .map { it.copy(instrumentType = "bond") }
            .sortedBy { it.name }
    }

    /** Tradable ETFs, tagged with instrumentType="etf" and sorted by name. */
    suspend fun getEtfs(): ApiResult<List<InstrumentShort>> = safeCall {
        api.getEtfs(InstrumentsRequest()).instruments
            .filter { it.apiTradeAvailableFlag }
            .map { it.copy(instrumentType = "etf") }
            .sortedBy { it.name }
    }

    suspend fun getCandles(
        uid: String,
        from: String,
        to: String,
        interval: String,
    ): ApiResult<List<com.tinvestlite.data.remote.dto.Candle>> = safeCall {
        api.getCandles(
            GetCandlesRequest(instrumentId = uid, from = from, to = to, interval = interval),
        ).candles
    }

    suspend fun getOrderBook(uid: String, depth: Int = 10): ApiResult<GetOrderBookResponse> =
        safeCall {
            api.getOrderBook(GetOrderBookRequest(instrumentId = uid, depth = depth))
        }

    suspend fun getLastPrice(uid: String): ApiResult<Quotation> = safeCall {
        api.getLastPrices(GetLastPricesRequest(instrumentId = listOf(uid)))
            .lastPrices.firstOrNull()?.price ?: Quotation()
    }

    // ---- Orders ----

    suspend fun postOrder(
        accountId: String,
        instrumentUid: String,
        lots: Long,
        isBuy: Boolean,
        isMarket: Boolean,
        price: Quotation?,
    ): ApiResult<PostOrderResponse> = safeCall {
        api.postSandboxOrder(
            PostOrderRequest(
                accountId = accountId,
                instrumentId = instrumentUid,
                quantity = lots,
                direction = if (isBuy) "ORDER_DIRECTION_BUY" else "ORDER_DIRECTION_SELL",
                orderType = if (isMarket) "ORDER_TYPE_MARKET" else "ORDER_TYPE_LIMIT",
                orderId = UUID.randomUUID().toString(),
                price = if (isMarket) null else price,
            ),
        )
    }

    suspend fun getOrders(accountId: String): ApiResult<List<OrderState>> = safeCall {
        api.getSandboxOrders(GetOrdersRequest(accountId = accountId)).orders
    }

    suspend fun cancelOrder(accountId: String, orderId: String): ApiResult<Unit> = safeCall {
        api.cancelSandboxOrder(CancelOrderRequest(accountId = accountId, orderId = orderId))
        Unit
    }

    // ---- internals ----

    @Serializable
    private data class GatewayError(
        val code: Int = 0,
        val message: String = "",
        val description: String = "",
    )

    private suspend fun <T> safeCall(block: suspend () -> T): ApiResult<T> =
        withContext(Dispatchers.IO) {
            try {
                ApiResult.Success(block())
            } catch (e: HttpException) {
                ApiResult.Error(parseHttpError(e), e)
            } catch (e: IOException) {
                ApiResult.Error("Нет сети. Проверь подключение к интернету.", e)
            } catch (e: Exception) {
                ApiResult.Error(e.message ?: "Неизвестная ошибка", e)
            }
        }

    private fun parseHttpError(e: HttpException): String {
        val raw = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        val parsed = raw?.let {
            runCatching { NetworkModule.json.decodeFromString<GatewayError>(it) }.getOrNull()
        }
        val detail = parsed?.message?.takeIf { it.isNotBlank() }
            ?: parsed?.description?.takeIf { it.isNotBlank() }
        return when (e.code()) {
            401 -> "Неверный токен или нет доступа (401). Проверь токен в настройках."
            429 -> "Слишком много запросов (429). Попробуй чуть позже."
            else -> detail ?: "Ошибка API: ${e.code()}"
        }
    }
}
