package com.emberjs.gts

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.lang.Language
import com.intellij.lang.ecmascript6.psi.ES6ImportDeclaration
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.javascript.psi.ecma6.ES6TaggedTemplateExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.suggested.startOffset


class EmberInspectionSuppressor : InspectionSuppressor {

    override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
        if (toolId.lowercase().contains("unused") && element is ES6ImportDeclaration) {
            val f = element.containingFile
            val manager = InjectedLanguageManager.getInstance(element.project)
            val templates = PsiTreeUtil.collectElements(f) { it is ES6TaggedTemplateExpression && it.tag?.text == "hbs" }.mapNotNull { (it as ES6TaggedTemplateExpression).templateExpression }
            var tpl = templates.mapNotNull {
                val injected = manager.findInjectedElementAt(f, it.startOffset + 1)?.containingFile
                        ?: return@mapNotNull null
                val hbs = injected.viewProvider.getPsi(Language.findLanguageByID("Handlebars")!!)
                val html = injected.viewProvider.getPsi(Language.findLanguageByID("HTML")!!)
                return@mapNotNull arrayOf<PsiFile>(hbs, html)
            }.toTypedArray().flatten()
            if (element.containingFile.fileType == GtsFileType.INSTANCE) {
                val hbs = element.containingFile.viewProvider.getPsi(Language.findLanguageByID("Handlebars")!!)
                val html = element.containingFile.viewProvider.getPsi(Language.findLanguageByID("HTML")!!)
                tpl = arrayOf<PsiFile>(hbs, html).toList()
            }


            return element.importedBindings.any { ib ->
                tpl.any {
                    PsiTreeUtil.collectElements(it) { el -> el.reference?.isReferenceTo(ib) == true || el.references.find { it.isReferenceTo(ib) } != null }.filterNotNull().isNotEmpty()
                }
            } ||
                    element.importSpecifiers.any { isp ->
                        tpl.any {
                            PsiTreeUtil.collectElements(it) { el -> el.reference?.isReferenceTo(isp) == true || el.references.find { it.isReferenceTo(isp) } != null }.filterNotNull().isNotEmpty()
                        } || let {
                            if (isp.alias == null) {
                                return@any false
                            }
                            tpl.any {
                                PsiTreeUtil.collectElements(it) { el -> el.reference?.isReferenceTo(isp.alias!!) == true || el.references.find { it.isReferenceTo(isp.alias!!) } != null }.filterNotNull().isNotEmpty()
                            }
                        }

                    }
        }
        return false
    }

    override fun getSuppressActions(element: PsiElement?, toolId: String): Array<SuppressQuickFix> {
        return emptyArray()
    }


}