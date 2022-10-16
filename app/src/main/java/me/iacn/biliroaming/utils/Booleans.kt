package me.iacn.biliroaming.utils

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
inline fun Boolean.yes(action: () -> Unit): Boolean {
    contract { callsInPlace(action, InvocationKind.AT_MOST_ONCE) }
    if (this) action()
    return this
}

@OptIn(ExperimentalContracts::class)
inline fun Boolean.no(action: () -> Unit): Boolean {
    contract { callsInPlace(action, InvocationKind.AT_MOST_ONCE) }
    if (!this) action()
    return this
}
