package com.otus.mail.feature.updates.domain

/** The user-facing changes to one [area] of the app, such as "Sidebar". */
data class ChangeGroup(
    val area: String,
    val changes: List<String>,
)

/**
 * The feature, fix, and speed changes in the notes of [builds], grouped by commit scope in the
 * order the scopes first appear. Commits for CI, the build, docs, and tests are left out, since
 * the user sees none of them.
 */
fun changesIn(builds: List<NightlyBuild>): List<ChangeGroup> =
    builds
        .flatMap { it.notes }
        .mapNotNull { COMMIT_LINE.matchEntire(it.trim()) }
        .groupBy(
            { match ->
                match.groupValues[1]
                    .ifEmpty { GENERAL }
                    .replace('-', ' ')
                    .capitalized()
            },
            { match -> match.groupValues[2].capitalized() },
        ).map { (area, changes) -> ChangeGroup(area, changes.distinct()) }

private fun String.capitalized() = replaceFirstChar { it.uppercaseChar() }

private const val GENERAL = "general"

/** `- feat(sidebar): show the owl (#52)` gives the scope and the subject without its pull request. */
private val COMMIT_LINE = Regex("""- (?:feat|fix|perf)(?:\(([^)]*)\))?!?: (.+?)(?: \(#\d+\))?""")
