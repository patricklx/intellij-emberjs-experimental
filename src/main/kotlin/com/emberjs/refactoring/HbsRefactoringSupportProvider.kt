package com.emberjs.refactoring

import com.dmarcotte.handlebars.parsing.HbTokenTypes
import com.emberjs.psi.EmberNamedElement
import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.elementType

class HbsRefactoringSupportProvider : RefactoringSupportProvider() {
    override fun isSafeDeleteAvailable(element: PsiElement): Boolean {
        return false;
    }

    override fun isAvailable(context: PsiElement): Boolean {
        if (context is EmberNamedElement) {
            return true
        }
        return false
    }

    override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean {
        return true;
    }

    override fun isInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean {
        return true;
    }

    override fun isInplaceIntroduceAvailable(element: PsiElement, context: PsiElement?): Boolean {
        return true
    }
}
