package com.nook.msgapp.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nook.core.Sticker
import com.nook.core.StickerPack
import com.nook.msgapp.data.Giphy
import com.nook.msgapp.data.GiphyItem
import com.nook.msgapp.data.GiphyType
import com.nook.msgapp.data.Stickers
import com.nook.msgapp.data.friendly
import com.nook.msgapp.ui.components.EmptyState
import com.nook.msgapp.ui.components.NText
import com.nook.msgapp.ui.components.Segmented
import com.nook.msgapp.ui.components.Skeleton
import com.nook.msgapp.ui.components.pressScale
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType
import com.nook.msgapp.ui.theme.Radius
import com.nook.msgapp.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** A compact, hand-picked emoji set (the system font renders them, nothing is downloaded). */
val EMOJI_GROUPS: List<Pair<String, List<String>>> = listOf(
    "Smileys" to "😀 😃 😄 😁 😆 😅 🤣 😂 🙂 😉 😊 😇 🥰 😍 🤩 😘 😋 😛 😜 🤪 😝 🤗 🤭 🤫 🤔 🤐 😐 😑 😶 😏 😒 🙄 😬 😌 😔 😪 😴 😷 🤒 🤕 🥵 🥶 🥴 😵 🤯 🤠 🥳 😎 🤓 🧐 😕 😟 🙁 😮 😯 😲 😳 🥺 😦 😧 😨 😰 😥 😢 😭 😱 😖 😣 😞 😓 😩 😫 🥱 😤 😡 😠 🤬 😈 💀 🤡 👻 👽 🤖 💩".split(" "),
    "Gestures" to "👍 👎 👏 🙌 🤝 🙏 👋 🤙 💪 🫶 👌 🤌 ✌️ 🤞 🤟 🤘 👀 🧠 🫡 🫠 🫣 🤷 🤦 💃 🕺".split(" "),
    "Hearts" to "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 💔 ❤️‍🔥 💕 💞 💓 💗 💖 💘 💝".split(" "),
    "Things" to "🔥 ✨ 🎉 🎊 🎂 🎁 🏆 ⚽ 🏀 🎮 🎧 🎵 📸 🍕 🍔 🍟 🌮 🍜 🍣 🍩 🍪 ☕ 🍺 🥂 🌙 ☀️ 🌈 ⭐ 💯 ✅ ❌ ⚠️ 💤 💸 🚗 ✈️ 🏠 📍 ⏰".split(" "),
)

private enum class PanelTab(val label: String) { Emoji("Emoji"), Gifs("GIFs"), Stickers("Stickers"), Packs("Ours") }

/** Emoji / GIF / sticker panel shown in place of the keyboard. */
@Composable
fun MediaPanel(
    onEmoji: (String) -> Unit,
    onGiphy: (GiphyItem, GiphyType) -> Unit,
    onSticker: (Sticker) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Nook.colors
    var tab by remember { mutableStateOf(PanelTab.Emoji) }
    var query by remember { mutableStateOf("") }
    Column(modifier.fillMaxWidth().background(c.background)) {
        Column(Modifier.padding(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Segmented(
                options = PanelTab.entries.map { it to it.label },
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.fillMaxWidth(),
            )
            if (tab == PanelTab.Gifs || tab == PanelTab.Stickers) {
                SearchField(query, { query = it }, if (tab == PanelTab.Gifs) "Search GIFs" else "Search stickers")
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (tab) {
                PanelTab.Emoji -> EmojiGrid(onEmoji)
                PanelTab.Gifs -> GiphyGrid(GiphyType.Gifs, query, columns = 2) { onGiphy(it, GiphyType.Gifs) }
                PanelTab.Stickers -> GiphyGrid(GiphyType.Stickers, query, columns = 3) { onGiphy(it, GiphyType.Stickers) }
                PanelTab.Packs -> PacksGrid(onSticker)
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String) {
    val c = Nook.colors
    val focus = LocalFocusManager.current
    val shape = RoundedCornerShape(Radius.pill)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 38.dp)
            .clip(shape)
            .background(c.surface)
            .border(1.dp, c.border, shape)
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = c.textMuted, modifier = Modifier.size(16.dp))
        Box(Modifier.weight(1f).padding(start = Spacing.xs)) {
            if (value.isEmpty()) NText(placeholder, NookType.body, c.textMuted, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = { onChange(it.take(50)) },
                singleLine = true,
                textStyle = NookType.body.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Grid of emoji with small group headers. */
@Composable
fun EmojiGrid(onEmoji: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Nook.colors
    LazyVerticalGrid(
        columns = GridCells.Fixed(8),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Spacing.xs, end = Spacing.xs, bottom = Spacing.md),
    ) {
        EMOJI_GROUPS.forEach { (label, emoji) ->
            item(key = "h-$label", span = { GridItemSpan(maxLineSpan) }) {
                NText(label.uppercase(), NookType.micro, c.textMuted, Modifier.padding(start = 4.dp, top = Spacing.sm, bottom = 4.dp))
            }
            items(emoji, key = { "$label-$it" }) { e ->
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .pressScale(scaleTo = 0.8f) { onEmoji(e) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(e, fontSize = 24.sp, lineHeight = 30.sp)
                }
            }
        }
    }
}

/** Giphy trending/search grid with debounced search and infinite scroll. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GiphyGrid(type: GiphyType, query: String, columns: Int, onPick: (GiphyItem) -> Unit) {
    val c = Nook.colors
    val scope = rememberCoroutineScope()
    var results by remember(type) { mutableStateOf<List<GiphyItem>>(emptyList()) }
    var next by remember(type) { mutableStateOf<Int?>(null) }
    var loading by remember(type) { mutableStateOf(true) }
    var error by remember(type) { mutableStateOf<String?>(null) }
    var requestId by remember { mutableIntStateOf(0) }
    val state = rememberLazyStaggeredGridState()

    fun load(offset: Int, replace: Boolean) {
        val id = ++requestId
        loading = true
        scope.launch {
            try {
                val (found, nextOffset) = Giphy.search(type, query, offset)
                if (id != requestId) return@launch
                results = if (replace) found else (results + found).distinctBy { it.id }
                next = nextOffset
                error = null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (id == requestId) error = e.friendly()
            } finally {
                if (id == requestId) loading = false
            }
        }
    }

    LaunchedEffect(type, query) {
        if (query.isNotBlank()) delay(400)
        load(0, true)
        if (results.isNotEmpty()) state.scrollToItem(0)
    }

    val nearEnd by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 6
        }
    }
    LaunchedEffect(nearEnd, loading, next) {
        val n = next
        if (nearEnd && !loading && n != null && results.isNotEmpty()) load(n, false)
    }

    if (error != null && results.isEmpty()) {
        EmptyState("No GIFs right now", error ?: "", icon = Icons.Rounded.CloudOff)
        return
    }
    if (!loading && results.isEmpty()) {
        EmptyState("Nothing found", "Try another word.", icon = Icons.Rounded.Search)
        return
    }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(columns),
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalItemSpacing = Spacing.xs,
    ) {
        items(results, key = { it.id }) { item ->
            val ratio = if (item.width > 0 && item.height > 0) (item.width.toFloat() / item.height).coerceIn(0.5f, 2.2f) else 1f
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (type == GiphyType.Stickers) 1f else ratio)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(if (type == GiphyType.Gifs) c.surfaceRaised else Color.Transparent)
                    .pressScale(scaleTo = 0.94f) { onPick(item) },
            ) {
                if (type == GiphyType.Gifs) {
                    AsyncImage(model = item.previewUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                AsyncImage(
                    model = item.url,
                    contentDescription = item.title.ifEmpty { if (type == GiphyType.Gifs) "GIF" else "Sticker" },
                    contentScale = if (type == GiphyType.Gifs) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        item(key = "footer", span = StaggeredGridItemSpan.FullLine) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (loading) CircularProgressIndicator(color = c.textMuted, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                NText("Powered by GIPHY", NookType.micro, c.textMuted)
            }
        }
    }
}

/** Our shared sticker packs (listened to only while this tab is open). */
@Composable
private fun PacksGrid(onSticker: (Sticker) -> Unit) {
    val c = Nook.colors
    var failed by remember { mutableStateOf<String?>(null) }
    val packs by remember { Stickers.packsFlow().catch { e -> failed = e.friendly() } }
        .collectAsState(initial = null as List<StickerPack>?)
    val list = packs
    when {
        list == null && failed != null -> EmptyState("Couldn't load packs", failed ?: "", icon = Icons.Rounded.CloudOff)
        list == null -> Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            repeat(4) { Skeleton(Modifier.size(68.dp)) }
        }
        list.isEmpty() -> EmptyState("No packs yet", "Make one from the Stickers tab with any photo.", icon = Icons.Rounded.Collections)
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(72.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            list.forEach { pack ->
                item(key = "p-${pack.id}", span = { GridItemSpan(maxLineSpan) }) {
                    NText(pack.name, NookType.captionBold, c.textMuted, Modifier.padding(start = 4.dp, top = Spacing.xs))
                }
                items(pack.stickers, key = { "${pack.id}-${it.publicId}-${it.url}" }) { s ->
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .pressScale(scaleTo = 0.9f) { onSticker(s) },
                    ) {
                        AsyncImage(model = s.url, contentDescription = "Sticker from ${pack.name}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

/** Height of the panel when it replaces the keyboard. */
val MEDIA_PANEL_HEIGHT = 300.dp
