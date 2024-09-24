package com.emberjs.utils
// <T, R> T.let(block: (T) -> R): R
inline fun <R> Boolean?.ifTrue(block: Boolean.() -> R): R? {
    if (this == true) {
        return block()
    }
    return null
}

inline fun <R> Boolean.ifElse(block1: Boolean?.() -> R, block2: Boolean.() -> R): R {
    if (this) {
        return block1()
    }
    return block2()
}

inline fun  <R> Boolean?.ifFalse(block: Boolean?.() -> R): R? {
    if (null == this || !this) {
        return block()
    }

    return null
}
