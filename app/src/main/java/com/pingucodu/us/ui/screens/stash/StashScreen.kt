package com.pingucodu.us.ui.screens.stash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.pingucodu.us.data.network.HangoutDto
import com.pingucodu.us.data.network.LinkPreviewDto
import com.pingucodu.us.data.network.SearchResultDto
import com.pingucodu.us.data.network.StashItemDto
import com.pingucodu.us.data.network.StashItemRequest
import com.pingucodu.us.ui.components.ChevronArrow
import com.pingucodu.us.ui.components.ErrorBanner
import com.pingucodu.us.ui.components.NeoBottomSheet
import com.pingucodu.us.ui.components.NeoChoiceChip
import com.pingucodu.us.ui.components.PillActionButton
import com.pingucodu.us.ui.components.NeoField
import com.pingucodu.us.ui.components.SectionLabel
import com.pingucodu.us.ui.components.SubmitButton
import com.pingucodu.us.ui.components.clickableNoRipple
import com.pingucodu.us.ui.theme.BorderWidth
import com.pingucodu.us.ui.theme.DashedDivider
import com.pingucodu.us.ui.theme.DescriptionGrey
import com.pingucodu.us.ui.theme.Ink
import com.pingucodu.us.ui.theme.NeoConfirmDialog
import com.pingucodu.us.ui.theme.Pink
import com.pingucodu.us.ui.theme.PinkTint
import com.pingucodu.us.ui.theme.PinguCoduType
import com.pingucodu.us.ui.theme.PlaceholderGrey
import com.pingucodu.us.ui.theme.SkeletonStashItemCard
import com.pingucodu.us.ui.theme.Teal
import com.pingucodu.us.ui.theme.YellowSoft
import com.pingucodu.us.ui.theme.hardShadow
import com.pingucodu.us.ui.util.LocalNameMask
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val CardShape = RoundedCornerShape(14.dp)
private val SQLITE_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private const val LOAD_MORE_THRESHOLD = 3

/** [sharedUrl] is a link shared into the app from another app; it opens the add sheet once and
 * is then cleared through [onSharedUrlConsumed]. */
@Composable
fun StashScreen(
    modifier: Modifier = Modifier,
    sharedUrl: String? = null,
    onSharedUrlConsumed: () -> Unit = {},
    viewModel: StashViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            viewModel.openAddDialogFromShare(sharedUrl)
            onSharedUrlConsumed()
        }
    }
    var itemToDelete by remember { mutableStateOf<StashItemDto?>(null) }

    // Hangouts are managed on their own tab - refetch on entry so cards and the add sheet's
    // hangout picker pick up ones created or renamed there.
    LaunchedEffect(Unit) {
        viewModel.refreshHangouts()
    }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = uiState.isLoading && uiState.items.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(4.dp))
            CategoryRow(selected = uiState.category, onSelect = viewModel::setCategory)
            Spacer(Modifier.height(12.dp))
            if (uiState.filterTags.isNotEmpty() || uiState.tag != null) {
                TagFilterRow(tags = uiState.filterTags, selected = uiState.tag, onSelect = viewModel::setTag)
                Spacer(Modifier.height(8.dp))
            }

            if (uiState.errorMessage != null) {
                ErrorBanner(
                    uiState.errorMessage!!,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }

            StashItemsSection(
                isLoading = uiState.isLoading,
                items = uiState.items,
                hangoutNames = uiState.hangouts.associate { it.id to it.name },
                tag = uiState.tag,
                isLoadingMore = uiState.isLoadingMore,
                loadMoreFailed = uiState.loadMoreFailed,
                onLoadMore = viewModel::loadMore,
                onToggle = viewModel::toggleItem,
                onEdit = viewModel::openEditDialog,
                onDelete = { itemToDelete = it },
            )
        }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 125.dp)
                .size(66.dp)
                .hardShadow(CircleShape)
                .background(Pink, CircleShape)
                .border(BorderWidth, Ink, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = viewModel::openAddDialog,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", style = MaterialTheme.typography.headlineLarge)
        }
    }

    if (uiState.showAddDialog) {
        StashItemFormDialog(
            item = uiState.editingItem,
            defaultType = uiState.prefillType ?: uiState.typeFilter,
            prefillUrl = uiState.prefillUrl,
            tagsByType = uiState.tagsByType,
            dialogError = uiState.dialogError,
            isSubmitting = uiState.isSubmitting,
            linkPreview = uiState.linkPreview,
            isFetchingPreview = uiState.isFetchingPreview,
            previewError = uiState.previewError,
            onFetchPreview = { viewModel.fetchPreview(normalizeUrl(it)) },
            searchResults = uiState.searchResults,
            isSearching = uiState.isSearching,
            searchError = uiState.searchError,
            onSearch = viewModel::search,
            onPickSearchResult = viewModel::pickSearchResult,
            onClearSearchResults = viewModel::clearSearchResults,
            hangouts = uiState.hangouts,
            onCreateHangout = viewModel::createHangoutAndReturn,
            onDismiss = viewModel::dismissDialog,
            onSubmit = viewModel::submitItem,
            onRenameTag = viewModel::renameTag,
            onDeleteTag = viewModel::deleteTag,
        )
    }

    if (itemToDelete != null) {
        val target = itemToDelete!!
        NeoConfirmDialog(
            title = "delete this?",
            message = "\"${target.title}\" goes away for good. this can't be undone.",
            confirmLabel = "delete",
            onConfirm = {
                viewModel.deleteItem(target.id)
                itemToDelete = null
            },
            onDismiss = { itemToDelete = null },
        )
    }
}

@Composable
private fun CategoryRow(selected: String, onSelect: (String) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Pill(label = "all", selected = selected == ALL_CATEGORY, onClick = { onSelect(ALL_CATEGORY) })
        STASH_TYPES.forEach { spec ->
            Pill(label = spec.label, selected = selected == spec.value, onClick = { onSelect(spec.value) })
        }
    }
}

@Composable
private fun Pill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .let { if (selected) it.hardShadow(shape, offsetX = 3.dp, offsetY = 3.dp) else it }
            .border(2.dp, Ink, shape)
            .background(if (selected) Teal else Color.White, shape)
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Ink)
    }
}

/** Horizontally scrolling tag chips; tapping the selected one clears the filter. */
@Composable
private fun TagFilterRow(tags: List<String>, selected: String?, onSelect: (String) -> Unit) {
    // Keep the active tag reachable even if it just vanished from the list (e.g. its last item
    // was edited), otherwise there'd be no chip left to tap to clear it.
    val shown = if (selected != null && selected !in tags) listOf(selected) + tags else tags
    LazyRow(
        contentPadding = PaddingValues(start = 20.dp, top = 2.dp, end = 20.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(shown, key = { it }) { tag ->
            TagFilterChip(tag = tag, selected = tag == selected, onClick = { onSelect(tag) })
        }
    }
}

@Composable
private fun TagFilterChip(tag: String, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .let { if (selected) it.hardShadow(shape, offsetX = 2.dp, offsetY = 2.dp) else it }
            .border(2.dp, Ink, shape)
            .background(if (selected) Teal else PinkTint, shape)
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(if (selected) "#$tag  ×" else "#$tag", style = PinguCoduType.monoLabel, color = Ink)
    }
}

internal fun typeBadgeColor(type: String): Color = when (type) {
    "movie", "todo" -> Pink
    "place", "book" -> Teal
    "link" -> YellowSoft
    else -> Color.White
}

internal fun typeLabel(type: String): String =
    (STASH_TYPES.firstOrNull { it.value == type }?.label ?: type).uppercase()

private fun titlePlaceholder(type: String): String = when (type) {
    "movie" -> "movie or show name"
    "book" -> "book title"
    "link" -> "what's the link about?"
    "place" -> "place to visit"
    "note" -> "note title"
    "todo" -> "what needs to be done?"
    else -> "title"
}

/** (done label, pending label) for the toggle pill, or null for types with no toggle. */
internal fun toggleLabel(type: String): Pair<String, String>? = when (type) {
    "todo" -> "done" to "mark done"
    "place" -> "visited" to "mark visited"
    "movie" -> "watched" to "mark watched"
    "book" -> "read" to "mark read"
    else -> null
}

private fun bodyPlaceholder(type: String): String = when (type) {
    "movie" -> "why watch it, or a note for later"
    "book" -> "why read it, or who suggested it"
    "link" -> "paste the link"
    "place" -> "where is it, or why go?"
    "note" -> "what's on your mind?"
    "todo" -> "any details, or a deadline"
    else -> "link, or why you're sharing it"
}

/** Stash links are often typed without a scheme (e.g. "libinfaby.dev") - assume https so the
 * browser intent actually resolves instead of failing to parse a schemeless URI. */
private fun normalizeUrl(raw: String): String {
    val trimmed = raw.trim()
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
}

/** Google's documented "search action" maps URL - opens straight in the Maps app when it's
 * installed, and falls back to the maps website in a browser otherwise, so it never dead-ends. */
private fun googleMapsSearchUrl(query: String): String {
    val trimmed = query.trim()
    val q = if (trimmed.isEmpty()) "" else URLEncoder.encode(trimmed, "UTF-8")
    return "https://www.google.com/maps/search/?api=1&query=$q"
}

// Matches, in order: a scheme'd URL, a www.-prefixed host, or a bare domain-looking token
// (e.g. "google.com", "libinfaby.dev/blog") - the last case requires a letters-only TLD of 2+
// chars right after the dot so it doesn't fire on "e.g." or "3.14" or "Mr. Smith".
private val URL_REGEX = Regex(
    """https?://\S+""" +
        """|www\.\S+""" +
        """|\b[a-zA-Z0-9](?:[\w-]*[a-zA-Z0-9])?(?:\.[a-zA-Z0-9](?:[\w-]*[a-zA-Z0-9])?)*\.[a-zA-Z]{2,}(?:/\S*)?\b""",
)
private val LinkStyle = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline))

/** For a "link" item the whole note is the link, even if typed bare ("libinfaby.dev"). For
 * every other type, only linkify URLs actually found inside the free-text note - a movie note
 * that happens to mention a link should still make just that URL tappable. */
private fun bodyWithLinks(text: String): AnnotatedString {
    return buildAnnotatedString {
        var lastIndex = 0
        for (match in URL_REGEX.findAll(text)) {
            if (match.range.first > lastIndex) append(text.substring(lastIndex, match.range.first))
            withLink(LinkAnnotation.Url(normalizeUrl(match.value), LinkStyle)) { append(match.value) }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < text.length) append(text.substring(lastIndex))
    }
}

internal fun relativeTime(sqliteDateTime: String): String = try {
    val then = java.time.LocalDateTime.parse(sqliteDateTime, SQLITE_DATETIME).toInstant(ZoneOffset.UTC)
    val minutes = Duration.between(then, Instant.now()).toMinutes()
    when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${minutes / 60}h"
        else -> "${minutes / (60 * 24)}d"
    }
} catch (e: Exception) {
    ""
}

// ---------- stash items ----------

@Composable
private fun StashItemsSection(
    isLoading: Boolean,
    items: List<StashItemDto>,
    hangoutNames: Map<String, String>,
    tag: String?,
    isLoadingMore: Boolean,
    loadMoreFailed: Boolean,
    onLoadMore: () -> Unit,
    onToggle: (String) -> Unit,
    onEdit: (StashItemDto) -> Unit,
    onDelete: (StashItemDto) -> Unit,
) {
    when {
        isLoading && items.isEmpty() -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                repeat(3) { SkeletonStashItemCard() }
            }
        }
        items.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (tag != null) "nothing tagged #$tag here" else "nothing here yet",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        else -> {
            // Items arrive page by page, already ordered by the server (pending first, done ones
            // sunk to the bottom). Ask for the next page a few cards before the end so it's usually
            // in before the user gets there.
            val listState = rememberLazyListState()
            val nearEnd by remember {
                derivedStateOf {
                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    lastVisible >= listState.layoutInfo.totalItemsCount - 1 - LOAD_MORE_THRESHOLD
                }
            }
            // Keyed on the size too: if a freshly appended page still doesn't fill the screen,
            // nearEnd stays true and this needs to fire again for the page after.
            LaunchedEffect(nearEnd, items.size) {
                if (nearEnd) onLoadMore()
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 210.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    StashItemCard(
                        item = item,
                        hangoutName = item.hangoutId?.let(hangoutNames::get),
                        onToggle = { onToggle(item.id) },
                        onEdit = { onEdit(item) },
                        onDelete = { onDelete(item) },
                    )
                }
                when {
                    isLoadingMore -> item(key = "loading-more") { SkeletonStashItemCard() }
                    loadMoreFailed -> item(key = "load-more-failed") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            PillActionButton(label = "couldn't load more · retry", background = Color.White, contentColor = Ink, onClick = onLoadMore)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StashItemCard(
    item: StashItemDto,
    hangoutName: String?,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val isMuted = toggleLabel(item.type) != null && item.status == "done"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isMuted) 0.55f else 1f)
            .let { if (isMuted) it else it.hardShadow(CardShape) }
            .border(BorderWidth, Ink, CardShape)
            .background(MaterialTheme.colorScheme.surface, CardShape)
            .padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .border(2.dp, Ink, RoundedCornerShape(7.dp))
                    .background(typeBadgeColor(item.type), RoundedCornerShape(7.dp))
                    .padding(horizontal = 7.dp, vertical = 5.dp),
            ) {
                Text(typeLabel(item.type), style = PinguCoduType.monoLabel)
            }
            Text("${LocalNameMask.current.resolve(item.author)} · ${relativeTime(item.createdAt)}", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = MaterialTheme.typography.titleMedium)
        if (hangoutName != null) {
            Spacer(Modifier.height(4.dp))
            Text("hangout · $hangoutName", style = PinguCoduType.monoLabel, color = DescriptionGrey)
        }
        if (!item.body.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(bodyWithLinks(item.body), style = MaterialTheme.typography.bodySmall)
        }
        if (item.tags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item.tags.forEach { tag -> TagChip(tag) }
            }
        }
        Spacer(Modifier.height(10.dp))
        DashedDivider()
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
            toggleLabel(item.type)?.let { (doneLabel, pendingLabel) ->
                val done = item.status == "done"
                PillActionButton(
                    label = if (done) doneLabel else pendingLabel,
                    background = if (done) Teal else Ink,
                    contentColor = if (done) Ink else Color.White,
                    onClick = onToggle,
                )
            }
            // Link items keep their URL in the note, so fall back to the first one found there.
            val linkUrl = item.url
                ?: if (item.type == "link") item.body?.let { URL_REGEX.find(it)?.value }?.let(::normalizeUrl) else null
            if (linkUrl != null) {
                val uriHandler = LocalUriHandler.current
                PillActionButton(label = "link ↗", background = YellowSoft, contentColor = Ink, onClick = {
                    runCatching { uriHandler.openUri(linkUrl) }
                })
            }
            PillActionButton(label = "edit", background = Color.White, contentColor = Ink, onClick = onEdit)
            PillActionButton(label = "delete", background = Color.White, contentColor = Ink, onClick = onDelete)
        }
    }
}

@Composable
private fun TagChip(tag: String) {
    Box(
        modifier = Modifier
            .border(2.dp, Ink, RoundedCornerShape(50))
            .background(PinkTint, RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text("#$tag", style = PinguCoduType.monoLabel, color = Ink)
    }
}

@Composable
private fun FetchLinkChip(enabled: Boolean, isFetching: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .hardShadow(shape, offsetX = 3.dp, offsetY = 3.dp)
            .border(3.dp, Ink, shape)
            .background(if (enabled) YellowSoft else Color.White, shape)
            .clickableNoRipple(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (isFetching) "…" else "fetch",
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled || isFetching) Ink else Ink.copy(alpha = 0.35f),
        )
    }
}

/** Small teal pill under the title: "find on maps" for places, "find movie" for movies. */
@Composable
private fun FindChip(label: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .border(2.dp, Ink, shape)
            .background(if (enabled) Teal else Color.White, shape)
            .clickableNoRipple(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (enabled) Ink else Ink.copy(alpha = 0.35f))
    }
}

/** One film or book from a name search: its title, and the line that tells same-named ones apart. */
@Composable
private fun SearchResultRow(result: SearchResultDto, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, Ink, shape)
            .background(Color.White, shape)
            .clickableNoRipple(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(result.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Ink)
        Spacer(Modifier.height(2.dp))
        Text(result.description, style = MaterialTheme.typography.labelSmall, color = DescriptionGrey)
    }
}

@Composable
private fun SuggestedTagChip(tag: String, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .border(2.dp, Ink, RoundedCornerShape(50))
            .background(if (selected) Pink else Color.White, RoundedCornerShape(50))
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text("#$tag", style = PinguCoduType.monoLabel, color = Ink)
    }
}


// ---------- add/edit stash item ----------

/** Which hangout a stash item belongs to: a dropdown of the existing ones, plus "+ new" to create
 * one on the spot - the stash-sheet take on the expense form's hangout picker. */
@Composable
private fun HangoutPicker(
    hangouts: List<HangoutDto>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onCreateHangout: suspend (String) -> HangoutDto?,
) {
    val scope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var showNewInput by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    val selectedName = hangouts.firstOrNull { it.id == selectedId }?.name

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            val shape = RoundedCornerShape(14.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .hardShadow(shape, offsetX = 3.dp, offsetY = 3.dp)
                    .border(3.dp, Ink, shape)
                    .background(Color.White, shape)
                    .clickableNoRipple { menuExpanded = true }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    selectedName ?: if (hangouts.isEmpty()) "no hangouts yet - add one" else "pick a hangout",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = if (selectedName != null) Ink else PlaceholderGrey,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                ChevronArrow(pointingUp = menuExpanded)
            }
            DropdownMenu(expanded = menuExpanded && hangouts.isNotEmpty(), onDismissRequest = { menuExpanded = false }) {
                hangouts.forEach { h ->
                    DropdownMenuItem(
                        text = { Text(h.name, style = MaterialTheme.typography.titleMedium) },
                        onClick = {
                            onSelect(h.id)
                            menuExpanded = false
                        },
                    )
                }
            }
        }
        NeoChoiceChip(
            label = if (showNewInput) "close" else "+ new",
            selected = showNewInput,
            onClick = {
                showNewInput = !showNewInput
                newName = ""
            },
        )
    }
    if (showNewInput) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeoField(
                value = newName,
                onValueChange = { newName = it },
                placeholder = "new hangout",
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            NeoChoiceChip(
                label = if (isCreating) "creating…" else "create",
                selected = true,
                selectedColor = Pink,
                onClick = {
                    val name = newName.trim()
                    if (name.isNotEmpty() && !isCreating) {
                        scope.launch {
                            isCreating = true
                            val created = onCreateHangout(name)
                            isCreating = false
                            if (created != null) {
                                onSelect(created.id)
                                showNewInput = false
                                newName = ""
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun StashItemFormDialog(
    item: StashItemDto?,
    defaultType: String?,
    prefillUrl: String?,
    tagsByType: Map<String, List<String>>,
    dialogError: String?,
    isSubmitting: Boolean,
    linkPreview: LinkPreviewDto?,
    isFetchingPreview: Boolean,
    previewError: String?,
    onFetchPreview: (String) -> Unit,
    searchResults: List<SearchResultDto>?,
    isSearching: Boolean,
    searchError: String?,
    onSearch: (type: String, query: String) -> Unit,
    onPickSearchResult: (type: String, result: SearchResultDto) -> Unit,
    onClearSearchResults: () -> Unit,
    hangouts: List<HangoutDto>,
    onCreateHangout: suspend (String) -> HangoutDto?,
    onDismiss: () -> Unit,
    onSubmit: (StashItemRequest) -> Unit,
    onRenameTag: (type: String, oldTag: String, newTag: String) -> Unit,
    onDeleteTag: (type: String, tag: String) -> Unit,
) {
    var type by remember { mutableStateOf(item?.type ?: defaultType ?: STASH_TYPES.first().value) }
    var title by remember { mutableStateOf(item?.title ?: "") }
    var body by remember { mutableStateOf(item?.body ?: "") }
    var url by remember { mutableStateOf(item?.url ?: prefillUrl ?: "") }
    // A fetched preview only overwrites fields the user hasn't typed into themselves.
    var titleTyped by remember { mutableStateOf(false) }
    var bodyTyped by remember { mutableStateOf(false) }
    var tags by remember { mutableStateOf(item?.tags?.toSet() ?: emptySet()) }
    var tagInput by remember { mutableStateOf("") }
    var tagToManage by remember { mutableStateOf<String?>(null) }
    var tagToConfirmDelete by remember { mutableStateOf<String?>(null) }
    var inHangout by remember { mutableStateOf(item?.hangoutId != null) }
    var hangoutId by remember { mutableStateOf(item?.hangoutId) }

    val isValid = title.isNotBlank()

    fun commitTypedTag() {
        val t = tagInput.trim().removePrefix("#")
        if (t.isNotEmpty()) tags = tags + t
        tagInput = ""
    }

    fun finalTags(): List<String> {
        val typed = tagInput.trim().removePrefix("#")
        return (if (typed.isNotEmpty()) tags + typed else tags).toList()
    }

    val suggestedTags = tagsByType[type].orEmpty()
    val visibleTags = (suggestedTags + tags).distinct()
    val uriHandler = LocalUriHandler.current
    val supportsLink = type in LINK_PREVIEW_TYPES

    LaunchedEffect(linkPreview) {
        val preview = linkPreview ?: return@LaunchedEffect
        // Only IMDb, Google Books and Maps links are fetched, so the preview knows what it is.
        type = preview.suggestedType
        if (type == "place") {
            // "Cafe X, 12 MG Road, Kochi" -> title "Cafe X", and the address goes in the notes.
            val name = preview.title.substringBefore(',').trim()
            val address = preview.title.substringAfter(',', "").trim()
            if (!titleTyped || title.isBlank()) title = name.ifEmpty { preview.title }
            val details = listOf(address, preview.description.orEmpty()).filter { it.isNotBlank() }.joinToString("\n")
            if ((!bodyTyped || body.isBlank()) && details.isNotEmpty()) body = details
        } else {
            if (!titleTyped || title.isBlank()) title = preview.title
            if ((!bodyTyped || body.isBlank()) && preview.description != null) body = preview.description
            // Select each genre as a tag, reusing an existing tag's spelling when only the case differs.
            val knownTags = tagsByType[type].orEmpty() + tags
            tags = tags + preview.genres.map { genre -> knownTags.firstOrNull { it.equals(genre, ignoreCase = true) } ?: genre }
        }
        // A picked film without an IMDb id comes back with no link - keep whatever is in the box.
        if (preview.url.isNotEmpty()) url = preview.url
    }

    NeoBottomSheet(title = if (item == null) "share something" else "edit item", onDismiss = onDismiss) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            STASH_TYPES.forEach { spec ->
                NeoChoiceChip(label = spec.label, selected = type == spec.value, onClick = {
                    // Films listed under "movie" have no business staying up after switching to "book".
                    if (type != spec.value) onClearSearchResults()
                    type = spec.value
                })
            }
        }
        Spacer(Modifier.height(16.dp))

        if (supportsLink) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeoField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = when (type) {
                        "place" -> "paste a google maps link"
                        "book" -> "paste a google books link"
                        else -> "paste an imdb link"
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (url.isNotBlank()) onFetchPreview(url) }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                FetchLinkChip(
                    enabled = url.isNotBlank() && !isFetchingPreview,
                    isFetching = isFetchingPreview,
                    onClick = { onFetchPreview(url) },
                )
            }
            Spacer(Modifier.height(6.dp))
            if (previewError != null) {
                ErrorBanner(previewError)
            } else {
                Text(
                    "optional - we'll fill in the title and description for you",
                    style = MaterialTheme.typography.labelSmall,
                    color = DescriptionGrey,
                )
            }
            Spacer(Modifier.height(14.dp))
        }

        NeoField(
            value = title,
            onValueChange = {
                title = it
                titleTyped = true
            },
            placeholder = titlePlaceholder(type),
            keyboardOptions = if (type in SEARCHABLE_TYPES) KeyboardOptions(imeAction = ImeAction.Search) else KeyboardOptions.Default,
            keyboardActions = KeyboardActions(onSearch = { if (title.isNotBlank()) onSearch(type, title) }),
            modifier = Modifier.fillMaxWidth(),
        )

        if (type == "place") {
            Spacer(Modifier.height(10.dp))
            FindChip(label = "📍 find on maps", enabled = title.isNotBlank()) {
                uriHandler.openUri(googleMapsSearchUrl(title))
            }
        }
        if (type in SEARCHABLE_TYPES) {
            Spacer(Modifier.height(10.dp))
            FindChip(
                label = if (isSearching) "finding…" else "find $type",
                enabled = title.isNotBlank() && !isSearching && !isFetchingPreview,
            ) { onSearch(type, title) }
            if (searchError != null) {
                Spacer(Modifier.height(8.dp))
                ErrorBanner(searchError)
            }
            if (searchResults != null) {
                Spacer(Modifier.height(10.dp))
                if (searchResults.isEmpty()) {
                    Text("no ${type}s found - try another name", style = MaterialTheme.typography.labelSmall, color = DescriptionGrey)
                } else {
                    Text("pick the right one", style = MaterialTheme.typography.labelSmall, color = DescriptionGrey)
                    Spacer(Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        searchResults.forEach { result ->
                            SearchResultRow(result) {
                                // The picked title replaces the typed name.
                                titleTyped = false
                                onPickSearchResult(type, result)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "none of these",
                        style = PinguCoduType.monoLabel,
                        color = DescriptionGrey,
                        modifier = Modifier.clickableNoRipple(onClick = onClearSearchResults),
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        NeoField(
            value = body,
            onValueChange = {
                body = it
                bodyTyped = true
            },
            placeholder = bodyPlaceholder(type),
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))

        SectionLabel("tags")
        Spacer(Modifier.height(8.dp))
        if (visibleTags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visibleTags.forEach { tag ->
                    SuggestedTagChip(
                        tag = tag,
                        selected = tag in tags,
                        onClick = { tags = if (tag in tags) tags - tag else tags + tag },
                        onLongClick = { tagToManage = tag },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("hold a tag to rename or delete it", style = MaterialTheme.typography.labelSmall, color = DescriptionGrey)
            Spacer(Modifier.height(6.dp))
        }
        NeoField(
            value = tagInput,
            onValueChange = { tagInput = it },
            placeholder = "type a new tag, press enter to create",
            textStyle = PinguCoduType.mono.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commitTypedTag() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))

        SectionLabel("part of a hangout?")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeoChoiceChip(label = "no", selected = !inHangout, onClick = { inHangout = false })
            NeoChoiceChip(label = "yes", selected = inHangout, onClick = { inHangout = true })
        }
        if (inHangout) {
            Spacer(Modifier.height(10.dp))
            HangoutPicker(
                hangouts = hangouts,
                selectedId = hangoutId,
                onSelect = { hangoutId = it },
                onCreateHangout = onCreateHangout,
            )
        }
        Spacer(Modifier.height(20.dp))

        if (dialogError != null) {
            ErrorBanner(dialogError)
            Spacer(Modifier.height(12.dp))
        }

        SubmitButton(label = if (item == null) "drop it in" else "save changes", enabled = isValid && !isSubmitting) {
            onSubmit(
                StashItemRequest(
                    type = type,
                    title = title.trim(),
                    body = body.ifBlank { null },
                    // "" clears a link on edit; types without links just leave it out.
                    url = if (supportsLink) url.trim().takeIf { it.isNotEmpty() }?.let(::normalizeUrl) ?: "" else null,
                    tags = finalTags(),
                    // "" unlinks it on edit.
                    hangoutId = hangoutId?.takeIf { inHangout } ?: "",
                ),
            )
        }
    }

    if (tagToManage != null) {
        val target = tagToManage!!
        EditTagDialog(
            tag = target,
            onRename = { newName ->
                if (newName.isNotEmpty() && newName != target) {
                    onRenameTag(type, target, newName)
                    tags = tags.map { t -> if (t == target) newName else t }.toSet()
                }
                tagToManage = null
            },
            onDeleteClick = {
                tagToConfirmDelete = target
                tagToManage = null
            },
            onDismiss = { tagToManage = null },
        )
    }

    if (tagToConfirmDelete != null) {
        val target = tagToConfirmDelete!!
        NeoConfirmDialog(
            title = "delete this tag?",
            message = "\"#$target\" is removed from every item that has it. this can't be undone.",
            confirmLabel = "delete",
            onConfirm = {
                onDeleteTag(type, target)
                tags = tags - target
                tagToConfirmDelete = null
            },
            onDismiss = { tagToConfirmDelete = null },
        )
    }
}

@Composable
private fun EditTagDialog(
    tag: String,
    onRename: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(tag) { mutableStateOf(tag) }

    fun commitRename() {
        onRename(name.trim().removePrefix("#"))
    }

    NeoBottomSheet(title = "edit tag", onDismiss = onDismiss) {
        SectionLabel("rename")
        Spacer(Modifier.height(8.dp))
        NeoField(
            value = name,
            onValueChange = { name = it },
            textStyle = PinguCoduType.mono.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commitRename() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        SubmitButton(label = "save changes", enabled = name.trim().removePrefix("#").isNotEmpty()) { commitRename() }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .hardShadow(RoundedCornerShape(16.dp), offsetX = 3.dp, offsetY = 3.dp)
                .border(BorderWidth, Ink, RoundedCornerShape(16.dp))
                .background(Pink, RoundedCornerShape(16.dp))
                .clickableNoRipple(onDeleteClick)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("delete this tag", style = MaterialTheme.typography.titleMedium, color = Ink)
        }
    }
}
