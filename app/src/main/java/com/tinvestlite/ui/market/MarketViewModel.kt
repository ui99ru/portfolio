package com.tinvestlite.ui.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinvestlite.data.ApiResult
import com.tinvestlite.data.remote.dto.InstrumentShort
import com.tinvestlite.data.repository.InvestRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Browsable catalog sections shown as chips above the list. */
enum class MarketCategory(val label: String) {
    BlueChips("Голубые фишки"),
    Shares("Акции"),
    Ofz("ОФЗ"),
    Bonds("Облигации"),
    Etfs("Фонды"),
}

/**
 * Blue chips = constituents of the MOEX Index (iMOEX). The API has no dedicated
 * endpoint, so we curate the index tickers and filter them out of the full
 * Russian share list. Keep in sync with the official iMOEX basket.
 */
private val IMOEX_TICKERS = setOf(
    "ALRS", "AFLT", "AFKS", "CHMF", "ENPG", "FEES", "GAZP", "GMKN", "HYDR",
    "IRAO", "LKOH", "MGNT", "MOEX", "MTSS", "NLMK", "NVTK", "PHOR", "PLZL",
    "ROSN", "RTKM", "RUAL", "SBER", "SBERP", "SGZH", "SNGS", "SNGSP", "TATN",
    "TATNP", "TRNFP", "VTBR", "YDEX", "T", "SIBN", "MAGN", "POSI", "UPRO",
    "BSPB", "SVCB", "MTLR",
)

data class MarketUiState(
    val query: String = "",
    val category: MarketCategory = MarketCategory.BlueChips,
    val isLoading: Boolean = false,
    val results: List<InstrumentShort> = emptyList(),
    val error: String? = null,
) {
    /** True when the user is actively searching (search overrides category). */
    val isSearching: Boolean get() = query.trim().length >= 2
}

class MarketViewModel(
    private val repository: InvestRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MarketUiState())
    val state: StateFlow<MarketUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var categoryJob: Job? = null

    // Cache full catalog lists so switching chips doesn't re-hit the API.
    private val sharesCache = mutableListOf<InstrumentShort>()
    private val bondsCache = mutableListOf<InstrumentShort>()
    private val etfsCache = mutableListOf<InstrumentShort>()

    init {
        selectCategory(MarketCategory.BlueChips)
    }

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query)
        searchJob?.cancel()
        if (query.trim().length < 2) {
            // Back to category browsing.
            categoryJob?.cancel()
            loadCategory(_state.value.category)
            return
        }
        searchJob = viewModelScope.launch {
            delay(350) // debounce
            _state.value = _state.value.copy(isLoading = true, error = null)
            when (val result = repository.findInstruments(query.trim())) {
                is ApiResult.Success -> _state.value = _state.value.copy(
                    isLoading = false,
                    // Russian market only for now — exclude foreign assets.
                    results = result.data
                        .filter { isRussian(it) }
                        .distinctBy { it.uid }
                        .take(50),
                )
                is ApiResult.Error -> _state.value = _state.value.copy(
                    isLoading = false,
                    error = result.message,
                )
            }
        }
    }

    fun selectCategory(category: MarketCategory) {
        _state.value = _state.value.copy(category = category)
        if (_state.value.isSearching) return // search takes precedence
        loadCategory(category)
    }

    fun retry() {
        if (_state.value.isSearching) onQueryChange(_state.value.query)
        else loadCategory(_state.value.category)
    }

    private fun loadCategory(category: MarketCategory) {
        categoryJob?.cancel()
        _state.value = _state.value.copy(isLoading = true, error = null)
        categoryJob = viewModelScope.launch {
            val result: ApiResult<List<InstrumentShort>> = when (category) {
                MarketCategory.BlueChips, MarketCategory.Shares -> loadShares()
                MarketCategory.Ofz, MarketCategory.Bonds -> loadBonds()
                MarketCategory.Etfs -> loadEtfs()
            }
            _state.value = when (result) {
                is ApiResult.Success -> _state.value.copy(
                    isLoading = false,
                    results = applyCategoryFilter(category, result.data),
                )
                is ApiResult.Error -> _state.value.copy(isLoading = false, error = result.message)
            }
        }
    }

    private fun applyCategoryFilter(
        category: MarketCategory,
        items: List<InstrumentShort>,
    ): List<InstrumentShort> {
        // Russian market only for now (foreign assets are deferred).
        val russian = items.filter { isRussian(it) }
        return when (category) {
            MarketCategory.BlueChips -> russian.filter { it.ticker in IMOEX_TICKERS }
            MarketCategory.Ofz -> russian.filter { isOfz(it) }
            else -> russian
        }
    }

    /**
     * Russian-market instrument. Prefer the explicit country of risk; when the
     * API omits it, fall back to a RUB-denominated MOEX board (TQ* class codes).
     */
    private fun isRussian(item: InstrumentShort): Boolean {
        if (item.countryOfRisk.isNotBlank()) {
            return item.countryOfRisk.equals("RU", ignoreCase = true)
        }
        val moexBoard = item.classCode.startsWith("TQ", ignoreCase = true)
        return moexBoard || item.currency.equals("rub", ignoreCase = true)
    }

    /** OFZ — federal loan bonds; identified by the "SU" ticker prefix or the name. */
    private fun isOfz(bond: InstrumentShort): Boolean =
        bond.ticker.startsWith("SU", ignoreCase = true) ||
            bond.name.contains("ОФЗ", ignoreCase = true) ||
            bond.name.contains("ОФЗ-", ignoreCase = true)

    private suspend fun loadShares(): ApiResult<List<InstrumentShort>> {
        if (sharesCache.isNotEmpty()) return ApiResult.Success(sharesCache)
        return repository.getShares().also { if (it is ApiResult.Success) sharesCache.addAll(it.data) }
    }

    private suspend fun loadBonds(): ApiResult<List<InstrumentShort>> {
        if (bondsCache.isNotEmpty()) return ApiResult.Success(bondsCache)
        return repository.getBonds().also { if (it is ApiResult.Success) bondsCache.addAll(it.data) }
    }

    private suspend fun loadEtfs(): ApiResult<List<InstrumentShort>> {
        if (etfsCache.isNotEmpty()) return ApiResult.Success(etfsCache)
        return repository.getEtfs().also { if (it is ApiResult.Success) etfsCache.addAll(it.data) }
    }
}
