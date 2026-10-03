package com.nook.msgapp.nav

import android.net.Uri

/** Every screen inside the signed-in app. */
object Routes {
    const val HOME = "home"
    const val CHAT = "chat/{chatId}"
    const val CHAT_INFO = "chatInfo/{chatId}"
    const val NEW_GROUP = "newGroup"
    const val PACK = "pack/{packId}"
    const val EDIT_PROFILE = "settings/profile"
    const val APP_LOCK = "settings/appLock"
    const val BLOCKED = "settings/blocked"
    const val DISAPPEARING = "settings/disappearing"
    const val NOTIFICATIONS = "settings/notifications"
    const val APPEARANCE = "settings/appearance"
    const val HELP = "settings/help"
    const val PRIVACY = "privacy"

    fun chat(chatId: String) = "chat/${Uri.encode(chatId)}"
    fun chatInfo(chatId: String) = "chatInfo/${Uri.encode(chatId)}"
    fun pack(packId: String) = "pack/${Uri.encode(packId)}"
}
