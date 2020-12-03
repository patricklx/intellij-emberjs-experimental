package com.emberjs.psi

import com.dmarcotte.handlebars.psi.HbBlockWrapper
import com.dmarcotte.handlebars.psi.HbPsiElement
import com.emberjs.EmberAttrDec
import com.emberjs.EmberXmlElementDescriptor
import com.emberjs.refactoring.SimpleNodeFactory
import com.emberjs.utils.parents
import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.meta.PsiMetaData
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.*
import javax.swing.Icon

open class EmberNamedElement(val target: PsiElement, val range: IntRange = IntRange(0, target.textLength - 1)) : PsiNameIdentifierOwner {
    private val userDataMap = HashMap<Any, Any>()
    override fun <T : Any?> getUserData(key: Key<T>): T? {
        return this.userDataMap[key] as T?
    }

    override fun equals(other: Any?): Boolean {
        if (other is EmberNamedElement) {
            return other.target == target
        }
        return target == other
    }

    override fun hashCode(): Int {
        return target.hashCode()
    }

    override fun <T : Any?> putUserData(key: Key<T>, value: T?) {
        this.userDataMap[key] = value as Any
    }

    override fun getIcon(flags: Int): Icon? {
        return null
    }

    override fun getProject(): Project {
        return target.project
    }

    override fun getLanguage(): Language {
        return target.language
    }

    override fun getManager(): PsiManager {
        return target.manager
    }

    override fun getChildren(): Array<PsiElement> {
        return target.children
    }

    override fun getParent(): PsiElement {
        return target.parent
    }

    override fun getFirstChild(): PsiElement {
        return target.firstChild
    }

    override fun getLastChild(): PsiElement {
        return target.lastChild
    }

    override fun getNextSibling(): PsiElement {
        return target.nextSibling
    }

    override fun getPrevSibling(): PsiElement {
        return target.prevSibling
    }

    override fun getContainingFile(): PsiFile {
        return target.containingFile
    }

    override fun getTextRange(): TextRange {
        return target.textRange
    }

    override fun getStartOffsetInParent(): Int {
        return target.startOffsetInParent
    }

    override fun getTextLength(): Int {
        return target.textLength
    }

    override fun findElementAt(offset: Int): PsiElement? {
        return target.findElementAt(offset)
    }

    override fun findReferenceAt(offset: Int): PsiReference? {
        return target.findReferenceAt(offset)
    }

    override fun getTextOffset(): Int {
        return target.textOffset
    }

    override fun getText(): String {
        return target.text
    }

    override fun textToCharArray(): CharArray {
        return target.textToCharArray()
    }

    override fun getNavigationElement(): PsiElement {
        return target.navigationElement
    }

    override fun getOriginalElement(): PsiElement {
        return target.originalElement
    }

    override fun textMatches(text: CharSequence): Boolean {
        return target.textMatches(text)
    }

    override fun textMatches(element: PsiElement): Boolean {
        return target.textMatches(element)
    }

    override fun textContains(c: Char): Boolean {
        return target.textContains(c)
    }

    override fun accept(visitor: PsiElementVisitor) {
        return target.accept(visitor)
    }

    override fun acceptChildren(visitor: PsiElementVisitor) {
        return target.acceptChildren(visitor)
    }

    override fun copy(): PsiElement {
        return target.copy()
    }

    override fun add(element: PsiElement): PsiElement {
        return target.add(element)
    }

    override fun addBefore(element: PsiElement, anchor: PsiElement?): PsiElement {
        return target.addBefore(element, anchor)
    }

    override fun addAfter(element: PsiElement, anchor: PsiElement?): PsiElement {
        return target.addBefore(element, anchor)
    }

    override fun checkAdd(element: PsiElement) {
        return target.checkAdd(element)
    }

    override fun addRange(first: PsiElement?, last: PsiElement?): PsiElement {
        return target.addRange(first, last)
    }

    override fun addRangeBefore(first: PsiElement, last: PsiElement, anchor: PsiElement?): PsiElement {
        return target.addRangeBefore(first, last, anchor)
    }

    override fun addRangeAfter(first: PsiElement?, last: PsiElement?, anchor: PsiElement?): PsiElement {
        return target.addRangeAfter(first, last, anchor)
    }

    override fun delete() {
        return target.delete()
    }

    override fun checkDelete() {
        return target.checkDelete()
    }

    override fun deleteChildRange(first: PsiElement?, last: PsiElement?) {
        return target.deleteChildRange(first, last)
    }

    override fun replace(newElement: PsiElement): PsiElement {
        return target.replace(newElement)
    }

    override fun isValid(): Boolean {
        return target.isValid
    }

    override fun isWritable(): Boolean {
        return target.isWritable
    }

    override fun getReference(): PsiReference? {
        return target.reference
    }

    override fun getReferences(): Array<PsiReference> {
        return target.references
    }

    override fun <T : Any?> getCopyableUserData(key: Key<T>?): T? {
        return target.getCopyableUserData(key)
    }

    override fun <T : Any?> putCopyableUserData(key: Key<T>?, value: T?) {
        return target.putCopyableUserData(key, value)
    }

    override fun processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement?, place: PsiElement): Boolean {
        return target.processDeclarations(processor, state, lastParent, place)
    }

    override fun getContext(): PsiElement? {
        return target.context
    }

    override fun isPhysical(): Boolean {
        return target.isPhysical
    }

    override fun getResolveScope(): GlobalSearchScope {
        return target.resolveScope
    }

    override fun getUseScope(): SearchScope {
        val elements = mutableListOf<PsiElement>()
        val hbsView = target.containingFile.viewProvider.getPsi(Language.findLanguageByID("Handlebars")!!)
        val htmlView = target.containingFile.viewProvider.getPsi(Language.findLanguageByID("HTML")!!)
        if (target.language == Language.findLanguageByID("Handlebars")) {
            val hbs = target.parents.find { it is HbBlockWrapper }!!
            val htmlElements = PsiTreeUtil.collectElements(htmlView) { hbs.textRange.contains(it.textRange) && hbs.references.any { it.isReferenceTo(target) } }
            elements.add(hbs)
            elements.addAll(htmlElements)
        }
        if (target.language == Language.findLanguageByID("HTML") && target.parents.find { it is XmlTag } != null) {
            val html = target.parents.find { it is XmlTag }!!
            val hbsElements = PsiTreeUtil.collectElements(hbsView) { html.textRange.contains(it.textRange) && !html.references.any { it.isReferenceTo(target) } }
            elements.add(html)
            elements.addAll(hbsElements)
        }
        return LocalSearchScope(elements.toTypedArray())
    }

    override fun getNode(): ASTNode {
        return target.node
    }

    override fun isEquivalentTo(another: PsiElement?): Boolean {
        return target.isEquivalentTo(another)
    }

    override fun getName(): String {
        if (target is XmlTag) {
            return target.name.substring(range)
        }
        if (target is EmberAttrDec) {
            var start = 0
            var end = target.name.length - 1
            if (target.name.startsWith("|")) {
                start = 1
            }
            if (target.name.startsWith("|")) {
                end -= 1
            }
            val r = IntRange(start, end)
            return target.text.substring(r)
        }
        return target.text.substring(range)
    }

    override fun setName(name: String): PsiElement {
        if (target is XmlTag) {
            target.name = name
        }
        if (target is EmberAttrDec) {
            target.name = name
        }
        if (target is HbPsiElement) {
            val node = SimpleNodeFactory.createNode(target.project, name)
            return target.replace(node)
        }
        return target
    }

    override fun getNameIdentifier(): PsiElement? {
        return target
    }
}


class EmberNamedAttribute(val xmltarget: XmlAttributeDecl, range: IntRange = IntRange(0, xmltarget.textLength - 1)) : XmlAttributeDecl, EmberNamedElement(xmltarget, range) {
    override fun processElements(processor: PsiElementProcessor<*>?, place: PsiElement?): Boolean {
        return true
    }

    override fun getMetaData(): PsiMetaData? {
        return null
    }

    override fun getNameElement(): XmlElement {
        return this
    }

    override fun getDefaultValue(): XmlAttributeValue? {
        return null
    }

    override fun getDefaultValueText(): String {
        return ""
    }

    override fun isAttributeRequired(): Boolean {
        return xmltarget.isAttributeRequired
    }

    override fun isAttributeFixed(): Boolean {
        return xmltarget.isAttributeFixed
    }

    override fun isAttributeImplied(): Boolean {
        return false
    }

    override fun isEnumerated(): Boolean {
        return false
    }

    override fun getEnumeratedValues(): Array<XmlElement> {
        return emptyArray()
    }

    override fun isIdAttribute(): Boolean {
        return false
    }

    override fun isIdRefAttribute(): Boolean {
        return false
    }

}
