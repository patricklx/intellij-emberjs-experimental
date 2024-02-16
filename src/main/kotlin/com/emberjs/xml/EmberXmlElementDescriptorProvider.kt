package com.emberjs.xml

import com.dmarcotte.handlebars.HbLanguage
import com.emberjs.gts.GtsLanguage
import com.emberjs.xml.EmberXmlElementDescriptor.Companion.forTag
import com.intellij.psi.impl.source.html.HtmlFileImpl
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.XmlElementDescriptor

class EmberXmlElementDescriptorProvider : XmlElementDescriptorProvider {
    override fun getDescriptor(tag: XmlTag?): XmlElementDescriptor? {
        if (tag == null) return null

        if (tag.containingFile.name.endsWith(".gjs")) {
            return forTag(tag)
        }

        if (tag.containingFile.name.endsWith(".gts")) {
            return forTag(tag)
        }


        val containingFile = tag.containingFile as? HtmlFileImpl ?: return null
        val language = containingFile.contentElementType?.language ?: return null
        if (language !is HbLanguage && language !is GtsLanguage) return null

        return forTag(tag)
    }
}
