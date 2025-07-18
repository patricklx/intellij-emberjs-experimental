package com.emberjs.gts

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.lang.javascript.JavaScriptSupportLoader
import com.intellij.lang.javascript.folding.TypeScriptFoldingBuilder
import com.intellij.lang.xml.XmlFoldingBuilder
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType


internal class GtsFoldingBuilder : FoldingBuilderEx(), DumbAware {

    public override fun buildFoldRegions(
        root: PsiElement,
        p1: Document,
        quick: Boolean
    ): Array<out FoldingDescriptor?> {

        val registeredRanges = mutableSetOf<TextRange>()

        val descriptors = mutableListOf<FoldingDescriptor>()

        val view = root.containingFile.viewProvider
        val JS = JavaScriptSupportLoader.ECMA_SCRIPT_6
        val TS = JavaScriptSupportLoader.TYPESCRIPT
        val tsView = view.getPsi(TS)
        val jsView = view.getPsi(JS)

        val tsRegions = TypeScriptFoldingBuilder().buildFoldRegions(tsView ?: jsView, p1, quick)
        val htmlRegions = XmlFoldingBuilder().buildFoldRegions(root, p1, quick)

        val templates = PsiTreeUtil.collectElements(tsView ?: jsView) { it.elementType == GtsElementTypes.GTS_OUTER_ELEMENT_TYPE }

        registeredRanges.addAll(templates.map { it.textRange })

        for (region in tsRegions) {
            if (!registeredRanges.contains(region.range)) {
                descriptors.add(region)
            }
        }

        for (region in htmlRegions) {
            if (registeredRanges.contains(region.range)) {
                descriptors.add(region)
            }
        }

        return descriptors.toTypedArray()
    }

    public override fun getPlaceholderText(node: ASTNode): String? {
        return null
    }

    public override fun isCollapsedByDefault(p0: ASTNode): Boolean {
        return true
    }
}