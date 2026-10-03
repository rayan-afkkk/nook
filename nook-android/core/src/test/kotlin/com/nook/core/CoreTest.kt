package com.nook.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class UsernameTest {
    @Test fun normalises() = assertEquals("ali_k", Username.normalize("  @Ali_K "))

    @Test fun accepts() {
        for (n in listOf("ali", "ali.k", "a_1", "nook_fan", "abcdefghijklmnopqrst")) {
            assertNull(n, Username.validate(n))
            assertTrue(n, Regex(Username.PATTERN).matches(n))
        }
    }

    @Test fun rejects() {
        val cases = mapOf(
            "ab" to "At least", "abcdefghijklmnopqrstu" to "At most", "1ali" to "Start with a letter",
            "ali-k" to "Only letters", "ali." to "end with", "ali_" to "end with", "a..b" to "two dots", "admin" to "reserved",
        )
        for ((name, msg) in cases) assertTrue(name, Username.validate(name)!!.contains(msg))
    }
}

class StageTest {
    private fun stage(
        seen: Boolean = false, primed: Boolean = false, auth: AuthStatus = AuthStatus.SignedOut,
        profile: ProfileState = ProfileState.Loading, lock: Boolean? = false,
    ) = Stage.compute(seen, primed, auth, profile, lock)

    private val p = ProfileState.Ready(Profile("u", "ali", "Ali"))

    @Test fun waits() {
        assertEquals(AppStage.Loading, stage(auth = AuthStatus.Loading))
        assertEquals(AppStage.Loading, stage(lock = null))
    }

    @Test fun introThenSignIn() {
        assertEquals(AppStage.Intro, stage())
        assertEquals(AppStage.SignIn, stage(seen = true))
    }

    @Test fun newAccountFlow() {
        val s = AuthStatus.SignedIn
        assertEquals(AppStage.ProfileLoading, stage(seen = true, auth = s))
        assertEquals(AppStage.Username, stage(seen = true, auth = s, profile = ProfileState.Missing))
        assertEquals(AppStage.Permissions, stage(seen = true, auth = s, profile = p))
        assertEquals(AppStage.SetLock, stage(seen = true, auth = s, profile = p, primed = true))
        assertEquals(AppStage.App, stage(seen = true, auth = s, profile = p, primed = true, lock = true))
    }
}

class ChatLogicTest {
    @Test fun dmIds() {
        assertEquals(ChatLogic.directChatId("b", "a"), ChatLogic.directChatId("a", "b"))
        assertEquals("dm_a_b", ChatLogic.directChatId("b", "a"))
    }

    @Test fun previews() {
        assertEquals("hi there", ChatLogic.previewFor(MessageKind.Text, "  hi \n there "))
        assertEquals(120, ChatLogic.previewFor(MessageKind.Text, "x".repeat(500)).length)
        assertEquals("Photo", ChatLogic.previewFor(MessageKind.Image, null))
        assertEquals("a.pdf", ChatLogic.previewFor(MessageKind.File, null, "a.pdf"))
        assertEquals("You: Photo", ChatLogic.lastMessageLine(LastMessage("me", MessageKind.Image, "Photo"), true))
        assertEquals("Message deleted", ChatLogic.lastMessageLine(LastMessage("me", MessageKind.Deleted, ""), true))
    }

    @Test fun unread() {
        assertTrue(ChatLogic.isUnread(10, 5, false))
        assertTrue(ChatLogic.isUnread(10, null, false))
        assertFalse(ChatLogic.isUnread(10, 5, true))
        assertFalse(ChatLogic.isUnread(5, 10, false))
        assertFalse(ChatLogic.isUnread(null, null, false))
    }

    @Test fun expiryAndSeen() {
        assertTrue(ChatLogic.isExpired(5, 10))
        assertFalse(ChatLogic.isExpired(null, 10))
        assertTrue(ChatLogic.seenByAll(10, listOf("me", "a"), "me", mapOf("a" to 11L)))
        assertFalse(ChatLogic.seenByAll(10, listOf("me", "a", "b"), "me", mapOf("a" to 11L)))
    }

    @Test fun reactions() {
        val pills = ChatLogic.groupReactions(mapOf("a" to "❤️", "b" to "❤️", "me" to "😂"), "me")
        assertEquals(ReactionPill("❤️", 2, false), pills[0])
        assertEquals(ReactionPill("😂", 1, true), pills[1])
    }

    @Test fun durationsAndWaveforms() {
        assertEquals("1:05", ChatLogic.durationLabel(65_000))
        assertEquals("0:00", ChatLogic.durationLabel(-5))
        val w = ChatLogic.toWaveform(List(100) { it * 300 }, 40)
        assertEquals(40, w.size)
        assertTrue(w.all { it in 0f..1f })
        assertEquals(40, ChatLogic.toWaveform(emptyList()).size)
        assertEquals(40, ChatLogic.toWaveform(listOf(1000, 2000)).size)
    }

    @Test fun labels() {
        val z = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 3, 10, 12, 0, 0, 0, z).toInstant().toEpochMilli()
        assertEquals("Today", ChatLogic.dayLabel(now - 3_600_000, now, z, Locale.UK))
        assertEquals("Yesterday", ChatLogic.dayLabel(now - 86_400_000, now, z, Locale.UK))
        assertEquals("3 Feb 2025", ChatLogic.dayLabel(now - 400L * 86_400_000, now, z, Locale.UK))
    }

    @Test fun thumbnails() {
        assertEquals(
            "https://res.cloudinary.com/x/image/upload/c_limit,w_200,q_auto,f_auto/a.jpg",
            ChatLogic.thumbnailUrl("https://res.cloudinary.com/x/image/upload/a.jpg", 200),
        )
    }
}

class FormatTest {
    @Test fun bytes() {
        assertEquals("0 MB", Format.bytes(0))
        assertEquals("512 B", Format.bytes(512))
        assertEquals("1.5 KB", Format.bytes(1536))
        assertEquals("10.0 MB", Format.bytes(10L * 1024 * 1024))
    }

    @Test fun initials() {
        assertEquals("AK", Format.initials("Ali  Khan"))
        assertEquals("A", Format.initials("ali"))
        assertEquals("?", Format.initials(" "))
        assertTrue(Format.hashString("abc") >= 0)
    }
}

class LockTest {
    @Test fun validation() {
        assertNotNull(LockCrypto.validateSecret(LockKind.Pin, "123"))
        assertNotNull(LockCrypto.validateSecret(LockKind.Pin, "12a456"))
        assertNotNull(LockCrypto.validateSecret(LockKind.Pin, "111111"))
        assertNotNull(LockCrypto.validateSecret(LockKind.Pin, "123456"))
        assertNotNull(LockCrypto.validateSecret(LockKind.Pin, "654321"))
        assertNull(LockCrypto.validateSecret(LockKind.Pin, "481902"))
        assertNull(LockCrypto.validateSecret(LockKind.Password, "hunter22"))
    }

    @Test fun verifyAndRoundTrip() {
        val r = LockCrypto.createRecord(LockKind.Pin, "481902", biometric = true, iterations = 1000)
        assertTrue(LockCrypto.verify(r, "481902"))
        assertFalse(LockCrypto.verify(r, "481903"))
        val back = LockRecord.parse(r.serialize())
        assertEquals(r, back)
        assertNull(LockRecord.parse("garbage"))
        assertNull(LockRecord.parse(null))
        val other = LockCrypto.createRecord(LockKind.Pin, "481902", biometric = false, iterations = 1000)
        assertNotEquals(r.hash, other.hash)
    }

    @Test fun cooldown() {
        assertEquals(0, LockCrypto.cooldownMs(4))
        assertEquals(30_000, LockCrypto.cooldownMs(5))
        assertEquals(60_000, LockCrypto.cooldownMs(6))
        assertEquals(15 * 60_000L, LockCrypto.cooldownMs(40))
        assertTrue(LockCrypto.safeEqual("ab", "ab"))
        assertFalse(LockCrypto.safeEqual("ab", "ac"))
    }
}
