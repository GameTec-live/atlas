package org.gtlv.car_common.screen

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.ScreenManager
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.gtlv.car_common.R
import org.gtlv.core.geoservice.AddressSuggestion
import org.gtlv.core.geoservice.GeoServiceRepository
import org.gtlv.core.geoservice.ResolveAddressResult

/** Address lookup with a host-managed keyboard for the assign-job fields. */
internal class AssignJobAddressSearchScreen(
    carContext: CarContext,
    private val title: String,
    initialValue: String,
    private val geoServiceRepository: GeoServiceRepository,
    private val onSuggestionSelected: (AddressSuggestion) -> Unit,
) : Screen(carContext), DefaultLifecycleObserver {
    private val screenScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate,
    )
    private val historyStore = AddressSearchHistoryStore(carContext)
    private var searchJob: Job? = null
    private var query = initialValue
    private var suggestions: List<AddressSuggestion> = emptyList()
    private var isLoading = false
    private var hasSearched = false
    private var hasError = false

    init {
        lifecycle.addObserver(this)
    }

    override fun onGetTemplate(): Template {
        val builder = SearchTemplate.Builder(
            object : SearchTemplate.SearchCallback {
                override fun onSearchTextChanged(searchText: String) {
                    search(searchText)
                }

                override fun onSearchSubmitted(searchText: String) {
                    search(searchText)
                }
            },
        )
            .setInitialSearchText(query)
            .setSearchHint(title)
            .setShowKeyboardByDefault(true)
            .setHeaderAction(Action.BACK)

        if (isLoading) {
            builder.setLoading(true)
        } else {
            resultList()?.let(builder::setItemList)
        }

        return builder.build()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        searchJob?.cancel()
        screenScope.cancel()
        lifecycle.removeObserver(this)
    }

    private fun search(updatedQuery: String) {
        if (query == updatedQuery && (isLoading || hasSearched)) return

        query = updatedQuery
        searchJob?.cancel()
        suggestions = emptyList()
        hasSearched = false
        hasError = false

        if (updatedQuery.isBlank()) {
            isLoading = false
            invalidate()
            return
        }

        isLoading = true
        invalidate()
        searchJob = screenScope.launch {
            val result = geoServiceRepository.resolveAddress(updatedQuery)
            currentCoroutineContext().ensureActive()
            if (query != updatedQuery) return@launch

            suggestions = if (result is ResolveAddressResult.Success) {
                result.suggestions
            } else {
                emptyList()
            }
            isLoading = false
            hasSearched = true
            hasError = result !is ResolveAddressResult.Success
            invalidate()
        }
    }

    private fun resultList(): ItemList? {
        if (!hasSearched) {
            return recentAddressList()
        }

        val list = ItemList.Builder()
        when {
            hasError -> list.addItem(statusRow(R.string.address_search_error))
            suggestions.isEmpty() -> list.addItem(
                statusRow(R.string.address_search_no_results),
            )
            else -> suggestions
                .take(contentLimit())
                .forEach { suggestion ->
                    list.addItem(addressRow(suggestion))
                }
        }
        return list.build()
    }

    private fun recentAddressList(): ItemList? {
        if (query.isNotBlank()) return null
        val recentAddresses = historyStore.recentAddresses()
            .take(contentLimit())
        if (recentAddresses.isEmpty()) return null

        return ItemList.Builder().also { list ->
            recentAddresses.forEach { suggestion ->
                list.addItem(
                    addressRow(
                        suggestion = suggestion,
                        supportingText = carContext.getString(
                            R.string.address_search_recent,
                        ),
                    ),
                )
            }
        }.build()
    }

    private fun addressRow(
        suggestion: AddressSuggestion,
        supportingText: String? = null,
    ): Row = Row.Builder()
        .setTitle(suggestion.displayName)
        .apply {
            supportingText?.let(::addText)
        }
        .setOnClickListener {
            historyStore.record(suggestion)
            onSuggestionSelected(suggestion)
            carContext
                .getCarService(ScreenManager::class.java)
                .pop()
        }
        .build()

    private fun statusRow(stringResource: Int): Row = Row.Builder()
        .setTitle(carContext.getString(stringResource))
        .build()

    private fun contentLimit(): Int = carContext
        .getCarService(ConstraintManager::class.java)
        .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        .coerceAtLeast(1)
}
