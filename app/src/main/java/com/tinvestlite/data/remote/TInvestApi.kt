package com.tinvestlite.data.remote

import com.tinvestlite.data.remote.dto.CancelOrderRequest
import com.tinvestlite.data.remote.dto.CancelOrderResponse
import com.tinvestlite.data.remote.dto.CloseSandboxAccountRequest
import com.tinvestlite.data.remote.dto.CloseSandboxAccountResponse
import com.tinvestlite.data.remote.dto.FindInstrumentRequest
import com.tinvestlite.data.remote.dto.FindInstrumentResponse
import com.tinvestlite.data.remote.dto.GetAccountsRequest
import com.tinvestlite.data.remote.dto.GetAccountsResponse
import com.tinvestlite.data.remote.dto.GetCandlesRequest
import com.tinvestlite.data.remote.dto.GetCandlesResponse
import com.tinvestlite.data.remote.dto.GetLastPricesRequest
import com.tinvestlite.data.remote.dto.GetLastPricesResponse
import com.tinvestlite.data.remote.dto.GetOrderBookRequest
import com.tinvestlite.data.remote.dto.GetOrderBookResponse
import com.tinvestlite.data.remote.dto.GetOrdersRequest
import com.tinvestlite.data.remote.dto.GetOrdersResponse
import com.tinvestlite.data.remote.dto.InstrumentRequest
import com.tinvestlite.data.remote.dto.InstrumentResponse
import com.tinvestlite.data.remote.dto.InstrumentsListResponse
import com.tinvestlite.data.remote.dto.InstrumentsRequest
import com.tinvestlite.data.remote.dto.OpenSandboxAccountRequest
import com.tinvestlite.data.remote.dto.OpenSandboxAccountResponse
import com.tinvestlite.data.remote.dto.OperationsRequest
import com.tinvestlite.data.remote.dto.OperationsResponse
import com.tinvestlite.data.remote.dto.PortfolioRequest
import com.tinvestlite.data.remote.dto.PortfolioResponse
import com.tinvestlite.data.remote.dto.PostOrderRequest
import com.tinvestlite.data.remote.dto.PostOrderResponse
import com.tinvestlite.data.remote.dto.SandboxPayInRequest
import com.tinvestlite.data.remote.dto.SandboxPayInResponse
import com.tinvestlite.data.remote.dto.WithdrawLimitsRequest
import com.tinvestlite.data.remote.dto.WithdrawLimitsResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * T-Invest API over the public REST/JSON gateway.
 * Every gRPC method is exposed as a POST endpoint named
 * `tinkoff.public.invest.api.contract.v1.<Service>/<Method>`.
 * Auth is a Bearer token (see [AuthInterceptor]).
 *
 * Sandbox endpoints operate on virtual accounts; market-data and instruments
 * endpoints work with the same token and return real exchange data.
 */
interface TInvestApi {

    // ---- Sandbox: accounts ----

    @POST("tinkoff.public.invest.api.contract.v1.SandboxService/GetSandboxAccounts")
    suspend fun getSandboxAccounts(@Body body: GetAccountsRequest): GetAccountsResponse

    @POST("tinkoff.public.invest.api.contract.v1.SandboxService/OpenSandboxAccount")
    suspend fun openSandboxAccount(@Body body: OpenSandboxAccountRequest): OpenSandboxAccountResponse

    @POST("tinkoff.public.invest.api.contract.v1.SandboxService/CloseSandboxAccount")
    suspend fun closeSandboxAccount(@Body body: CloseSandboxAccountRequest): CloseSandboxAccountResponse

    @POST("tinkoff.public.invest.api.contract.v1.SandboxService/SandboxPayIn")
    suspend fun sandboxPayIn(@Body body: SandboxPayInRequest): SandboxPayInResponse

    @POST("tinkoff.public.invest.api.contract.v1.SandboxService/GetSandboxWithdrawLimits")
    suspend fun getSandboxWithdrawLimits(@Body body: WithdrawLimitsRequest): WithdrawLimitsResponse

    // ---- Sandbox: portfolio & operations ----

    @POST("tinkoff.public.invest.api.contract.v1.SandboxService/GetSandboxPortfolio")
    suspend fun getSandboxPortfolio(@Body body: PortfolioRequest): PortfolioResponse

    @POST("tinkoff.public.invest.api.contract.v1.SandboxService/GetSandboxOperations")
    suspend fun getSandboxOperations(@Body body: OperationsRequest): OperationsResponse

    // ---- Sandbox: orders ----

    @POST("tinkoff.public.invest.api.contract.v1.SandboxService/PostSandboxOrder")
    suspend fun postSandboxOrder(@Body body: PostOrderRequest): PostOrderResponse

    @POST("tinkoff.public.invest.api.contract.v1.SandboxService/GetSandboxOrders")
    suspend fun getSandboxOrders(@Body body: GetOrdersRequest): GetOrdersResponse

    @POST("tinkoff.public.invest.api.contract.v1.SandboxService/CancelSandboxOrder")
    suspend fun cancelSandboxOrder(@Body body: CancelOrderRequest): CancelOrderResponse

    // ---- Real account: read-only (Users/Operations/Portfolio services) ----

    @POST("tinkoff.public.invest.api.contract.v1.UsersService/GetAccounts")
    suspend fun getRealAccounts(@Body body: GetAccountsRequest): GetAccountsResponse

    @POST("tinkoff.public.invest.api.contract.v1.OperationsService/GetPortfolio")
    suspend fun getRealPortfolio(@Body body: PortfolioRequest): PortfolioResponse

    @POST("tinkoff.public.invest.api.contract.v1.OperationsService/GetOperations")
    suspend fun getRealOperations(@Body body: OperationsRequest): OperationsResponse

    // ---- Instruments ----

    @POST("tinkoff.public.invest.api.contract.v1.InstrumentsService/FindInstrument")
    suspend fun findInstrument(@Body body: FindInstrumentRequest): FindInstrumentResponse

    @POST("tinkoff.public.invest.api.contract.v1.InstrumentsService/GetInstrumentBy")
    suspend fun getInstrumentBy(@Body body: InstrumentRequest): InstrumentResponse

    @POST("tinkoff.public.invest.api.contract.v1.InstrumentsService/Shares")
    suspend fun getShares(@Body body: InstrumentsRequest): InstrumentsListResponse

    @POST("tinkoff.public.invest.api.contract.v1.InstrumentsService/Bonds")
    suspend fun getBonds(@Body body: InstrumentsRequest): InstrumentsListResponse

    @POST("tinkoff.public.invest.api.contract.v1.InstrumentsService/Etfs")
    suspend fun getEtfs(@Body body: InstrumentsRequest): InstrumentsListResponse

    // ---- Market data (real quotes) ----

    @POST("tinkoff.public.invest.api.contract.v1.MarketDataService/GetCandles")
    suspend fun getCandles(@Body body: GetCandlesRequest): GetCandlesResponse

    @POST("tinkoff.public.invest.api.contract.v1.MarketDataService/GetLastPrices")
    suspend fun getLastPrices(@Body body: GetLastPricesRequest): GetLastPricesResponse

    @POST("tinkoff.public.invest.api.contract.v1.MarketDataService/GetOrderBook")
    suspend fun getOrderBook(@Body body: GetOrderBookRequest): GetOrderBookResponse
}
