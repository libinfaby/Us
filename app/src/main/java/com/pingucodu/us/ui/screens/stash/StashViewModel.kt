package com.pingucodu.us.ui.screens.stash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingucodu.us.data.auth.AuthRepository
import com.pingucodu.us.data.dates.DatesRepository
import com.pingucodu.us.data.dates.SaveDateResult
import com.pingucodu.us.data.money.AddMemoryResult
import com.pingucodu.us.data.money.CreateHangoutResult
import com.pingucodu.us.data.money.DeleteHangoutResult
import com.pingucodu.us.data.money.DeleteMemoryResult
import com.pingucodu.us.data.money.ExpenseRepository
import com.pingucodu.us.data.money.HangoutsResult
import com.pingucodu.us.data.money.UpdateHangoutResult
import com.pingucodu.us.data.money.UpdateMemoryResult
import com.pingucodu.us.data.network.HangoutDto
import com.pingucodu.us.data.network.HangoutMemoryDto
import com.pingucodu.us.data.network.LinkPreviewDto
import com.pingucodu.us.data.network.MovieSearchResultDto
import com.pingucodu.us.data.network.SpecialDateRequest
import com.pingucodu.us.data.network.StashItemDto
import com.pingucodu.us.data.network.StashItemRequest
import com.pingucodu.us.data.stash.AddStashItemResult
import com.pingucodu.us.data.stash.DeleteStashItemResult
import com.pingucodu.us.data.stash.LinkPreviewResult
import com.pingucodu.us.data.stash.MovieSearchResult
import com.pingucodu.us.data.stash.StashItemsResult
import com.pingucodu.us.data.stash.StashRepository
import com.pingucodu.us.data.stash.StashTagsResult
import com.pingucodu.us.data.stash.ToggleStashItemResult
import com.pingucodu.us.data.stash.UpdateStashItemResult
import com.pingucodu.us.ui.screens.dates.KIND_COUNTDOWN
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StashTypeSpec(val value: String, val label: String)

val STASH_TYPES = listOf(
    StashTypeSpec("movie", "movie"),
    StashTypeSpec("link", "link"),
    StashTypeSpec("place", "place"),
    StashTypeSpec("note", "note"),
    StashTypeSpec("todo", "to-do"),
)

/** Stash types that can carry a link with a fetched title + description. */
val LINK_PREVIEW_TYPES = setOf("movie", "place")

const val HANGOUTS_CATEGORY = "hangouts"
const val ALL_CATEGORY = "all"
private const val PAGE_SIZE = 10

data class StashUiState(
    val isLoading: Boolean = true,
    val items: List<StashItemDto> = emptyList(),
    val tagsByType: Map<String, List<String>> = emptyMap(),
    val allTags: List<String> = emptyList(),
    val category: String = ALL_CATEGORY,
    val tag: String? = null,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val loadMoreFailed: Boolean = false,
    val currentUsername: String? = null,
    val errorMessage: String? = null,
    val showAddDialog: Boolean = false,
    val editingItem: StashItemDto? = null,
    val dialogError: String? = null,
    val isSubmitting: Boolean = false,
    /** A link shared into the app - the add sheet opens with it and this type preselected. */
    val prefillUrl: String? = null,
    val prefillType: String? = null,
    /** The latest fetched preview; the open form applies it to its fields once. */
    val linkPreview: LinkPreviewDto? = null,
    val isFetchingPreview: Boolean = false,
    val previewError: String? = null,
    /** Films matching the typed movie name; null = no search shown. */
    val movieResults: List<MovieSearchResultDto>? = null,
    val isSearchingMovies: Boolean = false,
    val movieSearchError: String? = null,

    val hangouts: List<HangoutDto> = emptyList(),
    val hangoutsLoaded: Boolean = false,
    val hangoutsLoading: Boolean = false,
    val showHangoutDialog: Boolean = false,
    val editingHangout: HangoutDto? = null,
    val hangoutDialogError: String? = null,
    val isHangoutSubmitting: Boolean = false,

    val memoryDialogHangout: HangoutDto? = null,
    val editingMemory: HangoutMemoryDto? = null,
    val memoryDialogError: String? = null,
    val isMemorySubmitting: Boolean = false,
) {
    val typeFilter: String? get() = if (category == ALL_CATEGORY || category == HANGOUTS_CATEGORY) null else category
    val isHangoutCategory: Boolean get() = category == HANGOUTS_CATEGORY

    /** Tags that can narrow the current category - every tag under "all", else just that type's -
     * alphabetical, so a tag is easy to find in the filter row. */
    val filterTags: List<String>
        get() = (typeFilter?.let { tagsByType[it].orEmpty() } ?: allTags).sortedWith(String.CASE_INSENSITIVE_ORDER)
}

@HiltViewModel
class StashViewModel @Inject constructor(
    private val stashRepository: StashRepository,
    private val expenseRepository: ExpenseRepository,
    private val datesRepository: DatesRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StashUiState())
    val uiState: StateFlow<StashUiState> = _uiState.asStateFlow()

    private var itemsJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.loggedInUsername.collect { username ->
                _uiState.update { it.copy(currentUsername = username) }
            }
        }
        refresh()
        refreshAllTags()
    }

    fun setCategory(category: String) {
        // Switching type filters is a full data-set swap, not a refresh of what's on screen -
        // drop the stale items right away so the list falls back to skeleton cards instead of
        // briefly showing the previous category's items under a spurious pull-to-refresh spinner.
        // A tag filter carries over only if the new category actually has that tag.
        _uiState.update {
            val next = it.copy(category = category, items = emptyList())
            next.copy(tag = it.tag?.takeIf { tag -> tag in next.filterTags })
        }
        if (category == HANGOUTS_CATEGORY) {
            if (!_uiState.value.hangoutsLoaded) refreshHangouts()
        } else {
            refresh()
        }
    }

    /** Tapping the selected tag again clears it. */
    fun setTag(tag: String?) {
        val next = tag?.takeIf { it != _uiState.value.tag }
        _uiState.update { it.copy(tag = next, items = emptyList()) }
        refresh()
    }

    /** Filter change / pull-to-refresh: start over from the first page. */
    fun refresh() = loadItems(limit = PAGE_SIZE)

    /** After a mutation, re-fetch everything already scrolled in (in one request) rather than
     * snapping back to page one, so the list updates in place and keeps its scroll position. */
    private fun reloadLoaded() = loadItems(limit = maxOf(PAGE_SIZE, _uiState.value.items.size))

    private fun loadItems(limit: Int) {
        val state = _uiState.value
        itemsJob?.cancel()
        itemsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, isLoadingMore = false, loadMoreFailed = false, errorMessage = null) }
            val result = stashRepository.getItems(
                status = "everything",
                type = state.typeFilter,
                tag = state.tag,
                limit = limit,
                offset = 0,
            )
            when (result) {
                is StashItemsResult.Success ->
                    _uiState.update {
                        it.copy(isLoading = false, items = result.items, endReached = result.items.size < limit)
                    }
                is StashItemsResult.NetworkError ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.message) }
            }
        }
    }

    /** Appends the next page - called as the list nears its bottom, Instagram-style. */
    fun loadMore() {
        val state = _uiState.value
        if (state.isHangoutCategory || state.endReached || state.isLoading || state.isLoadingMore) return
        if (itemsJob?.isActive == true) return
        itemsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, loadMoreFailed = false) }
            val result = stashRepository.getItems(
                status = "everything",
                type = state.typeFilter,
                tag = state.tag,
                limit = PAGE_SIZE,
                offset = state.items.size,
            )
            when (result) {
                is StashItemsResult.Success ->
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            // Offsets can shift if the partner adds something mid-scroll - never show an item twice.
                            items = (it.items + result.items).distinctBy { item -> item.id },
                            endReached = result.items.size < PAGE_SIZE,
                        )
                    }
                is StashItemsResult.NetworkError ->
                    _uiState.update { it.copy(isLoadingMore = false, loadMoreFailed = true, errorMessage = result.message) }
            }
        }
    }

    /** Tags ever used, grouped by item type — powers the "suggested tags" chips in the add/edit
     * form so previously-used tags are one tap away, without leaking tags from other types
     * (e.g. a "thriller" tag on a movie has no business suggesting itself on a place). Also
     * feeds the tag filter row. */
    fun refreshAllTags() {
        viewModelScope.launch {
            when (val result = stashRepository.getTags()) {
                is StashTagsResult.Success ->
                    _uiState.update {
                        it.copy(
                            tagsByType = result.tags.groupBy({ t -> t.type }, { t -> t.tag }),
                            allTags = result.tags.map { t -> t.tag }.distinct(),
                        )
                    }
                is StashTagsResult.NetworkError -> Unit
            }
        }
    }

    fun refreshHangouts() {
        viewModelScope.launch {
            _uiState.update { it.copy(hangoutsLoading = true, errorMessage = null) }
            when (val result = expenseRepository.getHangouts()) {
                is HangoutsResult.Success ->
                    _uiState.update { it.copy(hangoutsLoading = false, hangoutsLoaded = true, hangouts = result.hangouts) }
                is HangoutsResult.NetworkError ->
                    _uiState.update { it.copy(hangoutsLoading = false, errorMessage = result.message) }
            }
        }
    }

    fun openAddDialog() {
        _uiState.update { it.withFreshDialog().copy(showAddDialog = true) }
    }

    fun openEditDialog(item: StashItemDto) {
        _uiState.update { it.withFreshDialog().copy(showAddDialog = true, editingItem = item) }
    }

    /** A link shared from another app: guess movie vs place from the host, open the add sheet
     * with the link filled in, and start fetching its title + description straight away. */
    fun openAddDialogFromShare(url: String) {
        val type = if (isMapsLink(url)) "place" else "movie"
        _uiState.update { it.withFreshDialog().copy(showAddDialog = true, prefillUrl = url, prefillType = type) }
        fetchPreview(url)
    }

    fun dismissDialog() {
        _uiState.update { it.withFreshDialog() }
    }

    private fun StashUiState.withFreshDialog() = copy(
        showAddDialog = false,
        editingItem = null,
        dialogError = null,
        prefillUrl = null,
        prefillType = null,
        linkPreview = null,
        isFetchingPreview = false,
        previewError = null,
        movieResults = null,
        isSearchingMovies = false,
        movieSearchError = null,
    )

    private var previewJob: Job? = null

    fun fetchPreview(url: String) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            _uiState.update { it.copy(isFetchingPreview = true, previewError = null) }
            when (val result = stashRepository.getLinkPreview(url)) {
                is LinkPreviewResult.Success ->
                    _uiState.update { it.copy(isFetchingPreview = false, linkPreview = result.preview) }
                is LinkPreviewResult.Unreadable ->
                    _uiState.update { it.copy(isFetchingPreview = false, previewError = result.message) }
                is LinkPreviewResult.NetworkError ->
                    _uiState.update { it.copy(isFetchingPreview = false, previewError = result.message) }
            }
        }
    }

    private var movieSearchJob: Job? = null

    /** Looks up films by the name typed in the title field, for the user to pick one. */
    fun searchMovies(query: String) {
        movieSearchJob?.cancel()
        movieSearchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearchingMovies = true, movieSearchError = null, movieResults = null) }
            when (val result = stashRepository.searchMovies(query.trim())) {
                is MovieSearchResult.Success ->
                    _uiState.update { it.copy(isSearchingMovies = false, movieResults = result.movies) }
                is MovieSearchResult.Failed ->
                    _uiState.update { it.copy(isSearchingMovies = false, movieSearchError = result.message) }
            }
        }
    }

    /** Fills the form from a picked film, exactly like fetching its IMDb link would. */
    fun pickMovie(movie: MovieSearchResultDto) {
        movieSearchJob?.cancel()
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            _uiState.update {
                it.copy(movieResults = null, isSearchingMovies = false, movieSearchError = null, isFetchingPreview = true, previewError = null)
            }
            when (val result = stashRepository.getMoviePreview(movie.id)) {
                is LinkPreviewResult.Success ->
                    _uiState.update { it.copy(isFetchingPreview = false, linkPreview = result.preview) }
                is LinkPreviewResult.Unreadable ->
                    _uiState.update { it.copy(isFetchingPreview = false, previewError = result.message) }
                is LinkPreviewResult.NetworkError ->
                    _uiState.update { it.copy(isFetchingPreview = false, previewError = result.message) }
            }
        }
    }

    fun clearMovieResults() {
        movieSearchJob?.cancel()
        _uiState.update { it.copy(movieResults = null, isSearchingMovies = false, movieSearchError = null) }
    }

    private fun isMapsLink(url: String): Boolean {
        val lower = url.lowercase()
        return "maps.app.goo.gl" in lower || "goo.gl/maps" in lower || "maps.google." in lower || "google.com/maps" in lower
    }

    fun submitItem(request: StashItemRequest) {
        val editing = _uiState.value.editingItem
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, dialogError = null) }
            if (editing == null) {
                when (val result = stashRepository.addItem(request)) {
                    is AddStashItemResult.Success -> {
                        _uiState.update { it.copy(isSubmitting = false) }
                        dismissDialog()
                        reloadLoaded()
                        refreshAllTags()
                    }
                    is AddStashItemResult.ValidationError ->
                        _uiState.update { it.copy(isSubmitting = false, dialogError = result.message) }
                    is AddStashItemResult.NetworkError ->
                        _uiState.update { it.copy(isSubmitting = false, dialogError = result.message) }
                }
            } else {
                when (val result = stashRepository.updateItem(editing.id, request)) {
                    is UpdateStashItemResult.Success -> {
                        _uiState.update { it.copy(isSubmitting = false) }
                        dismissDialog()
                        reloadLoaded()
                        refreshAllTags()
                    }
                    is UpdateStashItemResult.ValidationError ->
                        _uiState.update { it.copy(isSubmitting = false, dialogError = result.message) }
                    is UpdateStashItemResult.NetworkError ->
                        _uiState.update { it.copy(isSubmitting = false, dialogError = result.message) }
                }
            }
        }
    }

    fun toggleItem(id: String) {
        viewModelScope.launch {
            when (val result = stashRepository.toggleItem(id)) {
                is ToggleStashItemResult.Success -> reloadLoaded()
                is ToggleStashItemResult.NetworkError -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch {
            when (val result = stashRepository.deleteItem(id)) {
                DeleteStashItemResult.Success -> {
                    reloadLoaded()
                    refreshAllTags()
                }
                is DeleteStashItemResult.NetworkError -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    /** Tags aren't their own entity - just strings embedded on each item - so renaming one
     * means walking every [type] item that has it and PATCHing its tag list. */
    fun renameTag(type: String, oldTag: String, newTag: String) {
        if (newTag == oldTag) return
        viewModelScope.launch {
            when (val result = stashRepository.getItems(status = "everything", type = type, tag = oldTag)) {
                is StashItemsResult.Success -> {
                    result.items.forEach { item ->
                        val updatedTags = item.tags.map { t -> if (t == oldTag) newTag else t }.distinct()
                        stashRepository.updateItem(item.id, StashItemRequest(tags = updatedTags))
                    }
                    _uiState.update { if (it.tag == oldTag) it.copy(tag = newTag) else it }
                    reloadLoaded()
                    refreshAllTags()
                }
                is StashItemsResult.NetworkError -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    /** Deletes a tag from every [type] item that has it - see [renameTag]. */
    fun deleteTag(type: String, tag: String) {
        viewModelScope.launch {
            when (val result = stashRepository.getItems(status = "everything", type = type, tag = tag)) {
                is StashItemsResult.Success -> {
                    result.items.forEach { item ->
                        stashRepository.updateItem(item.id, StashItemRequest(tags = item.tags - tag))
                    }
                    _uiState.update { if (it.tag == tag) it.copy(tag = null) else it }
                    reloadLoaded()
                    refreshAllTags()
                }
                is StashItemsResult.NetworkError -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun openAddHangoutDialog() {
        _uiState.update { it.copy(showHangoutDialog = true, editingHangout = null, hangoutDialogError = null) }
    }

    fun openEditHangoutDialog(hangout: HangoutDto) {
        _uiState.update { it.copy(showHangoutDialog = true, editingHangout = hangout, hangoutDialogError = null) }
    }

    fun dismissHangoutDialog() {
        _uiState.update { it.copy(showHangoutDialog = false, editingHangout = null, hangoutDialogError = null) }
    }

    /** [countdownDate] non-null = also add a countdown to that date once the new hangout is saved. */
    fun submitHangout(name: String, startDate: String?, endDate: String?, countdownDate: String? = null) {
        val editing = _uiState.value.editingHangout
        viewModelScope.launch {
            _uiState.update { it.copy(isHangoutSubmitting = true, hangoutDialogError = null) }
            if (editing == null) {
                when (val result = expenseRepository.createHangout(name, startDate, endDate)) {
                    is CreateHangoutResult.Success -> {
                        val countdownError = countdownDate?.let { addCountdown(name, it) }
                        _uiState.update { it.copy(isHangoutSubmitting = false) }
                        dismissHangoutDialog()
                        refreshHangouts()
                        // After the refresh, which clears errorMessage when it starts.
                        if (countdownError != null) {
                            _uiState.update { it.copy(errorMessage = "hangout saved, but the countdown wasn't: $countdownError") }
                        }
                    }
                    is CreateHangoutResult.NetworkError ->
                        _uiState.update { it.copy(isHangoutSubmitting = false, hangoutDialogError = result.message) }
                }
            } else {
                when (val result = expenseRepository.updateHangout(editing.id, name, startDate, endDate)) {
                    is UpdateHangoutResult.Success -> {
                        _uiState.update { it.copy(isHangoutSubmitting = false) }
                        dismissHangoutDialog()
                        refreshHangouts()
                    }
                    is UpdateHangoutResult.ValidationError ->
                        _uiState.update { it.copy(isHangoutSubmitting = false, hangoutDialogError = result.message) }
                    is UpdateHangoutResult.NetworkError ->
                        _uiState.update { it.copy(isHangoutSubmitting = false, hangoutDialogError = result.message) }
                }
            }
        }
    }

    /** Returns the error message, or null when the countdown was added. */
    private suspend fun addCountdown(title: String, date: String): String? =
        when (val result = datesRepository.saveDate(null, SpecialDateRequest(kind = KIND_COUNTDOWN, title = title, date = date))) {
            is SaveDateResult.Success -> null
            is SaveDateResult.ValidationError -> result.message
            is SaveDateResult.NetworkError -> result.message
        }

    fun deleteHangout(id: String) {
        viewModelScope.launch {
            when (val result = expenseRepository.deleteHangout(id)) {
                DeleteHangoutResult.Success -> refreshHangouts()
                is DeleteHangoutResult.NetworkError -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }

    fun openAddMemoryDialog(hangout: HangoutDto) {
        _uiState.update { it.copy(memoryDialogHangout = hangout, editingMemory = null, memoryDialogError = null) }
    }

    fun openEditMemoryDialog(hangout: HangoutDto, memory: HangoutMemoryDto) {
        _uiState.update { it.copy(memoryDialogHangout = hangout, editingMemory = memory, memoryDialogError = null) }
    }

    fun dismissMemoryDialog() {
        _uiState.update { it.copy(memoryDialogHangout = null, editingMemory = null, memoryDialogError = null) }
    }

    fun submitMemory(text: String) {
        val hangout = _uiState.value.memoryDialogHangout ?: return
        val editing = _uiState.value.editingMemory
        viewModelScope.launch {
            _uiState.update { it.copy(isMemorySubmitting = true, memoryDialogError = null) }
            if (editing == null) {
                when (val result = expenseRepository.addMemory(hangout.id, text)) {
                    is AddMemoryResult.Success -> {
                        _uiState.update { it.copy(isMemorySubmitting = false) }
                        dismissMemoryDialog()
                        refreshHangouts()
                    }
                    is AddMemoryResult.ValidationError ->
                        _uiState.update { it.copy(isMemorySubmitting = false, memoryDialogError = result.message) }
                    is AddMemoryResult.NetworkError ->
                        _uiState.update { it.copy(isMemorySubmitting = false, memoryDialogError = result.message) }
                }
            } else {
                when (val result = expenseRepository.updateMemory(hangout.id, editing.id, text)) {
                    is UpdateMemoryResult.Success -> {
                        _uiState.update { it.copy(isMemorySubmitting = false) }
                        dismissMemoryDialog()
                        refreshHangouts()
                    }
                    is UpdateMemoryResult.ValidationError ->
                        _uiState.update { it.copy(isMemorySubmitting = false, memoryDialogError = result.message) }
                    is UpdateMemoryResult.NetworkError ->
                        _uiState.update { it.copy(isMemorySubmitting = false, memoryDialogError = result.message) }
                }
            }
        }
    }

    fun deleteMemory(hangoutId: String, memoryId: String) {
        viewModelScope.launch {
            when (val result = expenseRepository.deleteMemory(hangoutId, memoryId)) {
                DeleteMemoryResult.Success -> refreshHangouts()
                is DeleteMemoryResult.NetworkError -> _uiState.update { it.copy(errorMessage = result.message) }
            }
        }
    }
}
