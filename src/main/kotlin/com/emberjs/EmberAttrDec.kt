package com.emberjs

import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.meta.PsiMetaData
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.search.SearchScope
import com.intellij.psi.xml.XmlAttributeDecl
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlElement
import com.intellij.psi.xml.XmlTag
import javax.swing.Icon

class EmberAttrDec(private val descriptor: EmberAttributeDescriptor, ref: PsiReference?, private val references: Array<PsiReference>?) : XmlAttributeDecl {
    private val userDataMap = HashMap<Any, Any>()
    private val reference: PsiReference?
    private var name: String
    override fun <T> getUserData(key: Key<T>): T? {
        return this.userDataMap[key] as T?
    }

    init {
        this.reference = ref ?: references!!.firstOrNull()
        this.name = descriptor.name
    }

    override fun <T> putUserData(key: Key<T>, value: T?) {
        this.userDataMap[key] = value as Any
    }

    override fun getIcon(flags: Int): Icon? {
        return null
    }

    override fun getProject(): Project {
        return this.context.project
    }

    override fun getLanguage(): Language {
        return this.context.language
    }

    override fun getManager(): PsiManager {
        return this.context.manager
    }

    override fun getChildren(): Array<PsiElement> {
        return emptyArray<PsiElement>()
    }

    override fun getParent(): XmlTag {
        return this.context
    }

    override fun getFirstChild(): PsiElement? {
        return null
    }

    override fun getLastChild(): PsiElement? {
        return null
    }

    override fun getNextSibling(): PsiElement? {
        return null
    }

    override fun getPrevSibling(): PsiElement? {
        return null
    }

    override fun getContainingFile(): PsiFile? {
        return this.context.containingFile;
    }

    override fun getTextRange(): TextRange? {
        return descriptor.context.attributes.find { it.descriptor == descriptor }?.textRange
    }

    override fun getStartOffsetInParent(): Int {
        return 0
    }

    override fun getTextLength(): Int {
        return name.length
    }

    override fun findElementAt(offset: Int): PsiElement? {
        return null
    }

    override fun findReferenceAt(offset: Int): PsiReference? {
        return null
    }

    override fun getTextOffset(): Int {
        return 0
    }

    override fun getText(): String {
        return this.name
    }

    override fun textToCharArray(): CharArray {
        return this.name.toCharArray()
    }

    override fun getNavigationElement(): PsiElement? {
        return this
    }

    override fun getOriginalElement(): PsiElement? {
        return this
    }

    override fun textMatches(text: CharSequence): Boolean {
        return name == text
    }

    override fun textMatches(element: PsiElement): Boolean {
        return element.text == name
    }

    override fun textContains(c: Char): Boolean {
        return name.contains(c)
    }

    override fun accept(visitor: PsiElementVisitor) {
        visitor.visitElement(this)
    }

    override fun acceptChildren(visitor: PsiElementVisitor) {
        TODO("Not yet implemented")
    }

    override fun copy(): PsiElement {
        TODO("Not yet implemented")
    }

    override fun add(element: PsiElement): PsiElement {
        TODO("Not yet implemented")
    }

    override fun addBefore(element: PsiElement, anchor: PsiElement?): PsiElement {
        TODO("Not yet implemented")
    }

    override fun addAfter(element: PsiElement, anchor: PsiElement?): PsiElement {
        TODO("Not yet implemented")
    }

    override fun checkAdd(element: PsiElement) {
        TODO("Not yet implemented")
    }

    override fun addRange(first: PsiElement?, last: PsiElement?): PsiElement {
        TODO("Not yet implemented")
    }

    override fun addRangeBefore(first: PsiElement, last: PsiElement, anchor: PsiElement?): PsiElement {
        TODO("Not yet implemented")
    }

    override fun addRangeAfter(first: PsiElement?, last: PsiElement?, anchor: PsiElement?): PsiElement {
        TODO("Not yet implemented")
    }

    override fun delete() {
        TODO("Not yet implemented")
    }

    override fun checkDelete() {
        TODO("Not yet implemented")
    }

    override fun deleteChildRange(first: PsiElement?, last: PsiElement?) {
        TODO("Not yet implemented")
    }

    override fun replace(newElement: PsiElement): PsiElement {
        TODO("Not yet implemented")
    }

    override fun isValid(): Boolean {
        return true;
    }

    override fun isWritable(): Boolean {
        return true
    }

    override fun getReference(): PsiReference? {
        return this.reference
    }

    override fun getReferences(): Array<PsiReference> {
        return this.references ?: emptyArray()
    }

    override fun <T : Any?> getCopyableUserData(key: Key<T>?): T? {
        TODO("Not yet implemented")
    }

    override fun <T : Any?> putCopyableUserData(key: Key<T>?, value: T?) {
        TODO("Not yet implemented")
    }

    override fun processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement?, place: PsiElement): Boolean {
        TODO("Not yet implemented")
    }

    override fun getContext(): XmlTag {
        return this.descriptor.context
    }

    override fun isPhysical(): Boolean {
        return true
    }

    override fun getResolveScope(): GlobalSearchScope {
        TODO("Not yet implemented")
    }

    override fun getUseScope(): SearchScope {
        return ProjectScope.getProjectScope(this.project)
    }

    override fun getNode(): ASTNode? {
        return null
    }

    override fun isEquivalentTo(another: PsiElement?): Boolean {
        return false
    }

    override fun processElements(processor: PsiElementProcessor<*>?, place: PsiElement?): Boolean {
        return true
    }

    override fun getMetaData(): PsiMetaData? {
        return null
    }

    override fun getName(): String {
        return name
    }

    override fun setName(name: String): PsiElement {
        var newName = ""
        if (this.name.startsWith("|")) {
            newName = "|"
        }
        newName += name
        if (this.name.endsWith("|")) {
            newName += "|"
        }
        descriptor.context.attributes.find { it.name == this.name }?.name = newName
        this.name = newName
        return this
    }

    override fun getNameElement(): XmlElement {
        return this
    }

    override fun getDefaultValue(): XmlAttributeValue? {
        return null
    }

    override fun getDefaultValueText(): String? {
        return null
    }

    override fun isAttributeRequired(): Boolean {
        return descriptor.isRequired
    }

    override fun isAttributeFixed(): Boolean {
        return descriptor.isFixed
    }

    override fun isAttributeImplied(): Boolean {
        return false
    }

    override fun isEnumerated(): Boolean {
       return descriptor.isEnumerated
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
