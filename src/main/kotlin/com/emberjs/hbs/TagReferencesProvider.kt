package com.emberjs.hbs

import com.dmarcotte.handlebars.parsing.HbTokenTypes
import com.dmarcotte.handlebars.psi.HbHash
import com.dmarcotte.handlebars.psi.HbParam
import com.dmarcotte.handlebars.psi.HbSimpleMustache
import com.dmarcotte.handlebars.psi.impl.HbOpenBlockMustacheImpl
import com.emberjs.EmberAttrDec
import com.emberjs.EmberXmlElementDescriptor
import com.emberjs.glint.GlintLanguageServiceProvider
import com.emberjs.gts.GtsFileViewProvider
import com.emberjs.index.EmberNameIndex
import com.emberjs.psi.EmberNamedAttribute
import com.emberjs.psi.EmberNamedElement
import com.emberjs.utils.EmberUtils
import com.emberjs.utils.originalVirtualFile
import com.intellij.injected.editor.DocumentWindow
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.Language
import com.intellij.lang.ecmascript6.psi.ES6ImportDeclaration
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.JSTypeOwner
import com.intellij.lang.javascript.psi.JSVariable
import com.intellij.lang.javascript.psi.ecma6.ES6TaggedTemplateExpression
import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeofType
import com.intellij.lang.javascript.psi.impl.JSOuterLanguageElementExpressionImpl
import com.intellij.lang.javascript.psi.impl.JSUseScopeProvider
import com.intellij.lang.javascript.psi.resolve.JSContextResolver
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.util.*
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeDecl
import com.intellij.psi.xml.XmlTag
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.ProcessingContext

class ResolvedReference(element: PsiElement, private val resolved: PsiElement): PsiReferenceBase<PsiElement>(element) {
    override fun resolve(): PsiElement? {
        return resolved
    }
}

/**
 * this is just to remove leading and trailing `|` from attribute references
 */
fun toAttributeReference(target: XmlAttribute): PsiReference? {
    val name = target.name
    if ((name.startsWith("|") || name.endsWith("|")) && target.descriptor?.declaration != null) {
        if (name.length == 1) {
            return null
        }
        var range = TextRange(0, name.length)
        if (name.startsWith("|")) {
            range = TextRange(1, range.endOffset)
        }
        if (name.endsWith("|")) {
            range = TextRange(range.startOffset, range.endOffset - 1)
        }
        return RangedReference(target, target, range)
    }
    val psiFile = PsiManager.getInstance(target.project).findFile(target.originalVirtualFile!!)
    val document = PsiDocumentManager.getInstance(target.project).getDocument(psiFile!!)!!
    val service = GlintLanguageServiceProvider(target.project).getService(target.originalVirtualFile!!)
    val resolved = service?.getNavigationFor(document, target)?.firstOrNull()
    return resolved?.let {
        ResolvedReference(target, resolved)
    }
}


class TagReference(val element: XmlTag, val fullName: String, val range: TextRange) : HbReference(element) {

    override fun resolve(): PsiElement? {
        val t = TagReferencesProvider.forTag(element, fullName)
        if (t is XmlAttribute && t.descriptor?.declaration is EmberAttrDec) {
            return t.let { EmberNamedAttribute(it.descriptor!!.declaration as XmlAttributeDecl, IntRange(range.startOffset, range.endOffset)) }
        }
        return t?.let { EmberNamedElement(it, IntRange(range.startOffset, range.endOffset-1)) }
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        val r = resolve() as EmberNamedElement?
        return getElement().manager.areElementsEquivalent(r?.target, element)
    }

    override fun getRangeInElement(): TextRange {
        return range
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val parts = element.name.split('.').toMutableList()
        parts[0] = newElementName
        element.name = parts.joinToString(".")
        return element
    }
}

class TagReferencesProvider : PsiReferenceProvider() {

    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<TagReference> {
        val tag = element as XmlTag
        val parts = tag.name.split(".")
        val references = parts.mapIndexed { index, s ->
            val p = parts.subList(0, index).joinToString(".")
            val fullName = parts.subList(0, index + 1).joinToString(".")

            val offset: Int
            if (p.length == 0) {
                // <
                offset = 1
            } else {
                // < and .
                offset = p.length + 2
            }

            val range = TextRange(offset, offset + s.length)
            val ref = TagReference(tag, fullName, range)
            ref.resolve()?.let { ref }
        }
        return references.filterNotNull().toTypedArray()
    }

    companion object {

        fun resolveToLocalJs(element: XmlTag): PsiElement? {
            var tpl: Any? = null
            var f: PsiFile? = null
            if (element.originalVirtualFile is VirtualFileWindow) {
                val psiManager = PsiManager.getInstance(element.project)
                f = psiManager.findFile((element.originalVirtualFile as VirtualFileWindow).delegate)!!
                val manager = InjectedLanguageManager.getInstance(element.project)
                val templates = PsiTreeUtil.collectElements(f) { it is ES6TaggedTemplateExpression && it.tag?.text == "hbs" }.mapNotNull { (it as ES6TaggedTemplateExpression).templateExpression }
                tpl = templates.find {
                    val injected = manager.findInjectedElementAt(f as PsiFile, it.startOffset + 1)?.containingFile ?: return@find false
                    val virtualFile = injected.virtualFile
                    return@find virtualFile is VirtualFileWindow && virtualFile == (element.originalVirtualFile as VirtualFileWindow)
                } ?: return null
            }

            if (element.containingFile.viewProvider is GtsFileViewProvider) {
                val view = element.containingFile.viewProvider
                val JS = Language.findLanguageByID("JavaScript")!!
                val TS = Language.findLanguageByID("TypeScript")!!
                val tsView = view.getPsi(TS)
                val jsView = view.getPsi(JS)
                f = tsView ?: jsView
                tpl = view.findElementAt(element.startOffset, TS) ?: view.findElementAt(element.startOffset, JS)
            }

            if (tpl == null) {
                return null
            }

            val parts = element.name.split(".")
            var current: PsiElement? = null
            if (parts.first() == "this") {
                current = JSContextResolver.resolveThisReference(tpl as PsiElement)
            } else {
                val children = PsiTreeUtil.collectElements(f) { it is JSVariable || it is ES6ImportDeclaration }
                current = children.mapNotNull {
                    if (it is JSVariable && it.name?.equals(parts.first()) == true) {
                        val useScope = JSUseScopeProvider.getUseScopeElement(it)
                        if (useScope.isAncestor(tpl as PsiElement)) {
                            return@mapNotNull it
                        }
                    }

                    if (it is ES6ImportDeclaration) {
                        it.importedBindings.forEach { ib ->
                            if (ib.name?.equals(parts.first()) == true) {
                                return@mapNotNull ib
                            }
                        }
                        it.importSpecifiers.forEach {iss ->
                            val name = iss.alias?.name ?: iss.name ?: ""
                            if (name == parts.first()) {
                                return@mapNotNull iss.alias ?: iss
                            }
                        }
                    }
                    return@mapNotNull null
                }.firstOrNull()
            }

            parts.subList(1, parts.size).forEach {part ->
                current = (current as? JSTypeOwner)?.jsType?.asRecordType()?.properties?.find { it.memberName == part }?.jsType?.sourceElement
            }

            if (current is TypeScriptTypeofType) {
                current = (current as TypeScriptTypeofType).expression
            }

            return current?.let { EmberUtils.resolveToEmber(it) }
        }

        fun fromNamedYields(tag: XmlTag, name: String): PsiElement? {
            val angleComponents = tag.parents.find {
                it is XmlTag && it.descriptor is EmberXmlElementDescriptor
            } as XmlTag? ?: return null
            val data = (angleComponents.descriptor as EmberXmlElementDescriptor).getReferenceData()
            val tplYields = data.yields

            return tplYields
                    .map { it.yieldBlock }
                    .filterNotNull()
                    .find {
                        it.children.find { it is HbHash && it.hashName == "to" && it.children.last().text.replace(Regex("\"|'"), "") == name} != null
                    }
        }

        fun fromLocalBlock(tag: XmlTag, fullName: String): PsiElement? {
            val name = fullName.split(".").first()
            if (name.startsWith(":")) {
                return fromNamedYields(tag, name.removePrefix(":"))
            }
            // find html blocks with attribute |name|
            var blockParamIdx = 0
            var refPsi: PsiElement? = null
            val angleBracketBlock: XmlTag? = tag.parentsWithSelf
                    .find {
                        it is XmlTag && it.attributes.map { it.text }.joinToString(" ").contains(Regex("\\|.*\\b$name\\b.*\\|"))
                    } as XmlTag?

            if (angleBracketBlock != null) {
                val startIdx = angleBracketBlock.attributes.indexOfFirst { it.text.startsWith("|") }
                val endIdx = angleBracketBlock.attributes.size
                val params = angleBracketBlock.attributes.toList().subList(startIdx, endIdx)
                refPsi = params.find { Regex("\\|*.*\\b$name\\b.*\\|*").matches(it.text) }
                blockParamIdx = params.indexOf(refPsi)
            }

            // find mustache block |params| which has tag as a child
            val hbsView = tag.containingFile.viewProvider.getPsi(Language.findLanguageByID("Handlebars")!!)
            val hbBlockRef = PsiTreeUtil.collectElements(hbsView, { it is HbOpenBlockMustacheImpl })
                    .filter { it.text.contains(Regex("\\|.*\\b$name\\b.*\\|")) }
                    .map { it.parent }
                    .find { block ->
                        block.textRange.contains(tag.textRange)
                    }

            val param = hbBlockRef?.children?.firstOrNull()?.children?.find { it.elementType == HbTokenTypes.ID && it.text == name}
            blockParamIdx = hbBlockRef?.children?.firstOrNull()?.children?.indexOf(param) ?: blockParamIdx
            val parts = fullName.split(".")
            var ref = param ?: refPsi
            parts.subList(1, parts.size).forEach { part ->
                ref = EmberUtils.followReferences(ref)
                if (ref is HbSimpleMustache) {
                    ref = (ref as HbSimpleMustache).children.filter { it is HbParam }.getOrNull(blockParamIdx)
                    ref = EmberUtils.followReferences(ref) ?: ref
                }
                if (ref is HbParam && ref!!.children.getOrNull(1)?.text == "hash") {
                    ref = ref!!.children.filter { c -> c is HbHash }.find { c -> (c as HbHash).hashName == part }
                    if (ref is HbHash) {
                        ref = ref!!.children.last()
                        ref = EmberUtils.followReferences(ref)
                    }
                }
            }
            return ref
        }

        fun fromImports(tag: XmlTag): PsiElement? {
            return EmberUtils.referenceImports(tag, tag.name.split(".").first())
        }

        fun forTag(tag: XmlTag?, fullName: String): PsiElement? {
            if (tag == null) return null
            val local = fromLocalBlock(tag, fullName) ?: fromImports(tag)
            if (local != null) {
                return local
            }

            return resolveToLocalJs(tag)
                    ?: forTagName(tag.project, tag.name)
                    ?: let {
                        val psiFile = PsiManager.getInstance(tag.project).findFile(tag.originalVirtualFile!!)
                        var document = PsiDocumentManager.getInstance(tag.project).getDocument(psiFile!!)!!
                        val service = GlintLanguageServiceProvider(tag.project).getService(tag.originalVirtualFile!!)
                        service?.getNavigationFor(document, tag)?.firstOrNull()
                    }
        }

        fun forTagName(project: Project, tagName: String): PsiElement? {
            val name = tagName
                    .replace(Regex("-(.)")) { it.groupValues.last().uppercase() }
                    .replace(Regex("/(.)")) { "::" + it.groupValues.last().uppercase() }
            val internalComponentsFile = PsiFileFactory.getInstance(project).createFileFromText("intellij-emberjs/internal/components-stub", Language.findLanguageByID("TypeScript")!!, TagReferencesProvider::class.java.getResource("/com/emberjs/external/ember-components.ts").readText())
            val internalComponents = EmberUtils.resolveDefaultExport(internalComponentsFile) as JSObjectLiteralExpression

            if (internalComponents.properties.map { it.name }.contains(name)) {
                val prop = internalComponents.properties.find { it.name == name }
                return (prop?.jsType?.sourceElement as JSReferenceExpression).resolve()
            }

            val scope = ProjectScope.getAllScope(project)
            val psiManager: PsiManager by lazy { PsiManager.getInstance(project) }

            val templates = EmberNameIndex.getFilteredFiles(scope) { it.isComponentTemplate && it.angleBracketsName == name }
                    .mapNotNull { psiManager.findFile(it) }

            val components = EmberNameIndex.getFilteredFiles(scope) { it.type == "component" && it.angleBracketsName == name }
                    .mapNotNull { psiManager.findFile(it) }
            // find name.js first, then component.js
            val component = components.find { !it.name.startsWith("component.") } ?: components.find { it.name.startsWith("component.") }

            if (component != null) return EmberUtils.resolveToEmber(component)

            // find name.hbs first, then template.hbs
            val componentTemplate = templates.find { !it.name.startsWith("template.") } ?: templates.find { it.name.startsWith("template.") }

            if (componentTemplate != null) return EmberUtils.resolveToEmber(componentTemplate)

            return null
        }
    }
}

