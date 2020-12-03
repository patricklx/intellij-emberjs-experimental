package com.emberjs.refactoring

import com.dmarcotte.handlebars.parsing.HbTokenTypes
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType

object SimpleNodeFactory {
    fun createNode(project: Project?, name: String): PsiElement {
        val file: PsiFile = createFile(project, name)
        return PsiTreeUtil.collectElements(file, { it.elementType == HbTokenTypes.ID && it !is LeafPsiElement }).first()
    }

    fun createFile(project: Project?, text: String): PsiFile {
        val name = "dummy.hbs"
        val hbs = "{{$text}}"
        return PsiFileFactory.getInstance(project).createFileFromText(name, Language.findLanguageByID("Handlebars")!!, hbs)
    }
}
