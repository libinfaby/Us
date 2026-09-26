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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import com.pingucodu.us.data.network.HangoutMemoryDto
import com.pingucodu.us.data.network.LinkPreviewDto
import com.pingucodu.us.data.network.MovieSearchResultDto
import com.pingucodu.us.data.network.StashItemDto
import com.pingucodu.us.data.network.StashItemRequest
import com.pingucodu.us.ui.components.DateField
import com.pingucodu.us.ui.components.ErrorBanner
import com.pingucodu.us.ui.components.NeoBottomSheet
import com.pingucodu.us.ui.components.NeoChoiceChip
import com.pingucodu.us.ui.components.PillActionButton
import com.pingucodu.us.ui.components.NeoDatePickerDialog
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
import com.pingucodu.us.ui.theme.SkeletonHangoutCard
import com.pingucodu.us.ui.theme.SkeletonStashItemCard
import com.pingucodu.us.ui.theme.Teal
import com.pingucodu.us.ui.theme.YellowSoft
import com.pingucodu.us.ui.theme.dashedBorder
import com.pingucodu.us.ui.theme.hardShadow
import com.pingucodu.us.ui.util.LocalNameMask
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

private val CardShape = RoundedCornerShape(14.dp)
private val SQLITE_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val HANGOUT_PALETTE = listOf(Pink, Teal, YellowSoft)
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
    var hangoutToDelete by remember { mutableStateOf<HangoutDto?>(null) }
    var memoryToDelete by remember { mutableStateOf<Pair<HangoutDto, HangoutMemoryDto>?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = if (uiState.isHangoutCategory) {
                uiState.hangoutsLoading && uiState.hangoutsLoaded
            } else {
                uiState.isLoading && uiState.items.isNotEmpty()
            },
            onRefresh = { if (uiState.isHangoutCategory) viewModel.refreshHangouts() else viewModel.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(Modifier.height(4.dp))
            CategoryRow(selected = uiState.category, onSelect = viewModel::setCategory)
            Spacer(Modifier.height(12.dp))
            if (!uiState.isHangoutCategory && (uiState.filterTags.isNotEmpty() || uiState.tag != null)) {
                TagFilterRow(tags = uiState.filterTags, selected = uiState.tag, onSelect = viewModel::setTag)
                Spacer(Modifier.height(8.dp))
            }

            if (uiState.errorMessage != null) {
                ErrorBanner(
                    uiState.errorMessage!!,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }

            if (uiState.isHangoutCategory) {
                HangoutsSection(
                    hangouts = uiState.hangouts,
                    currentUsername = uiState.currentUsername,
                    isLoading = uiState.hangoutsLoading && !uiState.hangoutsLoaded,
                    onAddHangout = viewModel::openAddHangoutDialog,
                    onEditHangout = viewModel::openEditHangoutDialog,
                    onAddMemory = viewModel::openAddMemoryDialog,
                    onEditMemory = viewModel::openEditMemoryDialog,
                    onDeleteMemory = { hangout, memory -> memoryToDelete = hangout to memory },
                    onLongPressHangout = { hangoutToDelete = it },
                )
            } else {
                StashItemsSection(
                    isLoading = uiState.isLoading,
                    items = uiState.items,
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
        }

        if (!uiState.isHangoutCategory) {
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
            movieResults = uiState.movieResults,
            isSearchingMovies = uiState.isSearchingMovies,
            movieSearchError = uiState.movieSearchError,
            onSearchMovies = viewModel::searchMovies,
            onPickMovie = viewModel::pickMovie,
            onClearMovieResults = viewModel::clearMovieResults,
            onDismiss = viewModel::dismissDialog,
            onSubmit = viewModel::submitItem,
            onRenameTag = viewModel::renameTag,
            onDeleteTag = viewModel::deleteTag,
        )
    }

    if (uiState.showHangoutDialog) {
        HangoutFormDialog(
            hangout = uiState.editingHangout,
            dialogError = uiState.hangoutDialogError,
            isSubmitting = uiState.isHangoutSubmitting,
            onDismiss = viewModel::dismissHangoutDialog,
            onSubmit = viewModel::submitHangout,
        )
    }

    if (uiState.memoryDialogHangout != null) {
        MemoryFormDialog(
            hangout = uiState.memoryDialogHangout!!,
            memory = uiState.editingMemory,
            dialogError = uiState.memoryDialogError,
            isSubmitting = uiState.isMemorySubmitting,
            onDismiss = viewModel::dismissMemoryDialog,
            onSubmit = viewModel::submitMemory,
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

    if (hangoutToDelete != null) {
        val target = hangoutToDelete!!
        NeoConfirmDialog(
            title = "delete this?",
            message = "\"${target.name}\" and its memories go away for good. its expenses keep their date and amount but lose this hangout.",
            confirmLabel = "delete",
            onConfirm = {
                viewModel.deleteHangout(target.id)
                hangoutToDelete = null
            },
            onDismiss = { hangoutToDelete = null },
        )
    }

    if (memoryToDelete != null) {
        val (hangout, memory) = memoryToDelete!!
        NeoConfirmDialog(
            title = "delete this memory?",
            message = "\"${memory.text}\" goes away for good.",
            confirmLabel = "delete",
            onConfirm = {
                viewModel.deleteMemory(hangout.id, memory.id)
                memoryToDelete = null
            },
            onDismiss = { memoryToDelete = null },
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
        Pill(
            label = "hangouts",
            selected = selected == HANGOUTS_CATEGORY,
            unselectedBackground = Ink,
            unselectedTextColor = Teal,
            onClick = { onSelect(HANGOUTS_CATEGORY) },
        )
        STASH_TYPES.forEach { spec ->
            Pill(label = spec.label, selected = selected == spec.value, onClick = { onSelect(spec.value) })
        }
    }
}

@Composable
private fun Pill(
    label: String,
    selected: Boolean,
    unselectedBackground: Color = Color.White,
    unselectedTextColor: Color = Ink,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .let { if (selected) it.hardShadow(shape, offsetX = 3.dp, offsetY = 3.dp) else it }
            .border(2.dp, Ink, shape)
            .background(if (selected) Teal else unselectedBackground, shape)
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) Ink else unselectedTextColor)
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

private fun typeBadgeColor(type: String): Color = when (type) {
    "movie", "todo" -> Pink
    "place" -> Teal
    "link" -> YellowSoft
    else -> Color.White
}

private fun typeLabel(type: String): String =
    (STASH_TYPES.firstOrNull { it.value == type }?.label ?: type).uppercase()

private fun titlePlaceholder(type: String): String = when (type) {
    "movie" -> "movie or show name"
    "link" -> "what's the link about?"
    "place" -> "place to visit"
    "note" -> "note title"
    "todo" -> "what needs to be done?"
    else -> "title"
}

/** (done label, pending label) for the toggle pill, or null for types with no toggle. */
private fun toggleLabel(type: String): Pair<String, String>? = when (type) {
    "todo" -> "done" to "mark done"
    "place" -> "visited" to "mark visited"
    "movie" -> "watched" to "mark watched"
    else -> null
}

private fun bodyPlaceholder(type: String): String = when (type) {
    "movie" -> "why watch it, or a note for later"
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

private fun relativeTime(sqliteDateTime: String): String = try {
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
            if (item.url != null) {
                val uriHandler = LocalUriHandler.current
                PillActionButton(label = "link ↗", background = YellowSoft, contentColor = Ink, onClick = {
                    runCatching { uriHandler.openUri(item.url) }
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

// ---------- hangouts ----------

private fun formatHangoutDate(date: String): String? = runCatching { LocalDate.parse(date) }.getOrNull()?.let {
    "${it.month.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH).lowercase()} ${it.dayOfMonth}"
}

private fun formatCents(cents: Long): String = "₹%.2f".format(cents / 100.0)

private fun hangoutSubtitle(hangout: HangoutDto): String {
    val start = hangout.startDate?.let(::formatHangoutDate)
    val end = hangout.endDate?.let(::formatHangoutDate)
    val dateLabel = when {
        start != null && end != null && start != end -> "$start – $end"
        start != null -> start
        end != null -> end
        else -> null
    }
    return listOfNotNull(dateLabel, formatCents(hangout.totalCents)).joinToString(" · ")
}

private fun memoryCountLabel(count: Int): String = if (count == 1) "1 note" else "$count notes"

@Composable
private fun HangoutsSection(
    hangouts: List<HangoutDto>,
    currentUsername: String?,
    isLoading: Boolean,
    onAddHangout: () -> Unit,
    onEditHangout: (HangoutDto) -> Unit,
    onAddMemory: (HangoutDto) -> Unit,
    onEditMemory: (HangoutDto, HangoutMemoryDto) -> Unit,
    onDeleteMemory: (HangoutDto, HangoutMemoryDto) -> Unit,
    onLongPressHangout: (HangoutDto) -> Unit,
) {
    if (isLoading) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, top = 4.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            repeat(3) { SkeletonHangoutCard() }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 210.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            NewHangoutButton(onClick = onAddHangout)
        }
        itemsIndexed(hangouts, key = { _, h -> h.id }) { index, hangout ->
            HangoutCard(
                hangout = hangout,
                color = HANGOUT_PALETTE[index % HANGOUT_PALETTE.size],
                currentUsername = currentUsername,
                onEdit = { onEditHangout(hangout) },
                onAddMemory = { onAddMemory(hangout) },
                onEditMemory = { onEditMemory(hangout, it) },
                onDeleteMemory = { onDeleteMemory(hangout, it) },
                onLongPress = { onLongPressHangout(hangout) },
            )
        }
        if (hangouts.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                    Text("no hangouts yet", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun NewHangoutButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .dashedBorder(2.dp, Ink, shape)
            .background(Color.White, shape)
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("+ new hangout", style = MaterialTheme.typography.titleMedium, color = Ink)
    }
}

@Composable
private fun HangoutCard(
    hangout: HangoutDto,
    color: Color,
    currentUsername: String?,
    onEdit: () -> Unit,
    onAddMemory: () -> Unit,
    onEditMemory: (HangoutMemoryDto) -> Unit,
    onDeleteMemory: (HangoutMemoryDto) -> Unit,
    onLongPress: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(shape)
            .border(3.dp, Ink, shape)
            .background(Color.White, shape)
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
                onLongClick = onLongPress,
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().background(color).padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(hangout.name, style = MaterialTheme.typography.headlineSmall, color = Ink, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                CountBadge(memoryCountLabel(hangout.memories.size))
                Spacer(Modifier.width(6.dp))
                PillActionButton(label = "edit", background = Color.White, contentColor = Ink, onClick = onEdit)
            }
            Spacer(Modifier.height(4.dp))
            Text(hangoutSubtitle(hangout), style = PinguCoduType.monoLabel, color = Ink)
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(Ink))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (hangout.memories.isEmpty()) {
                Text(
                    "nothing written down yet. what did you like about this one?",
                    style = MaterialTheme.typography.bodySmall,
                    color = DescriptionGrey,
                )
            } else {
                hangout.memories.forEach { memory ->
                    MemoryRow(
                        memory = memory,
                        isPartnerMemory = currentUsername != null && memory.author != currentUsername,
                        onEdit = { onEditMemory(memory) },
                        onDelete = { onDeleteMemory(memory) },
                    )
                }
            }
            AddMemoryButton(onClick = onAddMemory)
        }
    }
}

@Composable
private fun CountBadge(label: String) {
    Box(
        modifier = Modifier
            .border(2.dp, Ink, RoundedCornerShape(50))
            .background(Color.White, RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 6.dp),
    ) {
        Text(label, style = PinguCoduType.monoLabel, color = Ink)
    }
}

@Composable
private fun MemoryRow(memory: HangoutMemoryDto, isPartnerMemory: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, Ink, shape)
            .background(if (isPartnerMemory) PinkTint else Color.White, shape)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Text(memory.text, style = MaterialTheme.typography.bodySmall, color = Ink)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "- ${LocalNameMask.current.resolve(memory.author)} · ${relativeTime(memory.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = DescriptionGrey,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PillActionButton(label = "edit", background = Color.White, contentColor = Ink, onClick = onEdit)
                if (!isPartnerMemory) {
                    PillActionButton(label = "delete", background = Color.White, contentColor = Ink, onClick = onDelete)
                }
            }
        }
    }
}

@Composable
private fun AddMemoryButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(shape, offsetX = 3.dp, offsetY = 3.dp)
            .border(3.dp, Ink, shape)
            .background(Pink, shape)
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("+ add a memory", style = MaterialTheme.typography.titleMedium, color = Ink)
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

/** One film from a name search: its title, and the line that tells same-named films apart. */
@Composable
private fun MovieResultRow(movie: MovieSearchResultDto, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, Ink, shape)
            .background(Color.White, shape)
            .clickableNoRipple(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(movie.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = Ink)
        Spacer(Modifier.height(2.dp))
        Text(movie.description, style = MaterialTheme.typography.labelSmall, color = DescriptionGrey)
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
    movieResults: List<MovieSearchResultDto>?,
    isSearchingMovies: Boolean,
    movieSearchError: String?,
    onSearchMovies: (String) -> Unit,
    onPickMovie: (MovieSearchResultDto) -> Unit,
    onClearMovieResults: () -> Unit,
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
        if (type !in LINK_PREVIEW_TYPES) type = preview.suggestedType
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
                NeoChoiceChip(label = spec.label, selected = type == spec.value, onClick = { type = spec.value })
            }
        }
        Spacer(Modifier.height(16.dp))

        if (supportsLink) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NeoField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = if (type == "place") "paste a google maps link" else "paste an imdb link",
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
            keyboardOptions = if (type == "movie") KeyboardOptions(imeAction = ImeAction.Search) else KeyboardOptions.Default,
            keyboardActions = KeyboardActions(onSearch = { if (title.isNotBlank()) onSearchMovies(title) }),
            modifier = Modifier.fillMaxWidth(),
        )

        if (type == "place") {
            Spacer(Modifier.height(10.dp))
            FindChip(label = "📍 find on maps", enabled = title.isNotBlank()) {
                uriHandler.openUri(googleMapsSearchUrl(title))
            }
        }
        if (type == "movie") {
            Spacer(Modifier.height(10.dp))
            FindChip(
                label = if (isSearchingMovies) "finding…" else "find movie",
                enabled = title.isNotBlank() && !isSearchingMovies && !isFetchingPreview,
            ) { onSearchMovies(title) }
            if (movieSearchError != null) {
                Spacer(Modifier.height(8.dp))
                ErrorBanner(movieSearchError)
            }
            if (movieResults != null) {
                Spacer(Modifier.height(10.dp))
                if (movieResults.isEmpty()) {
                    Text("no movies found - try another name", style = MaterialTheme.typography.labelSmall, color = DescriptionGrey)
                } else {
                    Text("pick the right one", style = MaterialTheme.typography.labelSmall, color = DescriptionGrey)
                    Spacer(Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        movieResults.forEach { movie ->
                            MovieResultRow(movie) {
                                // The picked film's title replaces the typed name.
                                titleTyped = false
                                onPickMovie(movie)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "none of these",
                        style = PinguCoduType.monoLabel,
                        color = DescriptionGrey,
                        modifier = Modifier.clickableNoRipple(onClick = onClearMovieResults),
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

// ---------- add/edit hangout ----------

@Composable
private fun HangoutFormDialog(
    hangout: HangoutDto?,
    dialogError: String?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (name: String, startDate: String?, endDate: String?, countdownDate: String?) -> Unit,
) {
    var name by remember { mutableStateOf(hangout?.name ?: "") }
    var addToCountdown by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf(hangout?.startDate) }
    var endDate by remember { mutableStateOf(hangout?.endDate) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val isValid = name.isNotBlank()
    // Only a new hangout that hasn't started yet can become a countdown - countdowns need today or later.
    val countdownDate = (startDate ?: endDate)
        ?.takeIf { hangout == null }
        ?.takeIf { date -> runCatching { !LocalDate.parse(date).isBefore(LocalDate.now()) }.getOrDefault(false) }

    NeoBottomSheet(title = if (hangout == null) "new hangout" else "edit hangout", onDismiss = onDismiss) {
        Text(
            "a trip, a one-day meet, a movie night - anything you'll want to group expenses and memories under.",
            style = MaterialTheme.typography.bodySmall,
            color = DescriptionGrey,
        )
        Spacer(Modifier.height(18.dp))

        SectionLabel("name")
        Spacer(Modifier.height(8.dp))
        NeoField(
            value = name,
            onValueChange = { name = it },
            placeholder = "new hangout",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        SectionLabel("dates - optional, leave them if it's a one-day thing")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DateField(label = "from", value = startDate, onClick = { showStartPicker = true }, onClear = { startDate = null }, modifier = Modifier.weight(1f))
            DateField(label = "to", value = endDate, onClick = { showEndPicker = true }, onClear = { endDate = null }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(20.dp))

        if (countdownDate != null) {
            SectionLabel("add to countdown?")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeoChoiceChip(label = "yes", selected = addToCountdown, onClick = { addToCountdown = true })
                NeoChoiceChip(label = "no", selected = !addToCountdown, onClick = { addToCountdown = false })
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "shows up in our dates, and we'll both get a push the day before and on the day.",
                style = MaterialTheme.typography.bodySmall,
                color = DescriptionGrey,
            )
            Spacer(Modifier.height(20.dp))
        }

        if (dialogError != null) {
            ErrorBanner(dialogError)
            Spacer(Modifier.height(12.dp))
        }

        SubmitButton(label = if (hangout == null) "create hangout" else "save changes", enabled = isValid && !isSubmitting) {
            onSubmit(name.trim(), startDate, endDate, countdownDate?.takeIf { addToCountdown })
        }
    }

    if (showStartPicker) {
        NeoDatePickerDialog(
            initialDate = runCatching { LocalDate.parse(startDate) }.getOrDefault(LocalDate.now()),
            onDismiss = { showStartPicker = false },
            onConfirm = { picked -> startDate = picked.toString(); showStartPicker = false },
        )
    }
    if (showEndPicker) {
        NeoDatePickerDialog(
            initialDate = runCatching { LocalDate.parse(endDate) }.getOrDefault(LocalDate.now()),
            onDismiss = { showEndPicker = false },
            onConfirm = { picked -> endDate = picked.toString(); showEndPicker = false },
        )
    }
}


// ---------- add/edit memory ----------

@Composable
private fun MemoryFormDialog(
    hangout: HangoutDto,
    memory: HangoutMemoryDto?,
    dialogError: String?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var text by remember { mutableStateOf(memory?.text ?: "") }
    val isValid = text.isNotBlank()

    NeoBottomSheet(title = if (memory == null) "add a memory" else "edit memory", onDismiss = onDismiss) {
        Text(hangout.name, style = PinguCoduType.monoLabel, color = DescriptionGrey)
        Spacer(Modifier.height(16.dp))
        NeoField(value = text, onValueChange = { text = it }, placeholder = "what happened?", minLines = 3, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))

        if (dialogError != null) {
            ErrorBanner(dialogError)
            Spacer(Modifier.height(12.dp))
        }

        SubmitButton(label = if (memory == null) "add memory" else "save changes", enabled = isValid && !isSubmitting) {
            onSubmit(text.trim())
        }
    }
}
