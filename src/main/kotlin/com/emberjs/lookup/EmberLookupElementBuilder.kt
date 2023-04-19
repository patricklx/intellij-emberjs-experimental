package com.emberjs.lookup

import com.emberjs.icons.EmberIconProvider
import com.emberjs.icons.EmberIcons
import com.emberjs.resolver.EmberName
import com.emberjs.xml.CandidateKey
import com.emberjs.xml.FullPathKey
import com.emberjs.xml.InsideKey
import com.emberjs.xml.PathKey
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.javascript.JavaScriptSupportLoader
import com.intellij.lang.javascript.completion.JSImportCompletionUtil
import com.intellij.lang.javascript.modules.JSImportPlaceInfo
import com.intellij.lang.javascript.modules.imports.JSImportCandidate
import com.intellij.lang.javascript.modules.imports.providers.JSImportCandidatesProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiFile
import java.util.function.Predicate

object EmberLookupElementBuilder {

    fun getCandidate(file: PsiFile, it: EmberName): JSImportCandidate? {
        val moduleName = it.importPath
        val candidates = mutableListOf<JSImportCandidate>()
        ApplicationManager.getApplication().runReadAction {
            val tsFile = file.viewProvider.getPsi(JavaScriptSupportLoader.TYPESCRIPT) ?: return@runReadAction
            val keyFilter = Predicate { n: String? -> n == it.name }
            val info = JSImportPlaceInfo(tsFile)
            val providers = JSImportCandidatesProvider.getProviders(info)
            JSImportCompletionUtil.processExportedElements(file, providers, keyFilter) { elements: Collection<JSImportCandidate?>, name: String? ->
                candidates.addAll(elements.filterNotNull().filter { it.descriptor?.moduleName == moduleName })
            }
        }
        return candidates.firstOrNull()
    }
    fun create(it: EmberName, file: PsiFile? = null, dots: Boolean = true, useImports: Boolean = false): LookupElement {
        val name = if (useImports) it.camelCaseName else it.name
        val element = LookupElementBuilder
                .create(it.importPath, if (dots) name.replace("/", ".") else name)
                .withTypeText(it.type)
                .withTailText(" from ${it.importPath}")
                .withIcon(EmberIconProvider.getIcon(it.type) ?: EmberIcons.EMPTY_16)
                .withCaseSensitivity(true)
                .withInsertHandler(HbsInsertHandler())
        element.putUserData(PathKey, it.importPath)
        element.putUserData(FullPathKey, it.fullImportPath)
        file?.let { f -> element.putUserData(CandidateKey, getCandidate(f, it)) }
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

    fun getCandidate(file: PsiFile, name: String): JSImportCandidate? {
        val moduleName = mapping.getOrDefault(name, null)?.first() ?: return null
        val candidates = mutableListOf<JSImportCandidate>()
        ApplicationManager.getApplication().runReadAction {
            val tsFile = file.viewProvider.getPsi(JavaScriptSupportLoader.TYPESCRIPT) ?: return@runReadAction
            val keyFilter = Predicate { n: String? -> n == name }
            val info = JSImportPlaceInfo(tsFile)
            val providers = JSImportCandidatesProvider.getProviders(info)
            JSImportCompletionUtil.processExportedElements(file, providers, keyFilter) { elements: Collection<JSImportCandidate?>, name: String? ->
                candidates.addAll(elements.filterNotNull().filter { it.descriptor?.moduleName == moduleName })
            }
        }
        return candidates.firstOrNull()
    }

    fun create(file: PsiFile, name: String, useImports: Boolean): LookupElement {
        val tsFile = file.viewProvider.getPsi(JavaScriptSupportLoader.TYPESCRIPT)
        val match = mapping.getOrDefault(name, null) ?: return LookupElementBuilder.create(name)
        val candidate = tsFile?.let { getCandidate(tsFile, name) }
        if (!useImports || tsFile == null) {
            return LookupElementBuilder.create(name)
                    .withTypeText(match[1])
                    .withTailText(" from ${match[0]}")
                    .withIcon(EmberIconProvider.getIcon(match[1]) ?: EmberIcons.EMPTY_16)
        }
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
        element.putUserData(CandidateKey, candidate)
        return element
    }
}