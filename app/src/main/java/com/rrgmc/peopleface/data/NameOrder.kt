package com.rrgmc.peopleface.data

import java.text.Collator

/**
 * Sorts by a name the way people expect in the phone's language: ignoring case and accents first
 * ("Ângela" goes next to "Andreia", not after "Wilson"), with accents only breaking ties. Stable, so
 * equal names keep their order. SQLite's NOCASE only knows A–Z, which is why this is done here.
 */
fun <T> Iterable<T>.sortedByName(name: (T) -> String): List<T> {
    val collator = Collator.getInstance().apply { strength = Collator.SECONDARY } // not thread-safe: one per call
    return sortedWith { a, b -> collator.compare(name(a), name(b)) }
}
