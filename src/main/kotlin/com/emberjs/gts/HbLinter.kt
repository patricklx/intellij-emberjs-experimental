package com.emberjs.gts

import com.emberjs.glint.GlintAnnotationError
import com.emberjs.glint.GlintTypeScriptService
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

class InitialInfo {
    var file: PsiFile? = null
    val project get() = file?.project
}

class HbLintExternalAnnotator() : ExternalAnnotator<InitialInfo, List<GlintAnnotationError>>() {

    override fun collectInformation(file: PsiFile): InitialInfo? {
        val initialInfo = InitialInfo()
        initialInfo.file = file
        return initialInfo
    }

    override fun doAnnotate(collectedInfo: InitialInfo): List<GlintAnnotationError>? {
        if (collectedInfo.file == null) {
            return null
        }
        return collectedInfo.project?.getService(GlintTypeScriptService::class.java)?.highlight(collectedInfo.file!!)?.get()?.map { it as GlintAnnotationError }
    }

    override fun apply(file: PsiFile, annotationResult: List<GlintAnnotationError>?, holder: AnnotationHolder) {
        annotationResult?.let {
            val documentManager = PsiDocumentManager.getInstance(file.project)
            val document = documentManager.getDocument(file)!!
            annotationResult.forEach {
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