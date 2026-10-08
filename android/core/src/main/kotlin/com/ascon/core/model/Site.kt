package com.ascon.core.model

/** A site pinned to the home screen. */
data class Site(
    val domain: String,
    val name: String,
    /** Two letters shown in the tile, e.g. "MD". */
    val monogram: String
)
