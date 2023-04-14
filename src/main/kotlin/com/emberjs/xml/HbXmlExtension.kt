package com.emberjs.xml

import com.dmarcotte.handlebars.file.HbFileViewProvider
import com.emberjs.gts.GtsFileViewProvider
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.xml.TagNameReference
import com.intellij.psi.xml.XmlToken
import com.intellij.psi.xml.XmlTokenType.XML_NAME
import com.intellij.xml.DefaultXmlExtension


class NullTagNameReference(nameElement: ASTNode?, startTagFlag: Boolean) : TagNameReference(nameElement, startTagFlag) {
    override fun resolve(): PsiElement? {
        return null
    }
}

class HbXmlExtension: DefaultXmlExtension() {
    override fun isAvailable(file: PsiFile?): Boolean {
        return file?.viewProvider is GtsFileViewProvider || file?.viewProvider is HbFileViewProvider
    }
    override fun createTagNameReference(nameElement: ASTNode?, startTagFlag: Boolean): TagNameReference? {
        if (nameElement?.psi is XmlToken && nameElement.elementType == XML_NAME) {
            if (nameElement.text.startsWith(":") || nameElement.text.firstOrNull()?.isUpperCase() == true) {
                return NullTagNameReference(nameElement, startTagFlag)
            }
        }
        return super.createTagNameReference(nameElement, startTagFlag)
    }
}