package com.tinvestlite.ui.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tinvestlite.data.ApiResult
import com.tinvestlite.data.remote.dto.InstrumentShort
import com.tinvestlite.data.repository.InvestRepository
import com.tinvestlite.data.repository.Quote
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Top-level market section (macro). */
enum class MarketSection(val label: String) {
    Shares("Акции"),
    Bonds("Облигации"),
    Etfs("Фонды"),
}

/**
 * Sub-section within a [MarketSection]. Shares and bonds have sub-filters;
 * ETFs have none (empty list → the sub-chip row is hidden).
 */
enum class SubSection(val label: String) {
    // Shares
    BlueChips("Голубые фишки"),
    AllShares("Все"),

    // Bonds
    Ofz("ОФЗ"),
    Corporate("Корпоративные"),
    Regions("Регионы"),
}

fun MarketSection.subSections(): List<SubSection> = when (this) {
    MarketSection.Shares -> listOf(SubSection.BlueChips, SubSection.AllShares)
    MarketSection.Bonds -> listOf(SubSection.Ofz, SubSection.Corporate, SubSection.Regions)
    MarketSection.Etfs -> emptyList()
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

/** Keywords that mark a regional/municipal (sub-federal) bond by issuer name. */
private val REGION_KEYWORDS = listOf(
    "обл", "край", "Республик", "респ", "город", "г.", "округ", "АО ", "область",
)

/** Max instruments rendered (and quoted) per section to keep lists snappy. */
private const val DISPLAY_LIMIT = 100

data class MarketItem(
    val instrument: InstrumentShort,
    val quote: Quote? = null,
)

data class MarketUiState(
    val query: String = "",
    val section: MarketSection = MarketSection.Shares,
    val sub: SubSection = SubSection.BlueChips,
    val isLoading: Boolean = false,
    val items: List<MarketItem> = emptyList(),
    val error: String? = null,
) {
    /** True when the user is actively searching (search overrides sections). */
    val isSearching: Boolean get() = query.trim().length >= 2
    val subSections: List<SubSection> get() = section.subSections()
}

class MarketViewModel(
    private val repository: InvestRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MarketUiState())
    val state: StateFlow<MarketUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var loadJob: Job? = null

    // Cache full catalog lists so switching chips doesn't re-hit the API.
    private val sharesCache = mutableListOf<InstrumentShort>()
    private val bondsCache = mutableListOf<InstrumentShort>()
    private val etfsCache = mutableListOf<InstrumentShort>()

    init {
        load()
    }

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query)
        searchJob?.cancel()
        if (query.trim().length < 2) {
            load()
            return
        }
        searchJob = viewModelScope.launch {
            delay(350) // debounce
            _state.value = _state.value.copy(isLoading = true, error = null)
            when (val result = repository.findInstruments(query.trim())) {
                is ApiResult.Success -> {
                    // No country filter on search: the user typed a specific
                    // query, so show whatever the API returns.
                    val list = result.data
                        .distinctBy { it.uid }
                        .take(DISPLAY_LIMIT)
                    publishAndQuote(list)
                }
                is ApiResult.Error -> _state.value = _state.value.copy(
                    isLoading = false,
                    error = result.message,
                )
            }
        }
    }

    fun selectSection(section: MarketSection) {
        if (section == _state.value.section && !_state.value.isSearching) return
        // Reset to the section's first sub-section.
        val firstSub = section.subSections().firstOrNull() ?: SubSection.AllShares
        _state.value = _state.value.copy(section = section, sub = firstSub, query = "")
        searchJob?.cancel()
        load()
    }

    fun selectSub(sub: SubSection) {
        if (sub == _state.value.sub && !_state.value.isSearching) return
        _state.value = _state.value.copy(sub = sub)
        load()
    }

    fun retry() {
        if (_state.value.isSearching) onQueryChange(_state.value.query) else load()
    }

    private fun load() {
        loadJob?.cancel()
        _state.value = _state.value.copy(isLoading = true, error = null)
        loadJob = viewModelScope.launch {
            val result = when (_state.value.section) {
                MarketSection.Shares -> loadShares()
                MarketSection.Bonds -> loadBonds()
                MarketSection.Etfs -> loadEtfs()
            }
            when (result) {
                is ApiResult.Success -> {
                    val list = applyFilter(result.data).take(DISPLAY_LIMIT)
                    publishAndQuote(list)
                }
                is ApiResult.Error -> _state.value =
                    _state.value.copy(isLoading = false, error = result.message)
            }
        }
    }

    /** Show instruments immediately, then enrich with quotes when they arrive. */
    private suspend fun publishAndQuote(list: List<InstrumentShort>) {
        _state.value = _state.value.copy(
            isLoading = false,
            items = list.map { MarketItem(it) },
        )
        val uids = list.map { it.uid }.filter { it.isNotBlank() }
        when (val quotes = repository.getQuotes(uids)) {
            is ApiResult.Success -> {
                val byUid = quotes.data
                _state.value = _state.value.copy(
                    items = _state.value.items.map { it.copy(quote = byUid[it.instrument.uid]) },
                )
            }
            is ApiResult.Error -> Unit // keep the list without quotes
        }
    }

    private fun applyFilter(items: List<InstrumentShort>): List<InstrumentShort> {
        // Russian market only for now (foreign assets are deferred).
        val russian = items.filter { isRussian(it) }
        return when (_state.value.sub) {
            SubSection.BlueChips -> russian.filter { it.ticker in IMOEX_TICKERS }
            SubSection.AllShares -> russian
            SubSection.Ofz -> russian.filter { isOfz(it) }
            SubSection.Regions -> russian.filter { isRegion(it) }
            SubSection.Corporate -> russian.filter { !isOfz(it) && !isRegion(it) }
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
            bond.name.contains("ОФЗ", ignoreCase = true)

    /** Regional/municipal bonds — heuristic match on the issuer name. */
    private fun isRegion(bond: InstrumentShort): Boolean =
        REGION_KEYWORDS.any { bond.name.contains(it, ignoreCase = true) }

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
