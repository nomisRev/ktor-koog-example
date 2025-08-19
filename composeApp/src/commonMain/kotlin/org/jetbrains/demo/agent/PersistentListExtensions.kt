package org.jetbrains.demo.agent

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

/**
 * Efficiently maps a PersistentList<A> to a PersistentList<B> using a builder,
 * avoiding intermediate mutable or standard lists.
 */
inline fun <A, B> PersistentList<A>.map(transform: (A) -> B): PersistentList<B> {
    val size = this.size
    if (size == 0) return persistentListOf()
    val builder = persistentListOf<B>().builder()
    for (element in this) {
        builder.add(transform(element))
    }
    return builder.build()
}
