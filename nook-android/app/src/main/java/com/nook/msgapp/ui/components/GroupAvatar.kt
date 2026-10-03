package com.nook.msgapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.People
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nook.core.Chat
import com.nook.core.ChatLogic
import com.nook.core.Format
import com.nook.msgapp.ui.theme.Nook
import com.nook.msgapp.ui.theme.NookType

/**
 * A group's avatar: its photo when one is set, otherwise a rounded pastel tile (colour seeded by the
 * chat id) with the group name's initials, or a people glyph when the group has no name.
 */
@Composable
fun GroupAvatar(chat: Chat, size: Dp, modifier: Modifier = Modifier) {
    val c = Nook.colors
    val shape = RoundedCornerShape(size * 0.34f)
    val photo = chat.photoURL
    if (!photo.isNullOrEmpty()) {
        AsyncImage(
            model = ChatLogic.thumbnailUrl(photo, if (size >= 64.dp) 512 else 160),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape).background(c.surfaceRaised),
        )
        return
    }
    val name = chat.name.orEmpty().trim()
    Box(
        modifier.size(size).clip(shape).background(c.pastelFor(chat.id)),
        contentAlignment = Alignment.Center,
    ) {
        if (name.isNotEmpty()) {
            NText(Format.initials(name), if (size >= 64.dp) NookType.headline else NookType.label, c.onPastel)
        } else {
            Icon(Icons.Rounded.People, contentDescription = null, tint = c.onPastel, modifier = Modifier.size(size * 0.45f))
        }
    }
}
