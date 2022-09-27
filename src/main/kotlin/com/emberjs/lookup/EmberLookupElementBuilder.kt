package com.emberjs.lookup

import com.emberjs.FullPathKey
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
