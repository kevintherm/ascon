package com.ascon.app

import androidx.navigation3.runtime.NavKey
import com.ascon.core.model.ReadingStatus
import kotlinx.serialization.Serializable

/** Destinations. Top-level ones sit under the floating nav; the rest push over it. */
sealed interface Route : NavKey {
    /** A tab in the floating nav. */
    sealed interface TopLevel : Route

    @Serializable
    data object Home : TopLevel

    @Serializable
    data class Library(val status: ReadingStatus = ReadingStatus.Reading) : TopLevel

    @Serializable
    data object Browse : TopLevel

    @Serializable
    data object Settings : TopLevel

    @Serializable
    data class Series(val id: String) : Route
}

/** The floating nav's tabs, in order. */
enum class Tab { Home, Library, Browse, Settings }

val Route.TopLevel.tab: Tab
    get() = when (this) {
        Route.Home -> Tab.Home
        is Route.Library -> Tab.Library
        Route.Browse -> Tab.Browse
        Route.Settings -> Tab.Settings
    }

fun Tab.route(): Route.TopLevel = when (this) {
    Tab.Home -> Route.Home
    Tab.Library -> Route.Library()
    Tab.Browse -> Route.Browse
    Tab.Settings -> Route.Settings
}
