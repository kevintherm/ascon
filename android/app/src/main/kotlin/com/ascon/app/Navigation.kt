package com.ascon.app

import androidx.navigation3.runtime.NavKey

/**
 * Back stack rules. Home is always at the bottom; another tab sits directly on Home,
 * so back from any tab lands on Home and back from Home leaves the app.
 */
fun MutableList<NavKey>.openTab(route: Route.TopLevel) {
    val current = lastOrNull()
    if (current == route) return
    clear()
    add(Route.Home)
    if (route != Route.Home) add(route)
}

/** Pushes a detail screen such as a series over the current tab. */
fun MutableList<NavKey>.push(route: Route) {
    if (lastOrNull() != route) add(route)
}

/** Pops one screen. Returns false when nothing is left to pop and the app should close. */
fun MutableList<NavKey>.pop(): Boolean {
    if (size <= 1) return false
    removeAt(lastIndex)
    return true
}

/** The tab to highlight: the nearest top-level route under the top of the stack. */
fun List<NavKey>.currentTab(): Tab = (lastOrNull { it is Route.TopLevel } as? Route.TopLevel)?.tab ?: Tab.Home

/** True when the top of the stack is a tab, so the floating nav is shown. */
fun List<NavKey>.showsNavBar(): Boolean = lastOrNull() is Route.TopLevel
