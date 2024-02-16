package com.emberjs.hbs

import com.dmarcotte.handlebars.HbLanguage
import com.dmarcotte.handlebars.psi.HbParam
import com.emberjs.utils.EmberUtils
import com.emberjs.utils.ifTrue
import com.intellij.codeInsight.hints.HintInfo
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.codeInsight.hints.Option
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.refactoring.suggested.startOffset
import java.lang.Integer.max
import java.util.*


class HbsParameterNameHints : InlayParameterHintsProvider {

    override fun createTraversal(root: PsiElement): SyntaxTraverser<PsiElement> {
        return (root as? PsiFile)?.viewProvider?.getPsi(HbLanguage.INSTANCE)?.let { super.createTraversal(it) } ?:
        return super.createTraversal(root)
    }

    override fun getParameterHints(psiElement: PsiElement): MutableList<InlayInfo> {
        if (psiElement is HbParam) {
            var firstParam = EmberUtils.findFirstHbsParamFromParam(psiElement)
            if (firstParam == null) {
                return emptyList<InlayInfo>().toMutableList()
            }
            val positonalLen = max(
                    firstParam.parent.children.filter { it is HbParam }.size,
                    firstParam.parent.parent.children.filter { it is HbParam }.size
            )
            var index = max(
                    firstParam.parent.children.filter { it is HbParam }.indexOf(psiElement),
                    firstParam.parent.parent.children.filter { it is HbParam }.indexOf(psiElement)
            )
            if (firstParam !is HbParam) {
                index += 1
            }
            if (index <= 0) {
                // if its the helper itself
                return emptyList<InlayInfo>().toMutableList()
            }

            index -= 1

            val map = EmberUtils.getArgsAndPositionals(firstParam, positonalLen)
            val n = map.positional.getOrNull(index) ?: (index == map.positional.size).ifTrue { map.restparamnames }
            return n?.let { mutableListOf(InlayInfo(it, psiElement.startOffset)) } ?: emptyList<InlayInfo>().toMutableList()
        }
        return emptyList<InlayInfo>().toMutableList()
    }

    override fun getHintInfo(element: PsiElement): HintInfo? {
        return null
    }

    override fun getDefaultBlackList(): MutableSet<String> {
        return Collections.emptySet()
    }

    override fun getSupportedOptions(): List<Option?> {
        return Collections.emptyList()
    }

    override fun isBlackListSupported(): Boolean {
        return false
    }
}
