package com.emberjs.glint

import com.dmarcotte.handlebars.psi.HbPsiFile
import com.emberjs.utils.ifTrue
import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.lang.typescript.compiler.languageService.codeFixes.TypeScriptServiceRelatedAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilCore


class GlintHBSupressErrorFix(val type: String) : BaseIntentionAction(), LowPriorityAction, TypeScriptServiceRelatedAction {
    override fun getFamilyName(): String {
        return "supress with @glint-$type"
    }

    override fun getText(): String {
        return familyName
    }

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        return file is HbPsiFile
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val offset = editor.caretModel.offset
        val element = PsiUtilCore.getElementAtOffset(file, offset)
        if (element !is PsiFile) {
            val ignore = (type == "ignore").ifTrue { "@glint-ignore" } ?: (type == "expect").ifTrue { "@glint-expect-error" } ?: (type == "no-check").ifTrue { "@glint-nocheck" }
            val comment = "{{! $ignore }}"
            val document = editor.document
            val sep = file.virtualFile?.detectedLineSeparator ?: StringUtil.detectSeparators(document.text) ?: CodeStyle.getProjectOrDefaultSettings(project).lineSeparator
            if (type == "no-check") {
                document.setText(document.text.replaceRange(0, 0, "$comment$sep"))
                return
            }
            val line = document.getLineNumber(offset)
            val startOffset = document.getLineStartOffset(line)
            val textRange = TextRange(startOffset, document.getLineEndOffset(line))
            val lineText = document.getText(textRange)
            val whitespace = " ".repeat(lineText.indexOfFirst { it != ' ' })
            document.setText(document.text.replaceRange(startOffset, startOffset, "$whitespace$comment$sep"))
        }
    }

    override fun getIndex(): Int {
        return Int.MAX_VALUE
    }
}
