package com.emberjs.hbs

object Internals {
    val InternalsWithBlock = arrayListOf(
            "component",
            "each",
            "each-in",
            "if",
            "input",
            "let",
            "link-to",
            "mount",
            "outlet",
            "query-params",
            "textarea",
            "unbound",
            "unless",
            "with")

    val InternalsWithoutBlock = arrayListOf(
            "action",
            "array",
            "component",
            "concat",
            "debugger",
            "fn",
            "get",
            "hasBlock",
            "hasBlockParams",
            "hash",
            "if",
            "in-element",
            "input",
            "link-to",
            "loc",
            "log",
            "mount",
            "mut",
            "on",
            "outlet",
            "query-params",
            "textarea",
            "unbound",
            "readonly",
            "unless",
            "yield",
            "has-block-params",
            "has-block",
            )

    val mapping = mapOf(
            "fn" to listOf("@ember/helper", "helper"),
            "array" to listOf("@ember/helper", "helper"),
            "concat" to listOf("@ember/helper", "helper"),
            "get" to listOf("@ember/helper", "helper"),
            "hash" to listOf("@ember/helper", "helper"),
            "on" to listOf("@ember/modifier", "modifier"),
            "Input" to listOf("@ember/component", "component"),
            "TextArea" to listOf("@ember/component", "component"),
            "LinkTo" to listOf("@ember/routing", "component"),
    )
}
