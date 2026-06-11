package com.tinvestlite.data.repository

import com.tinvestlite.data.ApiResult
import com.tinvestlite.data.AppMode
import com.tinvestlite.data.local.TokenStore
import com.tinvestlite.data.remote.NetworkModule
import com.tinvestlite.data.remote.TInvestApi
import com.tinvestlite.data.remote.dto.Account
import com.tinvestlite.data.remote.dto.CancelOrderRequest
import com.tinvestlite.data.remote.dto.ClosePriceInstrument
import com.tinvestlite.data.remote.dto.FindInstrumentRequest
import com.tinvestlite.data.remote.dto.GetClosePricesRequest
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID

class InvestRepository(
    private val tokenStore: TokenStore,
    private val enableLogging: Boolean,
) {

    // Recreated whenever needed; the interceptor reads the active-mode token live.
    private val api: TInvestApi by lazy { NetworkModule.create(tokenStore, enableLogging) }

    private val mode: AppMode get() = tokenStore.mode.value

    // ---- Accounts ----

    /** Open accounts for the active mode (sandbox or real). */
    suspend fun listAccounts(): ApiResult<List<Account>> = safeCall {
        fetchAccounts()
    }

    private suspend fun fetchAccounts(): List<Account> {
        val accounts = if (mode.isReal) {
            api.getRealAccounts(GetAccountsRequest()).accounts
        } else {
            api.getSandboxAccounts(GetAccountsRequest()).accounts
        }
        return accounts.filter { it.status == "ACCOUNT_STATUS_OPEN" || it.status.isBlank() }
    }

    /**
     * Ensures a usable account id for the active mode.
     * Sandbox: opens an account if none exists. Real: picks the saved account
     * or the first open one (read-only — never creates anything).
     */
    suspend fun ensureAccount(): ApiResult<String> = safeCall {
        tokenStore.accountId(mode)?.let { return@safeCall it }
        val accounts = fetchAccounts()
        val id = if (mode.isReal) {
            accounts.firstOrNull()?.id
                ?: throw IllegalStateException("На реальном токене нет доступных счетов")
        } else {
            accounts.firstOrNull()?.id ?: api.openSandboxAccount(OpenSandboxAccountRequest()).accountId
        }
        tokenStore.setAccountId(mode, id)
        id
    }

    suspend fun payIn(accountId: String, units: Long, currency: String = "rub"): ApiResult<Unit> =
        guardSandbox {
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
        fetchPortfolio(accountId)
    }

    private suspend fun fetchPortfolio(accountId: String): PortfolioResponse =
        if (mode.isReal) {
            api.getRealPortfolio(PortfolioRequest(accountId = accountId))
        } else {
            api.getSandboxPortfolio(PortfolioRequest(accountId = accountId))
        }

    /**
     * Consolidated view across every open account in the active mode.
     * Returns the per-account portfolios paired with their account, so the UI
     * can show a combined total and let the user drill into each one.
     */
    suspend fun getAllPortfolios(): ApiResult<List<AccountPortfolio>> = safeCall {
        coroutineScope {
            val accounts = fetchAccounts()
            accounts.map { account ->
                async { AccountPortfolio(account, fetchPortfolio(account.id)) }
            }.awaitAll()
        }
    }

    // ---- Operations ----

    suspend fun getOperations(
        accountId: String,
        from: String,
        to: String,
    ): ApiResult<List<Operation>> = safeCall {
        val ops = if (mode.isReal) {
            api.getRealOperations(OperationsRequest(accountId = accountId, from = from, to = to))
        } else {
            api.getSandboxOperations(OperationsRequest(accountId = accountId, from = from, to = to))
        }
        ops.operations.sortedByDescending { it.date }
    }

    // ---- Instruments & market data (same endpoints in both modes) ----

    suspend fun findInstruments(query: String): ApiResult<List<InstrumentShort>> = safeCall {
        // apiTradeAvailableFlag = false → include instruments not tradable via
        // the API too (e.g. some funds in sandbox), so search can still find them.
        api.findInstrument(
            FindInstrumentRequest(query = query, apiTradeAvailableFlag = false),
        ).instruments
    }

    suspend fun getInstrument(uid: String): ApiResult<InstrumentDetail> = safeCall {
        api.getInstrumentBy(InstrumentRequest(id = uid)).instrument
    }

    /** Look up an instrument by FIGI — fallback when a position has no UID. */
    suspend fun getInstrumentByFigi(figi: String): ApiResult<InstrumentDetail> = safeCall {
        api.getInstrumentBy(
            InstrumentRequest(idType = "INSTRUMENT_ID_TYPE_FIGI", id = figi),
        ).instrument
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

    /**
     * Batch quotes (last price + day change %) for a list of instrument UIDs.
     * Combines GetLastPrices with GetClosePrices (previous close) so the UI can
     * show the daily change. Missing entries simply don't appear in the map.
     */
    suspend fun getQuotes(uids: List<String>): ApiResult<Map<String, Quote>> = safeCall {
        if (uids.isEmpty()) return@safeCall emptyMap<String, Quote>()
        coroutineScope {
            val lastDeferred = async {
                api.getLastPrices(GetLastPricesRequest(instrumentId = uids)).lastPrices
            }
            val closeDeferred = async {
                api.getClosePrices(
                    GetClosePricesRequest(instruments = uids.map { ClosePriceInstrument(it) }),
                ).closePrices
            }
            val last = lastDeferred.await().associateBy { it.instrumentUid }
            val close = closeDeferred.await().associateBy { it.instrumentUid }

            uids.mapNotNull { uid ->
                val lastPrice = last[uid]?.price?.toDoubleOrZero() ?: return@mapNotNull null
                val closeEntry = close[uid]
                // Prefer the evening-session close (the base the broker uses for
                // today's change); fall back to the main-session close.
                val prevClose = closeEntry?.eveningSessionPrice?.toDoubleOrZero()
                    ?.takeIf { it > 0.0 }
                    ?: closeEntry?.price?.toDoubleOrZero()
                    ?: 0.0
                val changePercent = if (prevClose > 0.0) {
                    (lastPrice - prevClose) / prevClose * 100.0
                } else {
                    0.0
                }
                uid to Quote(lastPrice = lastPrice, dayChangePercent = changePercent)
            }.toMap()
        }
    }

    /**
     * Best-effort "market overview" — a fixed set of well-known instruments
     * (USD, MOEX index, gold, Brent, Bitcoin) resolved by search query, then
     * quoted. Items the API doesn't expose are silently skipped, so the result
     * may be shorter than the request.
     */
    suspend fun getMarketOverview(): ApiResult<List<OverviewItem>> = safeCall {
        coroutineScope {
            val resolved = MARKET_OVERVIEW.map { spec ->
                async {
                    val candidates = api.findInstrument(
                        FindInstrumentRequest(query = spec.query, apiTradeAvailableFlag = false),
                    ).instruments.filter { it.uid.isNotBlank() }
                    // Resolve precisely: exact ticker when given, else a name
                    // keyword match (avoids e.g. "BR" matching Broadridge instead
                    // of Brent oil), else the first result.
                    val instrument = when {
                        spec.exactTicker != null ->
                            candidates.firstOrNull { it.ticker.equals(spec.exactTicker, ignoreCase = true) }
                        spec.nameKeyword != null -> {
                            val byName = candidates.filter { it.name.contains(spec.nameKeyword, ignoreCase = true) }
                            // Prefer a candidate priced in the hinted currency (e.g. gold in USD).
                            byName.firstOrNull { it.currency.equals(spec.currencyHint, ignoreCase = true) }
                                ?: byName.firstOrNull()
                        }
                        else -> candidates.firstOrNull()
                    }
                    instrument?.let { spec to it }
                }
            }.awaitAll().filterNotNull()

            val uids = resolved.map { it.second.uid }
            val quotes = runCatching {
                getQuotesInternal(uids)
            }.getOrDefault(emptyMap())

            resolved.mapNotNull { (spec, instrument) ->
                val quote = quotes[instrument.uid] ?: return@mapNotNull null
                OverviewItem(
                    title = spec.title,
                    uid = instrument.uid,
                    logoUrl = com.tinvestlite.util.InstrumentLogo.url(instrument.brand),
                    currency = instrument.currency.ifBlank { spec.currencyHint },
                    quote = quote,
                )
            }
        }
    }

    private suspend fun getQuotesInternal(uids: List<String>): Map<String, Quote> {
        if (uids.isEmpty()) return emptyMap()
        return (getQuotes(uids) as? ApiResult.Success)?.data ?: emptyMap()
    }

    // ---- Orders (sandbox only — real mode is read-only) ----

    suspend fun postOrder(
        accountId: String,
        instrumentUid: String,
        lots: Long,
        isBuy: Boolean,
        isMarket: Boolean,
        price: Quotation?,
    ): ApiResult<PostOrderResponse> = guardSandbox {
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

    suspend fun getOrders(accountId: String): ApiResult<List<OrderState>> = guardSandbox {
        api.getSandboxOrders(GetOrdersRequest(accountId = accountId)).orders
    }

    suspend fun cancelOrder(accountId: String, orderId: String): ApiResult<Unit> = guardSandbox {
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

    /** Refuses to run trading actions outside sandbox; everything else via [safeCall]. */
    private suspend fun <T> guardSandbox(block: suspend () -> T): ApiResult<T> {
        if (mode.isReal) {
            return ApiResult.Error("Торговля недоступна в режиме реального счёта (только чтение).")
        }
        return safeCall(block)
    }

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
            else -> detail?.let { translateApiMessage(it) } ?: "Ошибка API: ${e.code()}"
        }
    }

    /** Map common English gateway messages to friendlier Russian text. */
    private fun translateApiMessage(message: String): String {
        val lower = message.lowercase()
        return when {
            "not available for trading" in lower ->
                "Инструмент недоступен для торговли (в песочнице доступны не все бумаги)."
            "instrument is not found" in lower || "not found" in lower ->
                "Инструмент не найден."
            "not enough" in lower || "insufficient" in lower ->
                "Недостаточно средств на счёте."
            "market is not opened" in lower || "market is closed" in lower ->
                "Рынок сейчас закрыт."
            else -> message
        }
    }
}

/** A single account paired with its portfolio — used for the consolidated view. */
data class AccountPortfolio(
    val account: Account,
    val portfolio: PortfolioResponse,
)

/** Current price and day change, used to show quotes in lists. */
data class Quote(
    val lastPrice: Double,
    val dayChangePercent: Double,
)

/** A resolved market-overview entry shown on the portfolio screen. */
data class OverviewItem(
    val title: String,
    val uid: String,
    val logoUrl: String?,
    val currency: String,
    val quote: Quote,
)

/** What to look up for the market overview and how to label it. */
private data class OverviewSpec(
    val title: String,
    val query: String,
    val currencyHint: String,
    /** Exact ticker to prefer among results; null → match by name keyword. */
    val exactTicker: String? = null,
    /** Name keyword used when [exactTicker] is null (case-insensitive). */
    val nameKeyword: String? = null,
)

/**
 * Well-known instruments shown in the overview. Resolved by search, so the
 * exact instrument depends on what the API returns; misses are skipped.
 */
private val MARKET_OVERVIEW = listOf(
    OverviewSpec("Доллар США", "USD000UTSTOM", "rub", exactTicker = "USD000UTSTOM"),
    OverviewSpec("Индекс МосБиржи", "IMOEX", "rub", exactTicker = "IMOEX"),
    OverviewSpec("Золото", "XAU", "usd", exactTicker = "XAU"),
    OverviewSpec("Нефть Brent", "LCOC1", "usd", exactTicker = "LCOC1"),
    OverviewSpec("Биткоин", "BTCUSD", "usd", nameKeyword = "bitcoin"),
)

private fun Quotation.toDoubleOrZero(): Double = units + nano / 1_000_000_000.0
