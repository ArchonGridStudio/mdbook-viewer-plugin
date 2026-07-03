package com.github.nbugash.mdbookviewerplugin

import com.github.nbugash.mdbookviewerplugin.git.looksLikeGitRepo
import com.github.nbugash.mdbookviewerplugin.git.toCloneUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitRepoTest {

    @Test
    fun `recognizes a github repo url`() {
        assertTrue(looksLikeGitRepo("https://github.com/rust-lang/mdBook"))
    }

    @Test
    fun `recognizes an explicit dot-git url`() {
        assertTrue(looksLikeGitRepo("https://example.com/team/book.git"))
    }

    @Test
    fun `recognizes an scp-style git url`() {
        assertTrue(looksLikeGitRepo("git@github.com:owner/repo.git"))
    }

    @Test
    fun `rejects a served localhost url`() {
        assertFalse(looksLikeGitRepo("http://localhost:3000"))
    }

    @Test
    fun `rejects a github pages url (served book, not a repo)`() {
        assertFalse(looksLikeGitRepo("https://someone.github.io/mybook/"))
    }

    @Test
    fun `rejects an empty string`() {
        assertFalse(looksLikeGitRepo("   "))
    }

    @Test
    fun `derives a clone url from a plain github repo url`() {
        assertEquals("https://github.com/rust-lang/mdBook.git", toCloneUrl("https://github.com/rust-lang/mdBook"))
    }

    @Test
    fun `derives a clone url from a deep github url`() {
        assertEquals("https://github.com/rust-lang/mdBook.git", toCloneUrl("https://github.com/rust-lang/mdBook/tree/master/guide"))
    }

    @Test
    fun `leaves an explicit dot-git url unchanged`() {
        assertEquals("https://github.com/rust-lang/mdBook.git", toCloneUrl("https://github.com/rust-lang/mdBook.git"))
    }

    @Test
    fun `leaves an scp-style url unchanged`() {
        assertEquals("git@github.com:owner/repo.git", toCloneUrl("git@github.com:owner/repo.git"))
    }
}
