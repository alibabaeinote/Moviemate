package com.moviemate.app.data.session

import com.moviemate.app.data.model.Pair
import com.moviemate.app.data.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Identity getters — [Session.partnerName] and [Session.partnerAvatarUrl] are
 * the only way a partner's picture or name ever reaches a screen, since the
 * rules let a user read only their own users/{uid}. Reading the wrong side of
 * the pair here shows someone their own name back at them as "their partner".
 */
class SessionTest {

    private fun pair(
        userA: String = "alice",
        userB: String? = "bob",
        userAName: String? = "Alice",
        userAAvatarUrl: String? = "https://example/alice.jpg",
        userBName: String? = "Bob",
        userBAvatarUrl: String? = "https://example/bob.jpg",
    ) = Pair(
        id = "p1",
        userA = userA,
        userB = userB,
        inviteCode = "ABC123",
        userAName = userAName,
        userAAvatarUrl = userAAvatarUrl,
        userBName = userBName,
        userBAvatarUrl = userBAvatarUrl,
    )

    @Test
    fun `userA sees userB's name and picture as the partner's`() {
        val session = Session(uid = "alice", user = User(uid = "alice"), pair = pair())

        assertEquals("Bob", session.partnerName)
        assertEquals("https://example/bob.jpg", session.partnerAvatarUrl)
    }

    @Test
    fun `userB sees userA's name and picture as the partner's, not their own`() {
        val session = Session(uid = "bob", user = User(uid = "bob"), pair = pair())

        assertEquals("Alice", session.partnerName)
        assertEquals("https://example/alice.jpg", session.partnerAvatarUrl)
    }

    @Test
    fun `no pair yet means no partner identity, not a guess`() {
        val session = Session(uid = "alice", user = User(uid = "alice"), pair = null)

        assertNull(session.partnerName)
        assertNull(session.partnerAvatarUrl)
    }

    @Test
    fun `before onUserProfileUpdated has ever run, the pair's fields are simply absent`() {
        val freshPair = pair(userAName = null, userAAvatarUrl = null)
        val session = Session(uid = "bob", user = User(uid = "bob"), pair = freshPair)

        assertNull(session.partnerName)
        assertNull(session.partnerAvatarUrl)
    }

    @Test
    fun `displayName and avatarUrl read the caller's own user document`() {
        val session = Session(
            uid = "alice",
            user = User(uid = "alice", name = "Alice", avatarUrl = "https://example/alice.jpg"),
            pair = pair(),
        )

        assertEquals("Alice", session.displayName)
        assertEquals("https://example/alice.jpg", session.avatarUrl)
    }

    @Test
    fun `a blank name reads as absent, not as an empty label`() {
        val session = Session(uid = "alice", user = User(uid = "alice", name = "   "), pair = null)

        assertNull(session.displayName)
    }
}
