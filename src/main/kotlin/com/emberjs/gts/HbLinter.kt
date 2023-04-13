package com.emberjs.gts

import com.dmarcotte.handlebars.file.HbFileViewProvider
import com.dmarcotte.handlebars.parsing.HbTokenTypes
import com.dmarcotte.handlebars.psi.HbPsiElement
import com.dmarcotte.handlebars.psi.HbPsiFile
import com.emberjs.glint.GlintAnnotationError
import com.emberjs.glint.GlintTypeScriptService
import com.emberjs.hbs.HbReference
import com.emberjs.utils.ifTrue
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.Language
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.ecmascript6.actions.ES6AddImportExecutor
import com.intellij.lang.javascript.JavaScriptBundle
import com.intellij.lang.javascript.JavaScriptSupportLoader
import com.intellij.lang.javascript.completion.JSImportCompletionUtil
import com.intellij.lang.javascript.modules.JSImportModuleFix
import com.intellij.lang.javascript.modules.JSImportPlaceInfo
import com.intellij.lang.javascript.modules.JSPlaceTail
import com.intellij.lang.javascript.modules.imports.JSImportCandidate
import com.intellij.lang.javascript.modules.imports.JSImportCandidateWithExecutor
import com.intellij.lang.javascript.modules.imports.providers.JSImportCandidatesProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlTokenType
import com.intellij.refactoring.suggested.endOffset
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


class FakeJsElement(val element: PsiElement): PsiElement by element {
    override fun getLanguage(): Language {
        return JavaScriptSupportLoader.TYPESCRIPT
    }

    override fun getContainingFile(): PsiFile {
        return element.containingFile.viewProvider.getPsi(JavaScriptSupportLoader.TYPESCRIPT)
    }
}

class GtsImportFix(node: PsiElement, descriptor: JSImportCandidateWithExecutor, tail: JSPlaceTail?, needHint: Boolean) : JSImportModuleFix(FakeJsElement(node), descriptor, tail, needHint) {

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        super.invoke(project, file, startElement, endElement)
        if (startElement is XmlTag) {
            startElement.name = startElement.name.split("::").last()
        }
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        super.invoke(project, editor, file)
        val startElement = this.startElement
        if (startElement is XmlTag) {
            startElement.name = startElement.name.split("::").last()
        }
    }
}

class HbLintExternalAnnotator() : ExternalAnnotator<InitialInfo, AnnotationResult>() {
    override fun collectInformation(file: PsiFile): InitialInfo? {
        if (file is HbPsiFile && file.viewProvider is GtsFileViewProvider) {
            return null
        }
        if (file.viewProvider !is GtsFileViewProvider && file.viewProvider !is HbFileViewProvider) {
            return null
        }
        val initialInfo = InitialInfo()
        initialInfo.file = file
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

    override fun apply(file: PsiFile, result: AnnotationResult?, holder: AnnotationHolder) {
        val documentManager = PsiDocumentManager.getInstance(file.project)
        val document = documentManager.getDocument(file)!!
        result?.annotationErrors?.forEach {
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

class HbLintAnnotator() : Annotator {

    fun getCandidates(file: PsiFile, name: String): MutableList<JSImportCandidate> {
        val candidates = mutableListOf<JSImportCandidate>()
        ApplicationManager.getApplication().runReadAction {
            val tsFile = file.viewProvider.getPsi(JavaScriptSupportLoader.TYPESCRIPT) ?: return@runReadAction
            val keyFilter = Predicate { n: String? -> n == name }
            val info = JSImportPlaceInfo(tsFile)
            val providers = JSImportCandidatesProvider.getProviders(info)
            JSImportCompletionUtil.processExportedElements(file, providers, keyFilter) { elements: Collection<JSImportCandidate?>, name: String? ->
                candidates.addAll(elements.filterNotNull())
            }
        }
        return candidates
    }

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val file = element.containingFile
        if (file.viewProvider !is GtsFileViewProvider && file.viewProvider !is HbFileViewProvider) {
            return
        }
        val tsFile = file.viewProvider.getPsi(JavaScriptSupportLoader.TYPESCRIPT)
        if (tsFile != null) {
            if (element is XmlTag && element.reference?.resolve() == null) {
                val candidates = getCandidates(file, element.name)
                val nameElement = element.children.find { it.elementType == XmlTokenType.XML_NAME } ?: return
                val closeNameElement = element.children.findLast { it.elementType == XmlTokenType.XML_NAME }
                val message = (((element.name.startsWith(":") || file.viewProvider is HbFileViewProvider)
                        .ifTrue { JavaScriptBundle.message("javascript.unresolved.symbol.message", Object()) + " '${element.name}'" }
                        ?: (JavaScriptBundle.message("js.inspection.missing.import", Object()) + " for '${element.name}'")))
                if (closeNameElement != null && closeNameElement.textRange.endOffset == element.endOffset - 1) {
                    holder.newSilentAnnotation(HighlightSeverity.ERROR)
                            .range(closeNameElement.textRange)
                            .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
                            .tooltip(message)
                            .create()
                }
                val annotation = holder.newAnnotation(HighlightSeverity.ERROR, message)
                        .range(nameElement.textRange)
                        .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
                        .tooltip(message)
                candidates.forEach { c ->
                    val icwe = JSImportCandidateWithExecutor(c, ES6AddImportExecutor(tsFile))
                    val fix = GtsImportFix(element, icwe, null, true)
                    annotation.withFix(fix)
                }
                annotation.needsUpdateOnTyping()
                annotation.create()
            }
            if (element is HbPsiElement && element.elementType == HbTokenTypes.ID && (element.reference?.resolve() == null && element.references.find { it is HbReference }?.resolve() == null)) {
                val name = element.text
                val message = JavaScriptBundle.message("javascript.unresolved.symbol.message", Object()) + " '${name}'"
                val candidates = getCandidates(element.containingFile, name)
                val annotation = holder.newAnnotation(HighlightSeverity.ERROR, message)
                        .range(element.textRange)
                        .highlightType(ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
                        .tooltip(message)
                candidates.forEach { c ->
                    val icwe = JSImportCandidateWithExecutor(c, ES6AddImportExecutor(tsFile))
                    val fix = GtsImportFix(element, icwe, null, true)
                    annotation.withFix(fix)
                }
                annotation.needsUpdateOnTyping()
                annotation.create()
            }
        }
    }
}