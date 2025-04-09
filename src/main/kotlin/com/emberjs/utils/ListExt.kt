package com.emberjs.utils


fun <T> List<T>.slice(start: Int) = this.subList(start, this.size)
fun <T> List<T>.slice(start: Int, end: Int) = this.subList(start, (end < 0).ifTrue { size + end } ?: end)

