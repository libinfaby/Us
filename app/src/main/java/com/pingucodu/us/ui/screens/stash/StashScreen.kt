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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.pingucodu.us.data.network.StashItemDto
import com.pingucodu.us.data.network.StashItemRequest
import com.pingucodu.us.ui.theme.BorderWidth
import com.pingucodu.us.ui.theme.Coral
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

@Composable
fun StashScreen(modifier: Modifier = Modifier, viewModel: StashViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
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

            if (uiState.errorMessage != null) {
                Text(
                    uiState.errorMessage!!,
                    color = Coral,
                    style = MaterialTheme.typography.bodySmall,
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
            defaultType = uiState.typeFilter,
            tagsByType = uiState.tagsByType,
            dialogError = uiState.dialogError,
            isSubmitting = uiState.isSubmitting,
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
                Text("nothing here yet", style = MaterialTheme.typography.bodyMedium)
            }
        }
        else -> {
            // pending todos first, done ones sink to the bottom; everything else keeps its order.
            val sortedItems = items.sortedBy { it.status == "done" }
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 210.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(sortedItems, key = { it.id }) { item ->
                    StashItemCard(
                        item = item,
                        onToggle = { onToggle(item.id) },
                        onEdit = { onEdit(item) },
                        onDelete = { onDelete(item) },
                    )
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
private fun PillActionButton(label: String, background: Color, contentColor: Color, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .border(1.5.dp, Ink, RoundedCornerShape(50))
            .background(background, RoundedCornerShape(50))
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = contentColor)
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

// ---------- shared dialog chrome ----------

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}

@Composable
private fun Modifier.clickableNoRipple(enabled: Boolean, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
private fun SheetCloseButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .border(BorderWidth, Ink, RoundedCornerShape(12.dp))
            .background(Color.White, RoundedCornerShape(12.dp))
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("×", style = MaterialTheme.typography.headlineMedium, color = Ink)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = PinguCoduType.monoLabel, color = Ink)
}

@Composable
private fun TypePickerChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .border(2.dp, Ink, shape)
            .background(if (selected) Teal else Color.White, shape)
            .clickableNoRipple(onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Ink)
    }
}

@Composable
private fun FindOnMapsChip(enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .border(2.dp, Ink, shape)
            .background(if (enabled) Teal else Color.White, shape)
            .clickableNoRipple(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("📍 find on maps", style = MaterialTheme.typography.labelLarge, color = if (enabled) Ink else Ink.copy(alpha = 0.35f))
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

@Composable
private fun NeoField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    backgroundColor: Color = Color.White,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 13.dp),
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .hardShadow(shape, offsetX = 3.dp, offsetY = 3.dp)
            .border(3.dp, Ink, shape)
            .background(backgroundColor, shape)
            .padding(contentPadding),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = textStyle, color = PlaceholderGrey)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle.copy(color = Ink),
            cursorBrush = SolidColor(Ink),
            minLines = minLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SubmitButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow(shape, offsetX = 4.dp, offsetY = 4.dp)
            .border(BorderWidth, Ink, shape)
            .background(Ink, shape)
            .clickableNoRipple(enabled = enabled, onClick = onClick)
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.headlineLarge, color = Color.White)
    }
}

/** Shared bottom-sheet chrome used by the money/cycle forms: a dimmed scrim, a slide-up
 * PinkTint sheet with rounded top corners, and a close ("×") button next to the title. */
@Composable
private fun StashBottomSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    // Dialog() opens its own window, which resets LocalDensity instead of inheriting the
    // app's global font-scale override from UsTheme - re-provide the correct one (captured
    // here, before the Dialog, where the override is still in effect) so this sheet's text
    // actually matches the rest of the app instead of quietly rendering ~25% smaller.
    val outerDensity = LocalDensity.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            window.setDimAmount(0f)
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }

        val sheetHeight = LocalConfiguration.current.screenHeightDp.dp - 70.dp
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }

        CompositionLocalProvider(LocalDensity provides outerDensity) {
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Ink.copy(alpha = 0.45f))
                    .clickableNoRipple(onDismiss),
            )

            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(animationSpec = tween(240), initialOffsetY = { it }) + fadeIn(tween(240)),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = sheetHeight)
                        .border(4.dp, Ink, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .background(PinkTint, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .navigationBarsPadding()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 22.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = MaterialTheme.typography.displayMedium, modifier = Modifier.weight(1f))
                        SheetCloseButton(onClick = onDismiss)
                    }
                    Spacer(Modifier.height(16.dp))
                    content()
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        }
    }
}

// ---------- add/edit stash item ----------

@Composable
private fun StashItemFormDialog(
    item: StashItemDto?,
    defaultType: String?,
    tagsByType: Map<String, List<String>>,
    dialogError: String?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (StashItemRequest) -> Unit,
    onRenameTag: (type: String, oldTag: String, newTag: String) -> Unit,
    onDeleteTag: (type: String, tag: String) -> Unit,
) {
    var type by remember { mutableStateOf(item?.type ?: defaultType ?: STASH_TYPES.first().value) }
    var title by remember { mutableStateOf(item?.title ?: "") }
    var body by remember { mutableStateOf(item?.body ?: "") }
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

    StashBottomSheet(title = if (item == null) "share something" else "edit item", onDismiss = onDismiss) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            STASH_TYPES.forEach { spec ->
                TypePickerChip(label = spec.label, selected = type == spec.value, onClick = { type = spec.value })
            }
        }
        Spacer(Modifier.height(16.dp))

        NeoField(
            value = title,
            onValueChange = { title = it },
            placeholder = titlePlaceholder(type),
            modifier = Modifier.fillMaxWidth(),
        )

        if (type == "place") {
            Spacer(Modifier.height(10.dp))
            FindOnMapsChip(enabled = title.isNotBlank()) {
                uriHandler.openUri(googleMapsSearchUrl(title))
            }
        }
        Spacer(Modifier.height(14.dp))

        NeoField(
            value = body,
            onValueChange = { body = it },
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
            Text(dialogError, color = Coral, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
        }

        SubmitButton(label = if (item == null) "drop it in" else "save changes", enabled = isValid && !isSubmitting) {
            onSubmit(StashItemRequest(type = type, title = title.trim(), body = body.ifBlank { null }, tags = finalTags()))
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

    StashBottomSheet(title = "edit tag", onDismiss = onDismiss) {
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
    onSubmit: (name: String, startDate: String?, endDate: String?) -> Unit,
) {
    var name by remember { mutableStateOf(hangout?.name ?: "") }
    var startDate by remember { mutableStateOf(hangout?.startDate) }
    var endDate by remember { mutableStateOf(hangout?.endDate) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val isValid = name.isNotBlank()

    StashBottomSheet(title = if (hangout == null) "new hangout" else "edit hangout", onDismiss = onDismiss) {
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

        if (dialogError != null) {
            Text(dialogError, color = Coral, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
        }

        SubmitButton(label = if (hangout == null) "create hangout" else "save changes", enabled = isValid && !isSubmitting) {
            onSubmit(name.trim(), startDate, endDate)
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

@Composable
private fun DateField(label: String, value: String?, onClick: () -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .hardShadow(shape, offsetX = 3.dp, offsetY = 3.dp)
            .border(BorderWidth, Ink, shape)
            .background(Color.White, shape)
            .clickableNoRipple(onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            value?.let { formatHangoutDate(it) } ?: label,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = if (value != null) Ink else PlaceholderGrey,
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Text("×", style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.clickableNoRipple(onClear))
        }
    }
}

/** The same neo-brutalist calendar used by Money's expense form, duplicated here per this
 * codebase's convention of each screen keeping its own private dialog chrome. */
@Composable
private fun NeoDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val today = remember { LocalDate.now() }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var viewMonth by remember { mutableStateOf(YearMonth.from(initialDate)) }

    val outerDensity = LocalDensity.current
    Dialog(onDismissRequest = onDismiss) {
      CompositionLocalProvider(LocalDensity provides outerDensity) {
        val cardShape = RoundedCornerShape(30.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .hardShadow(cardShape, offsetX = 5.dp, offsetY = 5.dp)
                .border(4.dp, Ink, cardShape)
                .background(PinkTint, cardShape)
                .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "when is it",
                        style = PinguCoduType.monoLabel.copy(fontSize = 13.sp),
                        color = Ink.copy(alpha = 0.55f),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(friendlyDateLabel(selectedDate, today), style = MaterialTheme.typography.displayLarge)
                }
                Column(
                    modifier = Modifier
                        .hardShadow(RoundedCornerShape(18.dp), offsetX = 3.dp, offsetY = 3.dp)
                        .border(BorderWidth, Ink, RoundedCornerShape(18.dp))
                        .background(Pink, RoundedCornerShape(18.dp))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        selectedDate.month.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH).uppercase(),
                        style = PinguCoduType.monoLabel.copy(fontSize = 12.sp),
                    )
                    Text(selectedDate.dayOfMonth.toString(), style = MaterialTheme.typography.headlineLarge)
                }
            }
            Spacer(Modifier.height(22.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickPickChip(
                    label = "today",
                    selected = selectedDate == today,
                    onClick = { selectedDate = today; viewMonth = YearMonth.from(today) },
                )
                QuickPickChip(
                    label = "tomorrow",
                    selected = selectedDate == today.plusDays(1),
                    onClick = { selectedDate = today.plusDays(1); viewMonth = YearMonth.from(selectedDate) },
                )
                QuickPickChip(
                    label = "+1 week",
                    selected = selectedDate == today.plusWeeks(1),
                    onClick = { selectedDate = today.plusWeeks(1); viewMonth = YearMonth.from(selectedDate) },
                )
            }
            Spacer(Modifier.height(26.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                NavArrowButton(symbol = "‹", onClick = { viewMonth = viewMonth.minusMonths(1) })
                Text(
                    "${viewMonth.month.getDisplayName(JavaTextStyle.FULL, Locale.ENGLISH).lowercase()} ${viewMonth.year}",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                NavArrowButton(symbol = "›", onClick = { viewMonth = viewMonth.plusMonths(1) })
            }
            Spacer(Modifier.height(18.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                WeekdayLetters.forEach { letter ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(letter, style = PinguCoduType.monoLabel.copy(fontSize = 13.sp), color = Ink.copy(alpha = 0.4f))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            val firstOfMonth = viewMonth.atDay(1)
            val leadingBlanks = firstOfMonth.dayOfWeek.value % 7
            val daysInMonth = viewMonth.lengthOfMonth()
            val cells: List<LocalDate?> = buildList {
                repeat(leadingBlanks) { add(null) }
                for (day in 1..daysInMonth) add(viewMonth.atDay(day))
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                cells.chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        week.forEach { date ->
                            Box(Modifier.weight(1f).aspectRatio(1f)) {
                                if (date != null) {
                                    DayCell(
                                        date = date,
                                        selected = date == selectedDate,
                                        onClick = { selectedDate = date },
                                    )
                                }
                            }
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .border(BorderWidth, Ink, RoundedCornerShape(16.dp))
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .clickableNoRipple(onDismiss)
                        .padding(horizontal = 26.dp, vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("cancel", style = MaterialTheme.typography.titleLarge)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .border(BorderWidth, Ink, RoundedCornerShape(16.dp))
                        .background(Ink, RoundedCornerShape(16.dp))
                        .clickableNoRipple { onConfirm(selectedDate) }
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "use ${shortDateLabel(selectedDate)}",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                    )
                }
            }
        }
      }
    }
}

private val WeekdayLetters = listOf("s", "m", "t", "w", "t", "f", "s")

private fun shortDateLabel(date: LocalDate): String =
    "${date.month.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH).lowercase()} ${date.dayOfMonth}"

private fun friendlyDateLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "today"
    today.minusDays(1) -> "yesterday"
    else -> shortDateLabel(date)
}

@Composable
private fun RowScope.QuickPickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .border(BorderWidth, Ink, RoundedCornerShape(50))
            .background(if (selected) Pink else Color.White, RoundedCornerShape(50))
            .clickableNoRipple(onClick)
            .padding(horizontal = 6.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = Ink, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun NavArrowButton(symbol: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(50.dp)
            .border(BorderWidth, if (enabled) Ink else Ink.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .background(Color.White, RoundedCornerShape(16.dp))
            .clickableNoRipple(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = MaterialTheme.typography.headlineSmall, color = if (enabled) Ink else Ink.copy(alpha = 0.3f))
    }
}

@Composable
private fun DayCell(date: LocalDate, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .let { if (selected) it.hardShadow(shape, offsetX = 2.dp, offsetY = 2.dp) else it }
            .border(if (enabled) BorderWidth else 1.5.dp, if (enabled) Ink else Ink.copy(alpha = 0.25f), shape)
            .background(if (selected) Pink else Color.White.copy(alpha = if (enabled) 1f else 0.5f), shape)
            .clickableNoRipple(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) Ink else Ink.copy(alpha = 0.3f),
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

    StashBottomSheet(title = if (memory == null) "add a memory" else "edit memory", onDismiss = onDismiss) {
        Text(hangout.name, style = PinguCoduType.monoLabel, color = DescriptionGrey)
        Spacer(Modifier.height(16.dp))
        NeoField(value = text, onValueChange = { text = it }, placeholder = "what happened?", minLines = 3, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))

        if (dialogError != null) {
            Text(dialogError, color = Coral, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
        }

        SubmitButton(label = if (memory == null) "add memory" else "save changes", enabled = isValid && !isSubmitting) {
            onSubmit(text.trim())
        }
    }
}
