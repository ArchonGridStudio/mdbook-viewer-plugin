package com.github.nbugash.mdbookviewerplugin.git

import java.net.URI

private val GIT_HOSTS = listOf("github.com", "gitlab.com", "bitbucket.org")

/**
 * True if [input] looks like a git repository URL to clone, as opposed to a URL of an
 * already-served book. Recognizes scp-style (`git@host:owner/repo`), explicit `.git`
 * URLs, and web URLs on known git hosts. Note that `*.github.io` is a *served* GitHub
 * Pages site, not a repo, so it is deliberately rejected.
 */
fun looksLikeGitRepo(input: String): Boolean {
    val s = input.trim()
    if (s.isEmpty()) return false
    if (s.startsWith("git@")) return true
    if (s.endsWith(".git")) return true
    val host = runCatching { URI(s).host }.getOrNull() ?: return false
    return GIT_HOSTS.any { host.equals(it, ignoreCase = true) }
}

/**
 * Normalizes [input] into a clonable git URL. scp-style and explicit `.git` URLs are
 * returned as-is; a web URL on a git host is reduced to `https://host/owner/repo.git`
 * (so deep links like `.../tree/main/guide` still clone the repo root).
 */
fun toCloneUrl(input: String): String {
    val s = input.trim()
    if (s.startsWith("git@") || s.endsWith(".git")) return s
    val uri = runCatching { URI(s) }.getOrNull() ?: return s
    val host = uri.host ?: return s
    val segments = uri.path.orEmpty().trim('/').split('/').filter { it.isNotEmpty() }
    if (segments.size < 2) return s
    val owner = segments[0]
    val repo = segments[1].removeSuffix(".git")
    return "https://$host/$owner/$repo.git"
}
