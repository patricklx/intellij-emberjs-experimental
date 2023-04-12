package com.emberjs.xml
import com.dmarcotte.handlebars.psi.HbPsiFile
import com.emberjs.glint.GlintLanguageServiceProvider
import com.emberjs.psi.EmberNamedElement
import com.emberjs.utils.*
import com.intellij.codeInsight.documentation.DocumentationManager.ORIGINAL_ELEMENT_KEY
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.lang.javascript.psi.JSNamedElement
import com.intellij.psi.*
import com.intellij.psi.impl.source.html.dtd.HtmlNSDescriptorImpl
import com.intellij.psi.impl.source.xml.TagNameReference
import com.intellij.psi.impl.source.xml.XmlDescriptorUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.XmlAttributeDescriptor
import com.intellij.xml.XmlElementDescriptor
import com.intellij.xml.XmlElementsGroup
import com.intellij.xml.XmlNSDescriptor
import com.sun.xml.bind.v2.runtime.unmarshaller.TagName


class GlintReference(val elem: PsiElement): PsiReferenceBase<PsiElement>(elem) {
    override fun resolve(): PsiElement? {
        val psiFile = PsiManager.getInstance(elem.project).findFile(elem.originalVirtualFile!!)
        val document = PsiDocumentManager.getInstance(elem.project).getDocument(psiFile!!)!!
        val service = GlintLanguageServiceProvider(elem.project).getService(elem.originalVirtualFile!!)
        return service?.getNavigationFor(document, elem)?.firstOrNull()
    }

}

class EmberXmlElementDescriptor(private val tag: XmlTag, private val declaration: PsiElement?) : XmlElementDescriptor {
    val project = tag.project
    val version = "v2022.1.11"

    override fun equals(other: Any?): Boolean {
        return (other as EmberXmlElementDescriptor).tag == this.tag && other.version == this.version
    }

    override fun hashCode(): Int {
        return (this.tag.name + this.version).hashCode()
    }

    companion object {

        fun forTag(tag: XmlTag): EmberXmlElementDescriptor? {
            val res: EmberNamedElement? = tag.references.lastOrNull()?.resolve() as? EmberNamedElement?
            if (res == null && !tag.name.startsWith(":") && !tag.name.first().isUpperCase()) {
                return null
            }
            return EmberXmlElementDescriptor(tag, res)
        }
    }

    override fun getDeclaration(): PsiElement? = declaration
    override fun getName(context: PsiElement?): String = (context as? XmlTag)?.name ?: name
    override fun getName(): String = tag.localName
    override fun init(element: PsiElement?) {
        element?.putUserData(ORIGINAL_ELEMENT_KEY, null)
    }
    override fun getQualifiedName(): String = name
    override fun getDefaultName(): String = name

    override fun getElementsDescriptors(context: XmlTag): Array<XmlElementDescriptor> {
        return XmlDescriptorUtil.getElementsDescriptors(context)
    }

    override fun getElementDescriptor(childTag: XmlTag, contextTag: XmlTag): XmlElementDescriptor? {
        return this
    }

    class YieldReference(element: PsiElement): PsiReferenceBase<PsiElement>(element) {

        val yieldBlock by lazy {
            element.parent.parent
        }

        override fun resolve(): PsiElement? {
            return yieldBlock
        }
    }

    /**
     * finds yields and data mustache `@xxx`
     * also check .ts/d.ts files for Component<Args>
     */
    fun getReferenceData(): ComponentReferenceData {
        var f: PsiFile? = null
        // if it references a block param
        val target: PsiElement?
        if (this.declaration is EmberNamedElement) {
            target = this.declaration.target
        } else {
            target = this.declaration
        }
        if (target == null) {
            return ComponentReferenceData()
        }
        val followed = EmberUtils.followReferences(target)
        if (followed is JSNamedElement || followed is JSFile || followed is HbPsiFile ) {
            return EmberUtils.getComponentReferenceData(followed)
        }
        val file = f ?: target.containingFile?.originalFile
        if (file == null || this.tag.originalVirtualFile?.path == file.originalVirtualFile?.path) {
            return ComponentReferenceData()
        }

        if (file.name == "intellij-emberjs/internal/components-stub") {
            return EmberUtils.getComponentReferenceData(target)
        }

        return EmberUtils.getComponentReferenceData(file)
    }

    override fun getAttributesDescriptors(context: XmlTag?): Array<out XmlAttributeDescriptor> {
        val result = mutableListOf<XmlAttributeDescriptor>()
        if (context != this.tag) {
            return result.toTypedArray()
        }
        val commonHtmlAttributes = HtmlNSDescriptorImpl.getCommonAttributeDescriptors(context)
        val data = getReferenceData()
        val attributes = data.args.map { EmberAttributeDescriptor(context, it.value, false, it.description, it.reference, null)  }
        result.addAll(attributes)
        if (data.hasSplattributes || this.declaration == null) {
            result.addAll(commonHtmlAttributes)
        }
        return result.toTypedArray()
    }

    override fun getAttributeDescriptor(attributeName: String?, context: XmlTag?): XmlAttributeDescriptor? {
        if (attributeName == null || context != this.tag) {
            return null
        }
        if (attributeName == "as") {
            val data = getReferenceData()
            return EmberAttributeDescriptor(context, attributeName, true, "yield", null, data.yields.toTypedArray())
        }
        val asIndex = context.attributes.indexOfFirst { it.text == "as" }
        if (asIndex >= 0) {
            // handle |param| or |param1 param2| or | param | or | param1 param2 | or | param1 param2|
            // for referencing && renaming the pattern | x y | would be the best
            // there is also a possiblity that this can be improved with value & valueTextRange
            // attributes are always separated by spaces
            val blockParams = context.attributes.toList().subList(asIndex + 1, context.attributes.size)
            val index = blockParams.indexOfFirst { it.text == attributeName }
            if (index == -1) {
                return this.getAttributesDescriptors(context).find { it.name == attributeName }
            }
            return EmberAttributeDescriptor(context, attributeName, true, "yield", null, emptyArray())
        }
        val attr = context.attributes.find { it.name == attributeName }
        return this.getAttributesDescriptors(context).find { it.name == attributeName } ?:
        attr?.let { EmberAttributeDescriptor(context, attributeName, true, "yield", GlintReference(it), emptyArray()) }

    }
    override fun getAttributeDescriptor(attribute: XmlAttribute?): XmlAttributeDescriptor?
            = getAttributeDescriptor(attribute?.name, attribute?.parent)

    override fun getNSDescriptor(): XmlNSDescriptor? = null
    override fun getTopGroup(): XmlElementsGroup? = null
    override fun getContentType(): Int = XmlElementDescriptor.CONTENT_TYPE_ANY
    override fun getDefaultValue(): String? = null
}
