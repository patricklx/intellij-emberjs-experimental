package com.emberjs.hbs

import com.dmarcotte.handlebars.file.HbFileType
import com.dmarcotte.handlebars.parsing.HbLexer
import com.dmarcotte.handlebars.parsing.HbTokenTypes
import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.lang.javascript.JavaScriptFileType
import com.intellij.lang.javascript.TypeScriptFileType
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.elementType

class HbsFindUsagesProvider: FindUsagesProvider {

    override fun getWordsScanner(): WordsScanner {
        val ID = TokenSet.create(HbTokenTypes.ID, HbTokenTypes.PARAM);
        return DefaultWordsScanner(HbLexer(), ID, HbTokenTypes.COMMENTS, HbTokenTypes.STRING_LITERALS)
    }

    override fun canFindUsagesFor(psiElement: PsiElement): Boolean {
        val type = psiElement.containingFile.fileType
        val styleSheetLanguages = arrayOf("sass", "scss", "less")
        if (styleSheetLanguages.contains(psiElement.containingFile.language.id.lowercase())) {
            return true
        }
        return type is JavaScriptFileType || type is TypeScriptFileType || type is HbFileType
    }

    override fun getHelpId(psiElement: PsiElement): String? {
        return null
    }

    override fun getType(element: PsiElement): String {
        return element.elementType.toString()
    }

    override fun getDescriptiveName(element: PsiElement): String {
        return element.text
    }

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String {
        return element.text
    }
}