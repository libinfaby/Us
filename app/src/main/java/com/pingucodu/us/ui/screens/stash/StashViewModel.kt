package com.pingucodu.us.ui.screens.stash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingucodu.us.data.money.CreateHangoutResult
import com.pingucodu.us.data.money.ExpenseRepository
import com.pingucodu.us.data.money.HangoutsResult
import com.pingucodu.us.data.network.HangoutDto
import com.pingucodu.us.data.network.LinkPreviewDto
import com.pingucodu.us.data.network.SearchResultDto
import com.pingucodu.us.data.network.StashItemDto
import com.pingucodu.us.data.network.StashItemRequest
import com.pingucodu.us.data.stash.AddStashItemResult
import com.pingucodu.us.data.stash.DeleteStashItemResult
import com.pingucodu.us.data.stash.LinkPreviewResult
import com.pingucodu.us.data.stash.SearchResult
import com.pingucodu.us.data.stash.StashItemsResult
import com.pingucodu.us.data.stash.StashRepository
import com.pingucodu.us.data.stash.StashTagsResult
import com.pingucodu.us.data.stash.ToggleStashItemResult
import com.pingucodu.us.data.stash.UpdateStashItemResult
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
    StashTypeSpec("place", "place"),
    StashTypeSpec("movie", "movie"),
    StashTypeSpec("book", "book"),
    StashTypeSpec("activity", "activity"),
    StashTypeSpec("link", "link"),
    StashTypeSpec("note", "note"),
    StashTypeSpec("todo", "to-do"),
)

/** Stash types that can carry a link with a fetched title + description. */
val LINK_PREVIEW_TYPES = setOf("movie", "book", "place")

/** Stash types that can be looked up by name with the form's "find" button. */
val SEARCHABLE_TYPES = setOf("movie", "book")

const val ALL_CATEGORY = "all"
const val PLACE_TYPE = "place"
const val ACTIVITY_TYPE = "activity"
/** Enough for every place we'd realistically save; the picker isn't paged. */
private const val PLACES_LIMIT = 200
private const val PAGE_SIZE = 10
/** How far [StashViewModel.focusItem] pages through looking for an item before giving up. */
private const val FOCUS_MAX_PAGES = 30

/** A stash item to scroll to and flash - from tapping it on a hangout card. */
data class StashFocus(val itemId: String, val type: String)

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
    /** Films or books matching the typed name; null = no search shown. */
    val searchResults: List<SearchResultDto>? = null,
    val isSearching: Boolean = false,
    val searchError: String? = null,

    /** For the hangout name on each card and the add sheet's hangout picker. */
    val hangouts: List<HangoutDto> = emptyList(),
    /** Set until the screen has scrolled to it (see [StashViewModel.focusItem]). */
    val focusItemId: String? = null,
    /** Saved places, for the add sheet's "at a place?" picker on activities. */
    val places: List<StashItemDto> = emptyList(),
    /** Narrows the activity list to the ones tied to this place (see [StashViewModel.showActivitiesAt]). */
    val placeFilter: StashItemDto? = null,
) {
    val typeFilter: String? get() = if (category == ALL_CATEGORY) null else category

    /** Tags that can narrow the current category - every tag under "all", else just that type's -
     * alphabetical, so a tag is easy to find in the filter row. */
    val filterTags: List<String>
        get() = (typeFilter?.let { tagsByType[it].orEmpty() } ?: allTags).sortedWith(String.CASE_INSENSITIVE_ORDER)
}

@HiltViewModel
class StashViewModel @Inject constructor(
    private val stashRepository: StashRepository,
    private val expenseRepository: ExpenseRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StashUiState())
    val uiState: StateFlow<StashUiState> = _uiState.asStateFlow()

    private var itemsJob: Job? = null

    init {
        refresh()
        refreshAllTags()
    }

    fun setCategory(category: String) {
        // Switching type filters is a full data-set swap, not a refresh of what's on screen -
        // drop the stale items right away so the list falls back to skeleton cards instead of
        // briefly showing the previous category's items under a spurious pull-to-refresh spinner.
        // A tag filter carries over only if the new category actually has that tag.
        _uiState.update {
            val next = it.copy(category = category, items = emptyList(), placeFilter = null)
            next.copy(tag = it.tag?.takeIf { tag -> tag in next.filterTags })
        }
        refresh()
    }

    /** A place card's "N activities" button: the activity list, narrowed to that place. */
    fun showActivitiesAt(place: StashItemDto) {
        _uiState.update { it.copy(category = ACTIVITY_TYPE, tag = null, placeFilter = place, items = emptyList()) }
        refresh()
    }

    fun clearPlaceFilter() {
        _uiState.update { it.copy(placeFilter = null, items = emptyList()) }
        refresh()
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
    private fun reloadLoaded() {
        loadItems(limit = maxOf(PAGE_SIZE, _uiState.value.items.size))
        // An added, edited or removed item can change what a hangout card lists.
        refreshHangouts()
    }

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
                placeId = state.placeFilter?.id,
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

    /** Shows [focus]'s item: switches to its type with no tag filter, then pages through until the
     * item is loaded so the screen can scroll to it. */
    fun focusItem(focus: StashFocus) {
        itemsJob?.cancel()
        // Loading is set right away so the screen doesn't look for the item in the emptied list.
        _uiState.update {
            it.copy(
                category = focus.type,
                tag = null,
                placeFilter = null,
                items = emptyList(),
                isLoading = true,
                focusItemId = focus.itemId,
            )
        }
        itemsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = false, loadMoreFailed = false, errorMessage = null) }
            val loaded = mutableListOf<StashItemDto>()
            var endReached = false
            for (page in 0 until FOCUS_MAX_PAGES) {
                when (val result = stashRepository.getItems(status = "everything", type = focus.type, limit = PAGE_SIZE, offset = loaded.size)) {
                    is StashItemsResult.Success -> {
                        loaded += result.items
                        endReached = result.items.size < PAGE_SIZE
                    }
                    is StashItemsResult.NetworkError -> {
                        _uiState.update { it.copy(isLoading = false, items = loaded.distinctBy { i -> i.id }, errorMessage = result.message) }
                        return@launch
                    }
                }
                if (endReached || loaded.any { it.id == focus.itemId }) break
            }
            _uiState.update { it.copy(isLoading = false, items = loaded.distinctBy { i -> i.id }, endReached = endReached) }
        }
    }

    fun consumeFocus() {
        _uiState.update { it.copy(focusItemId = null) }
    }

    /** Appends the next page - called as the list nears its bottom, Instagram-style. */
    fun loadMore() {
        val state = _uiState.value
        if (state.endReached || state.isLoading || state.isLoadingMore) return
        if (itemsJob?.isActive == true) return
        itemsJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, loadMoreFailed = false) }
            val result = stashRepository.getItems(
                status = "everything",
                type = state.typeFilter,
                tag = state.tag,
                limit = PAGE_SIZE,
                offset = state.items.size,
                placeId = state.placeFilter?.id,
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

    /** Cards show the name of the hangout they belong to, and the add sheet lists them. Quiet on
     * failure - it's only names, and the items list reports its own errors. */
    fun refreshHangouts() {
        viewModelScope.launch {
            when (val result = expenseRepository.getHangouts()) {
                is HangoutsResult.Success -> _uiState.update { it.copy(hangouts = result.hangouts) }
                is HangoutsResult.NetworkError -> Unit
            }
        }
    }

    fun openAddDialog() {
        _uiState.update { it.withFreshDialog().copy(showAddDialog = true) }
        refreshPlaces()
    }

    fun openEditDialog(item: StashItemDto) {
        _uiState.update { it.withFreshDialog().copy(showAddDialog = true, editingItem = item) }
        refreshPlaces()
    }

    /** Every saved place, for tying an activity to one. Quiet on failure - the picker just
     * keeps what it had. */
    private fun refreshPlaces() {
        viewModelScope.launch {
            when (val result = stashRepository.getItems(status = "everything", type = PLACE_TYPE, limit = PLACES_LIMIT)) {
                is StashItemsResult.Success -> _uiState.update { it.copy(places = result.items) }
                is StashItemsResult.NetworkError -> Unit
            }
        }
    }

    /** A link shared from another app: guess movie, book or place from the host, open the add sheet
     * with the link filled in, and start fetching its title + description straight away. */
    fun openAddDialogFromShare(url: String) {
        val type = when {
            isMapsLink(url) -> "place"
            isBooksLink(url) -> "book"
            else -> "movie"
        }
        _uiState.update { it.withFreshDialog().copy(showAddDialog = true, prefillUrl = url, prefillType = type) }
        fetchPreview(url)
        refreshPlaces()
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
        searchResults = null,
        isSearching = false,
        searchError = null,
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

    private var searchJob: Job? = null

    /** Looks up films or books ([type]) by the name typed in the title field, for the user to pick one. */
    fun search(type: String, query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchError = null, searchResults = null) }
            when (val result = stashRepository.search(type, query.trim())) {
                is SearchResult.Success ->
                    _uiState.update { it.copy(isSearching = false, searchResults = result.results) }
                is SearchResult.Failed ->
                    _uiState.update { it.copy(isSearching = false, searchError = result.message) }
            }
        }
    }

    /** Fills the form from a picked film or book, exactly like fetching its IMDb / Google Books link would. */
    fun pickSearchResult(type: String, result: SearchResultDto) {
        searchJob?.cancel()
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            _uiState.update {
                it.copy(searchResults = null, isSearching = false, searchError = null, isFetchingPreview = true, previewError = null)
            }
            when (val preview = stashRepository.getSearchResultPreview(type, result.id)) {
                is LinkPreviewResult.Success ->
                    _uiState.update { it.copy(isFetchingPreview = false, linkPreview = preview.preview) }
                is LinkPreviewResult.Unreadable ->
                    _uiState.update { it.copy(isFetchingPreview = false, previewError = preview.message) }
                is LinkPreviewResult.NetworkError ->
                    _uiState.update { it.copy(isFetchingPreview = false, previewError = preview.message) }
            }
        }
    }

    fun clearSearchResults() {
        searchJob?.cancel()
        _uiState.update { it.copy(searchResults = null, isSearching = false, searchError = null) }
    }

    private fun isBooksLink(url: String): Boolean {
        val lower = url.lowercase()
        return "books.google." in lower || "play.google.com/store/books" in lower || "google.com/books" in lower
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

    /** "+ new" in the add sheet's hangout picker: creates it and hands it back to be selected. */
    suspend fun createHangoutAndReturn(name: String): HangoutDto? {
        return when (val result = expenseRepository.createHangout(name)) {
            is CreateHangoutResult.Success -> {
                _uiState.update { it.copy(hangouts = it.hangouts + result.hangout, dialogError = null) }
                result.hangout
            }
            is CreateHangoutResult.NetworkError -> {
                _uiState.update { it.copy(dialogError = result.message) }
                null
            }
        }
    }
}
