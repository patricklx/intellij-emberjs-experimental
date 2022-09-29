package com.emberjs.lookup

import com.emberjs.FullPathKey
import com.emberjs.InsideKey
import com.emberjs.PathKey
import com.emberjs.icons.EmberIconProvider
import com.emberjs.icons.EmberIcons
import com.emberjs.resolver.EmberName
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder

object EmberLookupElementBuilder {
    fun create(it: EmberName, dots: Boolean = true, useImports: Boolean = false): LookupElement {
        val name = if (useImports) it.camelCaseName else it.name
        val element = LookupElementBuilder
                .create(it, if (dots) name.replace("/", ".") else name)
                .withTypeText(it.type)
                .withTailText(" from ${it.importPath}")
                .withIcon(EmberIconProvider.getIcon(it.type) ?: EmberIcons.EMPTY_16)
                .withCaseSensitivity(true)
                .withInsertHandler(HbsInsertHandler())
        element.putUserData(PathKey, it.importPath)
        element.putUserData(FullPathKey, it.fullImportPath)
        return element
    }
}

object EmberLookupInternalElementBuilder {
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
    fun create(name: String, useImports: Boolean): LookupElement {
        if (!useImports) {
            return LookupElementBuilder.create(name)
        }
        val match = mapping.getOrDefault(name, null) ?: return LookupElementBuilder.create(name)
        val element = LookupElementBuilder
                .create(name)
                .withTypeText(match[1])
                .withTailText(" from ${match[0]}")
                .withIcon(EmberIconProvider.getIcon(match[1]) ?: EmberIcons.EMPTY_16)
                .withCaseSensitivity(true)
                .withInsertHandler(HbsInsertHandler())
        element.putUserData(PathKey, match[0])
        element.putUserData(FullPathKey, match[0])
        element.putUserData(InsideKey, "true")
        return element
    }
}