package com.emberjs.gts

import com.dmarcotte.handlebars.HbLanguage
import com.dmarcotte.handlebars.file.HbFileViewProvider
import com.dmarcotte.handlebars.parsing.HbTokenTypes
import com.dmarcotte.handlebars.psi.HbData
import com.dmarcotte.handlebars.psi.HbPsiElement
import com.emberjs.glint.GlintAnnotationError
import com.emberjs.glint.GlintTypeScriptService
import com.emberjs.utils.ifTrue
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.ecmascript6.actions.ES6AddImportExecutor
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.javascript.JavaScriptBundle
import com.intellij.lang.javascript.JavaScriptSupportLoader
import com.intellij.lang.javascript.completion.JSImportCompletionUtil
import com.intellij.lang.javascript.modules.JSImportModuleFix
import com.intellij.lang.javascript.modules.JSImportPlaceInfo
import com.intellij.lang.javascript.modules.imports.JSImportCandidate
import com.intellij.lang.javascript.modules.imports.JSImportCandidateWithExecutor
import com.intellij.lang.javascript.modules.imports.providers.JSImportCandidatesProvider
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlTokenType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.util.containers.ContainerUtil
import java.util.function.Predicate

class InitialInfo {
    var file: PsiFile? = null
    val project get() = file?.project
    val map: MutableMap<String, MutableList<JSImportCandidate>> = mutableMapOf()
    val emberTags: MutableList<XmlTag> = mutableListOf()
    val hbIds: MutableList<HbPsiElement> = mutableListOf()
}

class AnnotationResult {
    var annotationErrors: MutableList<GlintAnnotationError> = mutableListOf()
    var initialInfo: InitialInfo? = null
}

class HbLintExternalAnnotator() : ExternalAnnotator<InitialInfo, AnnotationResult>() {

    override fun collectInformation(file: PsiFile): InitialInfo? {
        if (file.viewProvider !is GtsFileViewProvider && file.viewProvider !is HbFileViewProvider) {
            return null
        }
        val initialInfo = InitialInfo()
        initialInfo.file = file

        val html = file.viewProvider.getPsi(HTMLLanguage.INSTANCE)
        val hbs = file.viewProvider.getPsi(HbLanguage.INSTANCE)
        val tsFile = file.viewProvider.getPsi(JavaScriptSupportLoader.TYPESCRIPT)
        val emberTags = PsiTreeUtil.collectElements(html) {
            it is XmlTag && it.reference?.resolve() == null
        }.map { it as XmlTag }
        val hbIds = PsiTreeUtil.collectElements(hbs) {
            it is HbPsiElement && it.elementType == HbTokenTypes.ID && it.reference?.resolve() == null && it.parent !is HbData
        }.map { it as HbPsiElement }
        val map = emberTags.groupBy { it.name }.mapValues { mutableListOf<JSImportCandidate>() }.toMutableMap()

        if (tsFile != null) {
            val info = JSImportPlaceInfo(tsFile)
            val keyFilter = Predicate { name: String? -> name != null && (emberTags.find { name == it.name } != null || hbIds.find { name == it.text } != null )}
            val providers = JSImportCandidatesProvider.getProviders(info)
            hbIds.groupBy { it.name }.mapValuesTo(map) { mutableListOf() }
            JSImportCompletionUtil.processExportedElements(file, providers, keyFilter) { elements: Collection<JSImportCandidate?>, name: String? ->
                val candidate = if (elements.size == 1) ContainerUtil.getFirstItem(elements) else null
                if (candidate == null) {
                    return@processExportedElements true
                }
                map.entries.filter { candidate.name.startsWith(it.key) }.forEach {
                    it.value.add(candidate)
                }
                return@processExportedElements true
            }
        }

        hbIds.toCollection(initialInfo.hbIds)
        emberTags.toCollection(initialInfo.emberTags)
        map.toMap(initialInfo.map)
        return initialInfo
    }

    override fun doAnnotate(collectedInfo: InitialInfo): AnnotationResult? {
        if (collectedInfo.file == null) {
            return null
        }
        val result = AnnotationResult()
        if (collectedInfo.file?.viewProvider is HbFileViewProvider) {
            collectedInfo.project?.getService(GlintTypeScriptService::class.java)
                    ?.highlight(collectedInfo.file!!)
                    ?.get()
                    ?.map { it as GlintAnnotationError }
                    ?.toCollection(result.annotationErrors)
        }

        result.initialInfo = collectedInfo
        return result
    }

    override fun apply(file: PsiFile, annotationResult: AnnotationResult?, holder: AnnotationHolder) {
        val documentManager = PsiDocumentManager.getInstance(file.project)
        val document = documentManager.getDocument(file)!!
        val tsFile = file.viewProvider.getPsi(JavaScriptSupportLoader.TYPESCRIPT)
        annotationResult?.let { result ->
            result.initialInfo!!.emberTags.forEach {
                val nameElement = it.children.find { it.elementType == XmlTokenType.XML_NAME } ?: return@forEach
                val closeNameElement = it.children.findLast { it.elementType == XmlTokenType.XML_NAME }
                val message = (((it.name.startsWith(":") || file.viewProvider is HbFileViewProvider)
                        .ifTrue { JavaScriptBundle.message("javascript.unresolved.symbol.message", Object()) + " '<${it.name}>'" }
                        ?: (JavaScriptBundle.message("js.inspection.missing.import", Object()) + " for <${it.name}>")))
                if (closeNameElement != null && closeNameElement.textRange.endOffset == it.endOffset - 1) {
                    holder.newSilentAnnotation(HighlightSeverity.ERROR)
                            .range(closeNameElement.textRange)
                            .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
                            .tooltip(message)
                            .create()
                }
                val candidates = result.initialInfo!!.map.get(it.name) ?: emptyList()
                val annotation = holder.newAnnotation(HighlightSeverity.ERROR, message)
                        .range(nameElement.textRange)
                        .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
                        .tooltip(message)
                candidates.forEach { c ->
                    val icwe = JSImportCandidateWithExecutor(c, ES6AddImportExecutor(tsFile))
                    val fix = JSImportModuleFix(it, icwe, null, true)
                    annotation.withFix(fix)
                }
                annotation.create()
            }
            result.annotationErrors.forEach {
                val startLineNumber = it.line
                val endLineNumber = it.endLine
                val rangeStart = it.column
                val rangeEnd = it.endColumn
                val startOffset: Int = document.getLineStartOffset(startLineNumber) + rangeStart
                val endLineLength: Int = document.getLineEndOffset(endLineNumber) - document.getLineStartOffset(endLineNumber)
                val endOffset: Int = document.getLineStartOffset(endLineNumber) + Math.min(rangeEnd, endLineLength)
                val annotation = holder.newAnnotation(it.severity, it.description).range(TextRange(startOffset, endOffset))
                if (it.tooltipText != null) {
                    annotation.tooltip(it.tooltipText!!)
                } else {
                    annotation.tooltip(it.description)
                }
                annotation.create()
            }

        }
    }


}