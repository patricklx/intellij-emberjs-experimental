package com.emberjs.hbs

import com.dmarcotte.handlebars.parsing.HbTokenTypes
import com.dmarcotte.handlebars.psi.*
import com.dmarcotte.handlebars.psi.impl.HbOpenBlockMustacheImpl
import com.dmarcotte.handlebars.psi.impl.HbStatementsImpl
import com.emberjs.EmberAttrDec
import com.emberjs.glint.GlintLanguageServiceProvider
import com.emberjs.psi.EmberNamedAttribute
import com.emberjs.psi.EmberNamedElement
import com.emberjs.refactoring.SimpleNodeFactory
import com.emberjs.utils.EmberUtils
import com.emberjs.utils.originalVirtualFile
import com.emberjs.utils.parents
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.Language
import com.intellij.lang.ecmascript6.resolve.ES6PsiUtil
import com.intellij.lang.javascript.psi.JSElement
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSType
import com.intellij.lang.javascript.psi.JSTypeOwner
import com.intellij.lang.javascript.psi.ecma6.JSTypedEntity
import com.intellij.lang.javascript.psi.impl.JSVariableImpl
import com.intellij.lang.javascript.psi.jsdoc.impl.JSDocCommentImpl
import com.intellij.lang.javascript.psi.types.JSRecordTypeImpl
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.css.CssRulesetList
import com.intellij.psi.css.CssSelector
import com.intellij.psi.css.impl.CssRulesetImpl
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.impl.source.html.HtmlTagImpl
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeDecl
import com.intellij.psi.xml.XmlTag
import kotlin.math.max

class ImportNameReferences(element: PsiElement) : PsiPolyVariantReferenceBase<PsiElement>(element, TextRange(0, element.textLength), true) {
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val names = element.text.split(",")
        val named = names.map {
            if (it.contains(" as ")) {
                it.split(" as ").first()
            } else {
                it
            }
        }
        val mustache = element.parents.find { it is HbMustache }!!
        val path = mustache.children.findLast { it is HbParam }
        val fileRef = path?.references?.firstOrNull()?.resolve()
        if (fileRef is PsiDirectory) {
            return named
                    .map { fileRef.findFile(it) ?: fileRef.findSubdirectory(it) }
                    .filterNotNull()
                    .map { PsiElementResolveResult(it) }
                    .toTypedArray()
        }
        if (fileRef == null) {
            return emptyArray()
        }
        val ref = EmberUtils.resolveToEmber(fileRef as PsiFile)
        return arrayOf(PsiElementResolveResult(ref))
    }
}


class HbsLocalRenameReference(private val leaf: PsiElement, val target: PsiElement?) : PsiReferenceBase<PsiElement>(leaf) {
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


class HbsLocalReference(private val leaf: PsiElement, val target: PsiElement?) : PsiReferenceBase<PsiElement>(leaf) {
    private var namedXml: EmberNamedAttribute? = null
    private var named: EmberNamedElement?

    init {
        this.named = target?.let { EmberNamedElement(it) }
        if (target is XmlAttribute && target.descriptor?.declaration is EmberAttrDec) {
            this.namedXml = target.let { EmberNamedAttribute(it.descriptor!!.declaration as XmlAttributeDecl) }
        }
    }

    fun resolveYield(): PsiElement? {
        val name = leaf.text
        val res = this.resolve()
        if (res is XmlAttributeDecl) {
            val tag = res.context as XmlTag
            val index = tag.attributes.indexOfFirst { it.text == "as" }
            val blockParams = tag.attributes.toList().subList(index + 1, tag.attributes.size)
            val r = blockParams.find { it.text.matches(Regex("^\\|*\\b$name\\b\\|*$")) }
            val idx = blockParams.indexOf(r)
            var ref = tag.attributes[index] as PsiElement?
            ref = EmberUtils.followReferences(ref)
            if (ref is HbSimpleMustache) {
                ref = ref.children.filter { it is HbParam }.getOrNull(idx)
                return ref
            }
            return null
        }
        return null
    }

    override fun isReferenceTo(element: PsiElement): Boolean {
        return getElement().getManager().areElementsEquivalent(target, element);
    }

    override fun resolve(): PsiElement? {
        if (target?.originalVirtualFile?.path != leaf.originalVirtualFile?.path) {
            return target
        }
        if (target is XmlAttribute) {
            return namedXml
        }
        return named
    }

    override fun getRangeInElement(): TextRange {
        return TextRange(0, leaf.textLength)
    }

    override fun calculateDefaultRangeInElement(): TextRange {
        return leaf.textRangeInParent
    }

    override fun handleElementRename(newElementName: String): PsiElement? {
        if (leaf is HbPsiElement) {
            val node = SimpleNodeFactory.createNode(leaf.project, newElementName)
            return leaf.replace(node)
        }
        return leaf
    }

    companion object {

        fun resolveToLocalJs(element: PsiElement): HbsLocalReference? {
            if (element.originalVirtualFile !is VirtualFileWindow) {
                return null
            }
            val psiManager = PsiManager.getInstance(element.project)
            val f = psiManager.findFile((element.originalVirtualFile as VirtualFileWindow).delegate)
            val collection = ES6PsiUtil.createResolver(f as PsiElement).getLocalElements(element.text, listOf(f as PsiElement))

            if (collection.isEmpty()) {
                return null
            }
            return HbsLocalReference(element, collection.first())
        }

        fun resolveToJs(any: Any?, path: List<String>, resolveIncomplete: Boolean = false): PsiElement? {

            if (any is EmberNamedElement) {
                return resolveToJs(any.target, path, resolveIncomplete)
            }

            if (any is PsiFile) {
                val styleSheetLanguages = arrayOf("sass", "scss", "less")
                if (styleSheetLanguages.contains(any.language.id.lowercase())) {
                    PsiTreeUtil.collectElements(any) { it is CssRulesetList }.first().children.forEach { (it as CssRulesetImpl).selectors.forEach {
                        if (it.text == "." + path.first()) {
                            return it
                        }
                    }}
                }
            }

            if (any is CssSelector && any.ruleset?.block != null) {
                any.ruleset!!.block!!.children.map { it as? CssRulesetImpl }.filterNotNull().forEach{ it.selectors.forEach {
                    if (it.text == "." + path.first()) {
                        return it
                    }
                }}
            }

            if (any is PsiElement) {
                val resolvedHelper = EmberUtils.handleEmberHelpers(any)
                if (resolvedHelper != null) {
                    return resolveToJs(resolvedHelper, path)
                }

                val refYield = EmberUtils.findTagYieldAttribute(any)
                if (refYield != null && refYield.descriptor?.declaration != null) {
                    return resolveToJs(refYield.descriptor?.declaration, path, resolveIncomplete)
                }
            }

            if (any is PsiElement && any.references.find { it is HbsLocalReference } != null) {
                val ref = any.references.find { it is HbsLocalReference }
                return resolveToJs(ref?.resolve(), path, resolveIncomplete)
            }

            if (any is HbSimpleMustache && any.text.startsWith("{{yield ")) {
                path.first()
            }

            if (any is HbParam) {
                if (any.children[0].elementType == HbTokenTypes.OPEN_SEXPR) {
                    if (any.children[1].text == "hash") {
                        val name = path.first()
                        val res = any.children.find { it.elementType == HbTokenTypes.HASH && it.children[0].text == name }
                        if (res != null && res.children[2].children.firstOrNull() is HbMustacheName) {
                            val mustacheImpl = res.children[2].children.first()
                            val lastId = mustacheImpl.children[0].children.findLast { it.elementType == HbTokenTypes.ID }
                            return resolveToJs(lastId, path.subList(1, max(path.lastIndex, 1)), resolveIncomplete) ?: res
                        }
                        val ref = PsiTreeUtil.collectElements(res, { it.references.find { it is HbsLocalReference } != null }).firstOrNull()
                        if (ref != null) {
                            val hbsRef = ref.references.find { it is HbsLocalReference }!!
                            return resolveToJs(hbsRef.resolve(), path.subList(1, max(path.lastIndex, 1)), resolveIncomplete)
                        }
                        return res
                    }
                }
                if (any.children[0] is HbMustacheName) {
                    val lastId = any.children[0].children[0].children.findLast { it.elementType == HbTokenTypes.ID } // lookup id ref (something.x)
                            ?: any.children[0].children[1].children.findLast { it.elementType == HbTokenTypes.ID } // lookup data if ref (@something.x)
                    return resolveToJs(lastId, path, resolveIncomplete)
                }
            }

            if (any is PsiFile) {
                val helper = EmberUtils.resolveHelper(any)
                if (helper != null) {
                    return resolveToJs(helper, path, resolveIncomplete)
                }
                val modifier = EmberUtils.resolveDefaultModifier(any)
                if (modifier != null) {
                    return modifier
                }
                return EmberUtils.resolveDefaultExport(any)
            }

            if (path.isEmpty()) {
                return any as PsiElement?
            }

            var jsType: JSType? = null
            if (any is JSTypedEntity) {
                jsType = any.jsType
            }
            if (any is JSTypeOwner) {
                jsType = any.jsType
            }
            if (any is JSFunction && any.isGetProperty) {
                jsType = any.returnType
            }
            if (jsType != null) {
                if (jsType.sourceElement is JSDocCommentImpl) {
                    val doc = jsType.sourceElement as JSDocCommentImpl
                    val tag = doc.tags.find { it.text.startsWith("@type") }
                    val res = tag?.value?.reference?.resolve()
                    if (res != null) {
                        return resolveToJs(res, path, resolveIncomplete)
                    }
                }
                jsType = jsType.asRecordType()
                if (jsType is JSRecordTypeImpl) {
                    val elem = jsType.findPropertySignature(path.first())?.memberSource?.singleElement
                    return resolveToJs(elem, path.subList(1, max(path.lastIndex, 1)), resolveIncomplete)
                }
                if (any is JSVariableImpl<*, *> && any.doGetExplicitlyDeclaredType() != null) {
                    val jstype = any.doGetExplicitlyDeclaredType()
                    if (jstype is JSRecordTypeImpl) {
                        return resolveToJs(jstype.sourceElement, path.subList(1, max(path.lastIndex, 1)), resolveIncomplete)
                    }
                }
            }
            val followed = EmberUtils.followReferences(any as PsiElement?)
            if (followed !== null && followed != any) {
                return resolveToJs(followed, path)
            }
            return null
        }

        fun referenceBlocks(element: PsiElement, name: String): PsiReference? {
            // any |block param|
            // as mustache
            val hbsView = element.containingFile.viewProvider.getPsi(Language.findLanguageByID("Handlebars")!!)
            val hbblockRefs = PsiTreeUtil.collectElements(hbsView, { it is HbOpenBlockMustacheImpl })
                    .filter {
                        PsiTreeUtil.collectElements(PsiTreeUtil.getNextSiblingOfType(it, HbStatementsImpl::class.java), { it == element }).isNotEmpty()
                    }

            // as html tag
            val htmlView = element.containingFile.viewProvider.getPsi(Language.findLanguageByID("HTML")!!)
            val angleBracketBlocks = PsiTreeUtil.collectElements(htmlView, { it is XmlAttribute && it.text.startsWith("|") })
                    .filter{ (it.parent as HtmlTag).attributes.map { it.text }.joinToString(" ").contains(Regex("\\|.*\\b$name\\b.*\\|")) }
                    .map { it.parent }

            // validate if the element is a child of the tag
            val validBlock = angleBracketBlocks.filter { it ->
                it.textRange.contains(element.textRange)
            }.firstOrNull()

            val blockRef = hbblockRefs.find { it.text.contains(Regex("\\|.*\\b$name\\b.*\\|")) }
            val blockVal = blockRef?.children?.filter { it.elementType == HbTokenTypes.ID }?.find { it.text == name }


            if (blockRef != null || blockVal != null || validBlock != null) {
                if (validBlock != null) {
                    val tag  = validBlock as HtmlTagImpl
                    val index = tag.attributes.indexOfFirst { it.text == "as" }
                    val blockParams = tag.attributes.toList().subList(index + 1, tag.attributes.size)
                    val r = blockParams.find { it.text.matches(Regex("^\\|*\\b$name\\b\\|*$")) }
                    return r?.let { HbsLocalReference(element, it) }
                }
                return HbsLocalReference(element, blockVal ?: blockRef)
            }
            return null
        }

        fun createReference(element: PsiElement): PsiReference? {
            val psiFile = PsiManager.getInstance(element.project).findFile(element.originalVirtualFile!!)
            val document = PsiDocumentManager.getInstance(element.project).getDocument(psiFile!!)!!
            val service = GlintLanguageServiceProvider(element.project).getService(element.originalVirtualFile!!)

            val name = element.text.replace("IntellijIdeaRulezzz", "")

            val sibling = PsiTreeUtil.findSiblingBackward(element, HbTokenTypes.ID, null)
            if (name == "this" && sibling == null) {
                val cls = EmberUtils.findBackingJsClass(element)
                if (cls != null) {
                    return HbsLocalReference(element, cls)
                }
            }

            // for this.x.y
            if (sibling != null && sibling.references.find { it is HbsLocalReference || it is HbsLocalRenameReference } != null) {
                val ref = sibling.references.find { it is HbsLocalReference } as? HbsLocalReference
                val yieldRef = ref?.resolveYield()
                if (yieldRef != null) {
                    val res = resolveToJs(yieldRef, listOf(element.text))
                    return HbsLocalReference(element, res ?: service?.getNavigationFor(document, element)?.firstOrNull()?.parent)
                }
                if (ref != null) {
                    return HbsLocalReference(element, resolveToJs(ref.resolve(), listOf(element.text)))
                }
                val ref2 = sibling.references.find { it is HbsLocalRenameReference } as HbsLocalRenameReference
                val res = resolveToJs(ref2.resolve(), listOf(element.text))
                return HbsLocalReference(element, res ?: service?.getNavigationFor(document, element)?.firstOrNull()?.parent)
            }

            if (element.parent is HbData) {
                val cls = EmberUtils.findBackingJsClass(element)
                if (cls != null) {
                    val args = EmberUtils.findComponentArgsType(cls as JSElement)
                    val prop = args?.properties?.find { it.memberName == name }
                    if (prop != null) {
                        return HbsLocalReference(element, prop.memberSource.singleElement)
                    }
                    return null
                }
            }

            val importRef = EmberUtils.referenceImports(element, name)
            if (importRef != null) {
                return HbsLocalRenameReference(element, importRef)
            }

            if (element.parent is HbOpenBlockMustache && element.parent.children[0] != element) {
                return HbsLocalRenameReference(element, element)
            }

            return referenceBlocks(element, name)
                    ?: resolveToLocalJs(element)
                    ?: let {
                        val psiFile = PsiManager.getInstance(element.project).findFile(element.originalVirtualFile!!)
                        val document = PsiDocumentManager.getInstance(element.project).getDocument(psiFile!!)!!
                        HbsLocalReference(element,
                                GlintLanguageServiceProvider(element.project).getService(element.originalVirtualFile!!)
                                ?.getNavigationFor(document, element)?.firstOrNull()?.parent
                        )
                    }
        }
    }
}
