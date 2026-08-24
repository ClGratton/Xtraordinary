package com.xteink.companion.ui

/**
 * Compares firmware versions without treating a merely different build as an
 * update. Xtraordinary versions use semver-like ordering with numbered dev
 * builds below the corresponding stable release.
 */
internal enum class FirmwareVersionRelation {
    CatalogNewer,
    Equal,
    CurrentAhead,
    Unknown,
}

internal fun firmwareCheckPhaseFor(
    currentVersion: String?,
    releaseVersion: String,
): FirmwareCheckPhase = when (firmwareVersionRelation(currentVersion, releaseVersion)) {
    FirmwareVersionRelation.CatalogNewer,
    FirmwareVersionRelation.Unknown -> FirmwareCheckPhase.Available
    FirmwareVersionRelation.Equal,
    FirmwareVersionRelation.CurrentAhead -> FirmwareCheckPhase.UpToDate
}

internal fun firmwareVersionRelation(currentVersion: String?, releaseVersion: String): FirmwareVersionRelation {
    val current = currentVersion?.trim().orEmpty()
    val release = releaseVersion.trim()
    if (current.isEmpty() || release.isEmpty()) return FirmwareVersionRelation.Unknown

    val currentXtraordinary = parseXtraordinaryVersion(current)
    val releaseXtraordinary = parseXtraordinaryVersion(release)
    if (currentXtraordinary != null && releaseXtraordinary != null) {
        return when {
            releaseXtraordinary > currentXtraordinary -> FirmwareVersionRelation.CatalogNewer
            releaseXtraordinary < currentXtraordinary -> FirmwareVersionRelation.CurrentAhead
            else -> FirmwareVersionRelation.Equal
        }
    }
    if ((currentXtraordinary == null) != (releaseXtraordinary == null)) {
        // A source-family change is an install candidate, not a numeric
        // comparison across unrelated namespaces (for example XT V5.1.6 vs
        // xtraordinary-v0.2.6).
        return FirmwareVersionRelation.CatalogNewer
    }

    // Non-Xtraordinary releases can still expose numeric tags, but only compare
    // those tags when both sides are in the same opaque namespace.
    val currentNumbers = numericVersion(current)
    val releaseNumbers = numericVersion(release)
    return if (currentNumbers != null && releaseNumbers != null) {
        when (compareNumericVersions(releaseNumbers, currentNumbers)) {
            1 -> FirmwareVersionRelation.CatalogNewer
            0 -> FirmwareVersionRelation.Equal
            else -> FirmwareVersionRelation.CurrentAhead
        }
    } else {
        FirmwareVersionRelation.Unknown
    }
}

internal fun isFirmwareReleaseNewer(currentVersion: String?, releaseVersion: String): Boolean =
    firmwareVersionRelation(currentVersion, releaseVersion) == FirmwareVersionRelation.CatalogNewer

private data class XtraordinaryVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: Prerelease,
) : Comparable<XtraordinaryVersion> {
    override fun compareTo(other: XtraordinaryVersion): Int {
        val majorComparison = major.compareTo(other.major)
        if (majorComparison != 0) return majorComparison
        val minorComparison = minor.compareTo(other.minor)
        if (minorComparison != 0) return minorComparison
        val patchComparison = patch.compareTo(other.patch)
        return if (patchComparison != 0) patchComparison else prerelease.compareTo(other.prerelease)
    }
}

private sealed class Prerelease : Comparable<Prerelease> {
    data object Stable : Prerelease()
    data class Dev(val number: Int) : Prerelease()
    data class Other(val value: String) : Prerelease()

    override fun compareTo(other: Prerelease): Int = when {
        this is Stable && other !is Stable -> 1
        this !is Stable && other is Stable -> -1
        this is Dev && other is Dev -> number.compareTo(other.number)
        this is Other && other is Other -> value.compareTo(other.value, ignoreCase = true)
        this is Dev && other is Other -> -1
        this is Other && other is Dev -> 1
        else -> 0
    }
}

private val XtraordinaryVersionPattern = Regex(
    "^xtraordinary-v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([a-zA-Z]+)(\\d+)?(?:[-+].*)?)?$",
    RegexOption.IGNORE_CASE,
)

private fun parseXtraordinaryVersion(value: String): XtraordinaryVersion? {
    val match = XtraordinaryVersionPattern.matchEntire(value.trim()) ?: return null
    val (major, minor, patch, qualifier, qualifierNumber) = match.destructured
    val prerelease = when {
        qualifier.isBlank() -> Prerelease.Stable
        qualifier.equals("dev", ignoreCase = true) -> Prerelease.Dev(qualifierNumber.toIntOrNull() ?: 0)
        else -> Prerelease.Other(qualifier)
    }
    return XtraordinaryVersion(major.toInt(), minor.toInt(), patch.toInt(), prerelease)
}

private fun numericVersion(value: String): List<Int>? {
    val numbers = Regex("\\d+").findAll(value).map { it.value.toInt() }.toList()
    return numbers.takeIf { it.isNotEmpty() }
}

private fun compareNumericVersions(left: List<Int>, right: List<Int>): Int {
    val size = maxOf(left.size, right.size)
    for (index in 0 until size) {
        val comparison = (left.getOrNull(index) ?: 0).compareTo(right.getOrNull(index) ?: 0)
        if (comparison != 0) return comparison
    }
    return 0
}
