package com.example.ui.guide

import com.example.model.TabloAiring

/** Pure guide focus rules, kept separate so edge cases remain unit-testable. */
internal fun guideAnchorFor(airing: TabloAiring, windowStart: Long): Long =
    maxOf(airing.startTimeMillis, windowStart)

internal fun airingAtAnchor(airings: List<TabloAiring>, anchorMs: Long): TabloAiring? =
    airings.firstOrNull { it.startTimeMillis <= anchorMs && anchorMs < it.endTimeMillis }
        ?: airings.minByOrNull { kotlin.math.abs(it.startTimeMillis - anchorMs) }
