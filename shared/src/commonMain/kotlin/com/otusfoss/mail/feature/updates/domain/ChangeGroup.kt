package com.otusfoss.mail.feature.updates.domain

/** The user-facing changes to one [area] of the app, such as "Sidebar". */
data class ChangeGroup(
    val area: String,
    val changes: List<String>,
)

/**
 * The feature, fix, and speed changes in the notes of [builds], grouped by commit scope in the
 * order the scopes first appear. Commits for CI, the build, docs, and tests are left out, since
 * the user sees none of them. Scopes spelled with and without hyphens, such as `design-system`
 * and `designsystem`, share one group, and a change listed in both Mail and Meta appears once.
 */
fun changesIn(builds: List<NightlyBuild>): List<ChangeGroup> =
    builds
        .flatMap { it.notes }
        .mapNotNull { COMMIT_LINE.matchEntire(it.trim()) }
        .map { match -> Change(match.groupValues[1].ifEmpty { GENERAL }, match.groupValues[2].capitalized()) }
        .distinctBy { it.subject }
        .groupBy { it.scope.lowercase().replace("-", "") }
        .values
        .map { group ->
            // The hyphens mark the word breaks, so a hyphenated spelling names the group.
            val scope = group.map { it.scope }.firstOrNull { '-' in it } ?: group.first().scope
            ChangeGroup(scope.replace('-', ' ').capitalized(), group.map { it.subject })
        }

private class Change(
    val scope: String,
    val subject: String,
)

private fun String.capitalized() = replaceFirstChar { it.uppercaseChar() }

private const val GENERAL = "general"

/** `- feat(sidebar): show the owl (#52)` gives the scope and the subject without its pull request. */
private val COMMIT_LINE = Regex("""- (?:feat|fix|perf)(?:\(([^)]*)\))?!?: (.+?)(?: \(#\d+\))?""")
