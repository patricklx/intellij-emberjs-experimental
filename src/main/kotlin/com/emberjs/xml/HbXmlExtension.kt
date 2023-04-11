package com.emberjs.xml

import com.dmarcotte.handlebars.file.HbFileViewProvider
import com.emberjs.gts.GtsFileViewProvider
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.xml.SchemaPrefix
import com.intellij.psi.impl.source.xml.TagNameReference
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlToken
import com.intellij.psi.xml.XmlTokenType.XML_NAME
import com.intellij.util.xml.XmlName
import com.intellij.xml.DefaultXmlExtension
import com.intellij.xml.XmlElementDescriptor
import com.intellij.xml.XmlExtension
import com.intellij.xml.XmlNSDescriptor

class HbXmlExtension: DefaultXmlExtension() {
    override fun isAvailable(file: PsiFile?): Boolean {
        return file?.viewProvider is GtsFileViewProvider || file?.viewProvider is HbFileViewProvider
    }
    override fun createTagNameReference(nameElement: ASTNode?, startTagFlag: Boolean): TagNameReference? {
        if (nameElement?.psi is XmlToken && nameElement.elementType == XML_NAME) {
            if (nameElement.text.startsWith(":") || nameElement.text.firstOrNull()?.isUpperCase() == true) {
                return null
            }
        }
        return super.createTagNameReference(nameElement, startTagFlag)
    }
}