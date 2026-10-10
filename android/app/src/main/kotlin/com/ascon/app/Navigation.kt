package com.ascon.app

import androidx.navigation3.runtime.NavKey

/** Pushes a detail screen such as a series over the tabs. */
fun MutableList<NavKey>.push(route: Route) {
    if (lastOrNull() != route) add(route)
}

/** Pops one detail screen. Returns false when only the tabs are left. */
fun MutableList<NavKey>.pop(): Boolean {
    if (size <= 1) return false
    removeAt(lastIndex)
    return true
}

/**
 * Pops back to the browser screen when one is open under the top, and returns true.
 * The browser has one live page, so it must never be on the stack twice.
 */
fun MutableList<NavKey>.popToBrowser(): Boolean {
    val at = indexOfLast { it is Route.Browser }
    if (at < 0) return false
    while (size > at + 1) removeAt(lastIndex)
    return true
}

/** True when no detail screen covers the tabs, so the floating nav is shown. */
fun List<NavKey>.showsTabs(): Boolean = size <= 1

/**
 * Where back goes from a tab when no detail screen is open. Every tab sits on Home, so
 * back from another tab lands on Home and back from Home leaves the app, shown as null.
 */
fun Tab.back(): Tab? = if (this == Tab.Home) null else Tab.Home
