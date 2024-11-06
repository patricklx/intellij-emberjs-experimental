package com.emberjs.hbs

import com.dmarcotte.handlebars.psi.HbSimpleMustache
import com.dmarcotte.handlebars.psi.HbStringLiteral
import com.dmarcotte.handlebars.psi.impl.HbStatementsImpl
import com.emberjs.gts.GjsLanguage
import com.emberjs.gts.GtsLanguage
import com.emberjs.index.EmberNameIndex
import com.emberjs.psi.EmberNamedAttribute
import com.emberjs.psi.EmberNamedElement
import com.emberjs.refactoring.SimpleNodeFactory
import com.emberjs.resolver.EmberName
import com.emberjs.utils.originalVirtualFile
import com.emberjs.xml.EmberAttrDec
import com.intellij.lang.Language
import com.intellij.lang.ecmascript6.psi.ES6ImportSpecifier
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeDecl
import com.intellij.psi.xml.XmlTag
import com.intellij.refactoring.rename.BindablePsiReference


interface EmberReference {}


abstract class HbReference(element: PsiElement): PsiReferenceBase<PsiElement>(element), EmberReference {
    override fun isReferenceTo(other: PsiElement): Boolean {
        var res = resolve()
        if (res is EmberNamedElement) {
            res = res.target
        }
        if (res is ES6ImportSpecifier) {
            var resolved = res.containingFile.viewProvider.findReferenceAt(res.textOffset, GtsLanguage.INSTANCE)?.resolve()
            resolved = resolved ?: res.containingFile.viewProvider.findReferenceAt(res.textOffset, GjsLanguage.INSTANCE)?.resolve()
            if (element.manager.areElementsEquivalent(resolved, other)) {
                return true
            }
            if (element.manager.areElementsEquivalent(res.reference?.resolve(), other)) {
                return true
            }
            if (res.references.any {
                    element.manager.areElementsEquivalent(it.element, other) || super.isReferenceTo(other)
                }) {
                return true
            }
            val results = res.multiResolve(false)
            val r = results.any {
                element.manager.areElementsEquivalent(it.element, other) || super.isReferenceTo(other)
            }
            if (r) {
                return true
            }
        }
        return element.manager.areElementsEquivalent(res, other) || super.isReferenceTo(other)
    }
}

open class RangedReference(element: PsiElement, val targetPsi: PsiElement?, val range: TextRange) : HbReference(element), BindablePsiReference {
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

    val isImportFrom by lazy {
        element is HbStringLiteral && PsiTreeUtil.findFirstParent(element) { it is HbSimpleMustache }?.text?.startsWith("{{import ") == true && target is PsiFile
    }

    override fun bindToElement(newElement: PsiElement): PsiElement {
        if (isImportFrom) {
            var newFileLocation = newElement as? PsiFile
            if (newElement is PsiDirectory) {
                newFileLocation = newElement.findFile((target as PsiFile).name)
            }
            val importPath = newFileLocation?.let { EmberName.from(newFileLocation.virtualFile)?.importPath } ?: return super.bindToElement(newElement)
            val node = SimpleNodeFactory.createTextNode(newElement.project, importPath)
            return element.replace(node)
        }
        return super.bindToElement(newElement)
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        if (element is HbStatementsImpl) {
            val tag = element.containingFile.viewProvider.getPsi(Language.findLanguageByID("HTML")!!).findElementAt(range.startOffset)!!.parent as XmlTag
            tag.name = newElementName
            return tag
        }
        val target = this.target as? PsiFile
        if (isImportFrom) {
            val text = EmberNameIndex.getFilteredPairs(GlobalSearchScope.allScope(element.project)) { it.virtualFile == target?.originalVirtualFile }.firstOrNull()?.first?.importPath ?: return element
            val node = SimpleNodeFactory.createTextNode(element.project, text)
            return element.replace(node)
        }
        return super.handleElementRename(newElementName)
    }
}


class ImportNameReference(element: PsiElement, psiElement: PsiElement?, textRange: TextRange): RangedReference(element, psiElement,textRange) {
    override fun handleElementRename(newElementName: String): PsiElement {
        val intRange = IntRange(range.startOffset, range.endOffset)
        val text = element.text.replaceRange(intRange, newElementName)
        val node = SimpleNodeFactory.createTextNode(element.project, text)
        return element.replace(node)
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
        val node = SimpleNodeFactory.createIdNode(leaf.project, newElementName)
        return leaf.replace(node)
    }
}
