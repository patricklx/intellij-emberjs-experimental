package com.emberjs.xml

import com.dmarcotte.handlebars.file.HbFileViewProvider
import com.emberjs.gts.GtsFileViewProvider
import com.emberjs.hbs.TagReferencesProvider
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.xml.TagNameReference
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlToken
import com.intellij.psi.xml.XmlTokenType.XML_NAME
import com.intellij.xml.DefaultXmlExtension


class EmberTagNameReference(nameElement: ASTNode, startTagFlag: Boolean) : TagNameReference(nameElement, startTagFlag) {

    val tag = nameElement.psi.parent as XmlTag

    override fun getRangeInElement(): TextRange {
        if (tag.name.contains(".")) {
            val part = tag.name.split(".").last()
            val start = tag.name.length - part.length + 1
            return TextRange(start, start + part.length)
        }
        return super.getRangeInElement()
    }

    override fun resolve(): PsiElement? {
        val element = TagReferencesProvider.getReferencesByElement(tag).lastOrNull()?.resolve()
        if (element != null) {
            return element
        }
        if (nameElement.text.startsWith(":") || nameElement.text.firstOrNull()?.isUpperCase() == true) {
            return null
        }
        return super.resolve()
    }
}

class HbXmlExtension: DefaultXmlExtension() {
    override fun isAvailable(file: PsiFile?): Boolean {
        return file?.viewProvider is GtsFileViewProvider || file?.viewProvider is HbFileViewProvider
    }
    override fun createTagNameReference(nameElement: ASTNode?, startTagFlag: Boolean): TagNameReference? {
        if (nameElement?.psi is XmlToken && nameElement.elementType == XML_NAME && nameElement.psi.parent is XmlTag) {
            if (nameElement.text.startsWith(":") || nameElement.text.firstOrNull()?.isUpperCase() == true || nameElement.text.contains(".")) {
                return null
            }
            if (TagReferencesProvider.getReferencesByElement(nameElement.psi.parent).lastOrNull()?.resolve() != null) {
                return null
            }
        }
        return super.createTagNameReference(nameElement, startTagFlag)
    }
}