package com.emberjs.gts

import com.dmarcotte.handlebars.psi.HbPsiFile
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.structureView.impl.TemplateLanguageStructureViewBuilder
import com.intellij.lang.Language
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.lang.html.structureView.HtmlStructureViewBuilderProvider
import com.intellij.lang.javascript.TypeScriptFileType
import com.intellij.lang.typescript.structureView.TypeScriptStructureViewBuilderFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile


class GtsStructureViewFactory : PsiStructureViewFactory {

    fun getModel(psiFile: PsiFile, editor: Editor?): StructureViewModel? {
        if (psiFile is GtsFile) {
            return (TypeScriptStructureViewBuilderFactory().getStructureViewBuilder(psiFile) as TreeBasedStructureViewBuilder).createStructureViewModel(editor)
        }
        if (psiFile.fileType === TypeScriptFileType) {
            return (TypeScriptStructureViewBuilderFactory().getStructureViewBuilder(psiFile) as TreeBasedStructureViewBuilder).createStructureViewModel(editor)
        }
        if (psiFile is XmlFile) {
            return (HtmlStructureViewBuilderProvider().createStructureViewBuilder(psiFile) as TreeBasedStructureViewBuilder).createStructureViewModel(editor)
        }
        if (psiFile is HbPsiFile) {
            return (TypeScriptStructureViewBuilderFactory().getStructureViewBuilder(psiFile) as TreeBasedStructureViewBuilder).createStructureViewModel(editor)
        }
        return null
    }

    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
        return object : TemplateLanguageStructureViewBuilder(psiFile) {
            val modelFactory = this@GtsStructureViewFactory::getModel

            override fun isAcceptableBaseLanguageFile(dataFile: PsiFile?): Boolean {
                return dataFile !is HbPsiFile
            }

            override fun createMainBuilder(psi: PsiFile): TreeBasedStructureViewBuilder? {
                return object : TreeBasedStructureViewBuilder() {
                    override fun isRootNodeShown(): Boolean {
                        return false
                    }

                    override fun createStructureViewModel(editor: Editor?): StructureViewModel {
                        return modelFactory(psi, editor) as StructureViewModel
                    }
                }
            }
        }
    }
}
