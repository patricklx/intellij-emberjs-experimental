package com.emberjs.hbs

import com.dmarcotte.handlebars.psi.impl.HbStatementsImpl
import com.emberjs.EmberAttrDec
import com.emberjs.psi.EmberNamedAttribute
import com.emberjs.psi.EmberNamedElement
import com.emberjs.refactoring.SimpleNodeFactory
import com.intellij.lang.Language
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeDecl
import com.intellij.psi.xml.XmlTag


abstract class HbReference(element: PsiElement): PsiReferenceBase<PsiElement>(element)


class RangedReference(element: PsiElement, val targetPsi: PsiElement?, val range: TextRange) : HbReference(element) {
    private var targetRef: PsiReference? = null
    constructor(element: PsiElement, targetRef: PsiReference, range: TextRange) : this(element, null, range) {
        this.targetRef = targetRef
    }

    val target by lazy {
        if (targetPsi != null) {
            return@lazy targetPsi
        }
        return@lazy targetRef?.resolve()
    }


    private val named: EmberNamedElement? by lazy {
        target?.let { EmberNamedElement(it) }
    }
    private val namedXml: EmberNamedAttribute? by lazy {
        if (target is XmlAttribute && (target as XmlAttribute).descriptor?.declaration is EmberAttrDec) {
            return@lazy (target as XmlAttribute).let { EmberNamedAttribute(it.descriptor!!.declaration as XmlAttributeDecl, IntRange(range.startOffset, range.endOffset)) }
        }
        return@lazy null
    }

    override fun resolve(): PsiElement? {
        if (target is XmlAttribute) {
            return namedXml
        }
        return named
    }

    override fun getRangeInElement(): TextRange {
        return range
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        if (element is XmlAttribute) {
            val attr = element as XmlAttribute
            var newName = ""
            if (attr.name.startsWith("|")) {
                newName = "|"
            }
            newName += newElementName
            if (attr.name.endsWith("|")) {
                newName += "|"
            }
            attr.name = newName
            return element
        }
        if (element is HbStatementsImpl) {
            val tag = element.containingFile.viewProvider.getPsi(Language.findLanguageByID("HTML")!!).findElementAt(range.startOffset)!!.parent as XmlTag
            tag.name = newElementName
        }
        return super.handleElementRename(newElementName)
    }
}



class HbsLocalRenameReference(private val leaf: PsiElement, val target: PsiElement?) : HbReference(leaf) {
    val named = target?.let { EmberNamedElement(it) }
    override fun resolve(): PsiElement? {
        return named
    }

    override fun getRangeInElement(): TextRange {
        return TextRange(0, leaf.textLength)
    }

    override fun calculateDefaultRangeInElement(): TextRange {
        return leaf.textRangeInParent
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val node = SimpleNodeFactory.createNode(leaf.project, newElementName)
        return leaf.replace(node)
    }
}
