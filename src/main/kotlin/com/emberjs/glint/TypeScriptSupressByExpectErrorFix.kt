package com.emberjs.glint

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.lang.javascript.JavaScriptBundle
import com.intellij.lang.javascript.inspections.JSSuppressByCommentFix
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.lang.javascript.psi.JSSuppressionHolder
import com.intellij.lang.typescript.compiler.languageService.codeFixes.TypeScriptServiceRelatedAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore


class TypeScriptSupressByExpectErrorFix(private val myHolderClass: Class<out JSSuppressionHolder?>) : BaseIntentionAction(), LowPriorityAction, TypeScriptServiceRelatedAction {
    override fun getFamilyName(): String {
        return JavaScriptBundle.message("intention.family.name.suppress.with.ts.ignore", *arrayOfNulls(0))
    }

    override fun getText(): String {
        return familyName
    }

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        return file is JSFile
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val offset = editor.caretModel.offset
        val element = PsiUtilCore.getElementAtOffset(file, offset)
        if (element !is PsiFile) {
            val container: PsiElement? = PsiTreeUtil.getParentOfType(element, myHolderClass)
            if (container != null) {
                JSSuppressByCommentFix.suppressByComment(project, element, container, "@ts-expect-error")
            }
        }
    }

    override fun getIndex(): Int {
        return Int.MAX_VALUE
    }
}
