package com.ascon.feature.library

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** A fixed moment so dates in fake data and screenshots never drift. */
internal val FixedClock: Clock = Clock.fixed(Instant.parse("2026-10-08T12:00:00Z"), ZoneOffset.UTC)
