package com.emberjs.hbs

import com.dmarcotte.handlebars.parsing.HbTokenTypes
import com.dmarcotte.handlebars.psi.*
import com.dmarcotte.handlebars.psi.impl.HbOpenBlockMustacheImpl
import com.dmarcotte.handlebars.psi.impl.HbStatementsImpl
import com.emberjs.glint.GlintLanguageServiceProvider
import com.emberjs.psi.EmberNamedAttribute
import com.emberjs.psi.EmberNamedElement
import com.emberjs.refactoring.SimpleNodeFactory
import com.emberjs.utils.EmberUtils
import com.emberjs.utils.originalVirtualFile
import com.emberjs.xml.EmberAttrDec
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.Language
import com.intellij.lang.ecmascript6.psi.ES6ImportDeclaration
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.javascript.JavaScriptSupportLoader
import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.lang.javascript.psi.ecma6.JSTypedEntity
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.impl.JSUseScopeProvider
import com.intellij.lang.javascript.psi.impl.JSVariableImpl
import com.intellij.lang.javascript.psi.jsdoc.impl.JSDocCommentImpl
import com.intellij.lang.javascript.psi.resolve.JSContextResolver
import com.intellij.lang.javascript.psi.types.JSRecordTypeImpl
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.css.CssRulesetList
import com.intellij.psi.css.CssSelector
import com.intellij.psi.css.impl.CssRulesetImpl
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.impl.source.html.HtmlTagImpl
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.isAncestor
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeDecl
import com.intellij.psi.xml.XmlTag
import com.intellij.refactoring.suggested.startOffset
import kotlin.math.max


class HbsLocalReference(private val leaf: PsiElement, val resolved: Any?) : HbReference(leaf) {
    private var namedXml: EmberNamedAttribute? = null
    private var named: EmberNamedElement?

    val target: PsiElement? by lazy {
      if (resolved is PsiElement) {
          return@lazy resolved
      }
      if (resolved is JSRecordType.PropertySignature) {
        return@lazy resolved.memberSource.singleElement
      }
      return@lazy null
    }

    init {
        this.named = target?.let { EmberNamedElement(it) }
        val target = this.target
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
            val node = SimpleNodeFactory.createIdNode(leaf.project, newElementName)
            return leaf.replace(node)
        }
        return leaf
    }

    companion object {

        fun resolveToLocalJs(element: PsiElement): HbsLocalReference? {
            var tpl: Any? = null
            var f: PsiFile? = null
            if (element.originalVirtualFile is VirtualFileWindow) {
                val psiManager = PsiManager.getInstance(element.project)
                f = psiManager.findFile((element.originalVirtualFile as VirtualFileWindow).delegate)!!
                val manager = InjectedLanguageManager.getInstance(element.project)
                val templates = PsiTreeUtil.collectElements(f) { it is JSStringTemplateExpression }
                tpl = templates.find {
                    val injected = manager.findInjectedElementAt(f as PsiFile, it.startOffset+1)?.containingFile ?: return@find false
                    val virtualFile = injected.virtualFile
                    return@find virtualFile is VirtualFileWindow && virtualFile == (element.originalVirtualFile as VirtualFileWindow)
                } ?: return null
            }

            if (element.containingFile.viewProvider is com.emberjs.gts.GtsFileViewProvider) {
                val view = element.containingFile.viewProvider
                val JS = JavaScriptSupportLoader.ECMA_SCRIPT_6
                val TS = JavaScriptSupportLoader.TYPESCRIPT
                val tsView = view.getPsi(TS)
                val jsView = view.getPsi(JS)
                f = tsView ?: jsView
                tpl = view.findElementAt(element.startOffset, TS) ?: view.findElementAt(element.startOffset, JS)
            }

            if (tpl == null) {
                return null
            }

            val parts = element.text.split(".")
            var current: PsiElement? = null
            if (parts.first() == "this") {
                current = JSContextResolver.resolveThisReference(tpl as PsiElement)
            } else {
                val children = PsiTreeUtil.collectElements(f) { it is JSFunction || it is JSVariable || it is ES6ImportDeclaration || it is JSClass }
                current = children.mapNotNull {
                    if (it is JSVariable && it.name?.equals(parts.first()) == true) {
                        val useScope = JSUseScopeProvider.getBlockScopeElement(it)
                        if (useScope.isAncestor(tpl as PsiElement)) {
                            return@mapNotNull it
                        }
                    }

                    if (it is JSFunction && it.name?.equals(parts.first()) == true && it.parent !is JSVariable) {
                        val useScope = JSUseScopeProvider.getBlockScopeElement(it)
                        if (useScope.isAncestor(tpl as PsiElement)) {
                            return@mapNotNull it
                        }
                    }

                    if (it is JSClass && it.name?.equals(parts.first()) == true) {
                        val useScope = JSUseScopeProvider.getBlockScopeElement(it)
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
            return HbsLocalReference(element, current)
        }

        fun resolveToJs(any: Any?, path: List<String>, resolveIncomplete: Boolean = false, recursionCounter: Int = 0): Any? {

            if (recursionCounter > 10) {
                throw Error("resolveToJs reached recursion limit")
            }

            if (any is EmberNamedElement) {
                return resolveToJs(any.target, path, resolveIncomplete, recursionCounter + 1)
            }

            if (any is PsiFile) {
                val styleSheetLanguages = arrayOf("sass", "scss", "less")
                if (styleSheetLanguages.contains(any.language.id.lowercase())) {
                    PsiTreeUtil.collectElements(any) { it is CssRulesetList }.first().children.forEach { (it as? CssRulesetImpl)?.selectors?.forEach {
                        if (it.text == "." + path.first()) {
                            return it
                        }
                    }}
                }
            }

            if (any is CssSelector && any.ruleset?.block != null) {
                any.ruleset!!.block!!.children.mapNotNull { it as? CssRulesetImpl }.forEach{ it.selectors.forEach {
                    if (it.text == "." + path.first()) {
                        return it
                    }
                }}
            }

            if (any is PsiElement) {
                val resolvedHelper = EmberUtils.handleEmberHelpers(any)
                if (resolvedHelper != null) {
                    return resolveToJs(resolvedHelper, path, resolveIncomplete, recursionCounter + 1)
                }

                val refYield = EmberUtils.findTagYieldAttribute(any)
                if (refYield != null && refYield.declaration != null) {
                    return resolveToJs(refYield.declaration, path, resolveIncomplete, recursionCounter + 1)
                }
            }

            if (any is PsiElement && any.references.find { it is HbReference } != null) {
                val ref = any.references.find { it is HbReference }
                val res = ref?.resolve()
                if (res == any) {
                    return null
                }
                return resolveToJs(res, path, resolveIncomplete, recursionCounter + 1)
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
                            return resolveToJs(lastId, path.subList(1, max(path.lastIndex, 1)), resolveIncomplete, recursionCounter + 1) ?: res
                        }
                        val ref = PsiTreeUtil.collectElements(res, { it.references.find { it is HbReference } != null }).firstOrNull()
                        if (ref != null) {
                            val hbsRef = ref.references.find { it is HbReference }!!
                            return resolveToJs(hbsRef.resolve(), path.subList(1, max(path.lastIndex, 1)), resolveIncomplete, recursionCounter + 1)
                        }
                        return res
                    }
                }
                if (any.children[0] is HbMustacheName) {
                    val lastId = any.children[0].children[0].children.findLast { it.elementType == HbTokenTypes.ID } // lookup id ref (something.x)
                            ?: any.children[0].children[1].children.findLast { it.elementType == HbTokenTypes.ID } // lookup data if ref (@something.x)
                    return resolveToJs(lastId, path, resolveIncomplete, recursionCounter + 1)
                }
            }

            if (any is PsiFile) {
                val helper = EmberUtils.resolveHelper(any)
                if (helper != null) {
                    return resolveToJs(helper, path, resolveIncomplete, recursionCounter + 1)
                }
                val modifier = EmberUtils.resolveDefaultModifier(any)
                if (modifier != null) {
                    return modifier
                }
                return EmberUtils.resolveDefaultExport(any)
            }

            if (path.isEmpty()) {
                return any
            }

            if (any is JSRecordTypeImpl.PropertySignatureImpl) {
                if (path.isEmpty()) {
                    return any.jsType?.sourceElement
                }
                return resolveToJs(any.jsType, path)
            }

            var jsType: JSType? = null

            if (any is JSType) {
                jsType = any
            }

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
                        return resolveToJs(res, path, resolveIncomplete, recursionCounter + 1)
                    }
                }
                if (jsType is JSRecordTypeImpl && jsType.findPropertySignature(path.first()) != null) {
                    val elem = jsType.findPropertySignature(path.first())
                    return resolveToJs(elem, path.subList(1, max(path.lastIndex, 1)), resolveIncomplete, recursionCounter + 1)
                }
                jsType = EmberUtils.handleEmberProxyTypes(jsType) ?: jsType
                jsType = jsType.asRecordType()
                if (jsType is JSRecordTypeImpl) {
                    val elem = jsType.findPropertySignature(path.first())
                    return resolveToJs(elem, path.subList(1, max(path.lastIndex, 1)), resolveIncomplete, recursionCounter + 1)
                }
                if (any is JSVariableImpl<*, *> && any.doGetExplicitlyDeclaredType() != null) {
                    val jstype = any.doGetExplicitlyDeclaredType()
                    if (jstype is JSRecordTypeImpl) {
                        return resolveToJs(jstype.sourceElement, path.subList(1, max(path.lastIndex, 1)), resolveIncomplete, recursionCounter + 1)
                    }
                }
            }
            val followed = EmberUtils.followReferences(any as PsiElement?)
            if (followed !== null && followed != any) {
                return resolveToJs(followed, path, resolveIncomplete, recursionCounter + 1)
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
            val angleBracketBlocks = PsiTreeUtil.collectElements(htmlView) { it is XmlAttribute && it.text.startsWith("|") }
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
            val ref = this.findReference(element)
            if (ref?.resolve() == null ) {
                return null
            }
            return ref
        }

        fun findReference(element: PsiElement): PsiReference? {
            val psiFile = PsiManager.getInstance(element.project).findFile(element.originalVirtualFile!!)
            val document = PsiDocumentManager.getInstance(element.project).getDocument(psiFile!!)!!
            val service = GlintLanguageServiceProvider(element.project).getService(element.originalVirtualFile!!)

            val name = element.text.replace("IntellijIdeaRulezzz", "")

            if (name == "if") {
                return HbsModuleReference(element, "helper")
            }

            val closeMustache = PsiTreeUtil.collectParents(element, HbCloseBlockMustache::class.java, false) { it is HbBlockWrapper }.firstOrNull()
            if (closeMustache != null) {
                val blockWrapper = closeMustache.parent
                val openId = PsiTreeUtil.collectElements(blockWrapper) { HbsPatterns.BLOCK_MUSTACHE_NAME_ID.accepts(it) }.firstOrNull()
                return HbsLocalRenameReference(element, openId)
            }

            val sibling = PsiTreeUtil.findSiblingBackward(element, HbTokenTypes.ID, null)
            if (name == "this" && sibling == null) {
                val cls = EmberUtils.findBackingJsClass(element)
                if (cls != null) {
                    return HbsLocalReference(element, cls)
                }
            }

            // for this.x.y
            val prevSiblingIsSep = element.parent.prevSibling.elementType == HbTokenTypes.SEP ||
                    element.prevSibling.elementType == HbTokenTypes.SEP
            if (sibling != null && prevSiblingIsSep) {
                val ref = sibling.references.find { it is HbReference } as? HbReference
                val yieldRef = (ref as? HbsLocalReference)?.resolveYield()
                if (yieldRef != null) {
                    val res = resolveToJs(yieldRef, listOf(element.text))
                    return HbsLocalReference(element, res ?: service?.getNavigationFor(document, element, true)?.firstOrNull()?.parent)
                }
                val sig = (ref as? HbsLocalReference)?.resolved as? JSRecordType.PropertySignature
                if (ref != null && resolveToJs(sig ?: ref.resolve(), listOf(element.text)) != null) {
                    return HbsLocalReference(element, resolveToJs(sig ?: ref.resolve(), listOf(element.text)))
                }
                val ref2 = sibling.references.find { it is HbReference } as HbReference?
                val res = resolveToJs(ref2?.resolve(), listOf(element.text))
                return HbsLocalReference(element, res ?: service?.getNavigationFor(document, element, true)?.firstOrNull()?.parent)
            }

            if (element.parent is HbData) {
                val cls = EmberUtils.findBackingJsClass(element)
                if (cls != null) {
                    val args = EmberUtils.findComponentArgsType(cls as JSElement)
                    val prop = args?.properties?.find { it.memberName == name }
                    if (prop != null) {
                        return HbsLocalReference(element, prop.memberSource.singleElement)
                    }
                    if (element.text == "model") {
                        val modelProp = (cls as? JSClass)?.let { it.jsType.asRecordType().properties.find { it.memberName == "model" } }
                        return modelProp?.let { HbsLocalReference(element, it.memberSource.singleElement) }
                    }
                    val resolved = service?.getNavigationFor(document, element, true)?.firstOrNull()?.parent
                    return resolved?.let { HbsLocalReference(element, it) }
                }
            }

            val helperElement = EmberUtils.findFirstHbsParamFromParam(element.parent)
            if (element.parent is HbHash && helperElement != null) {
                val map = EmberUtils.getArgsAndPositionals(helperElement)
                val i = map.named.indexOf((element.parent as HbHash).hashName)
                val v = map.namedRefs.getOrNull(i)
                return HbsLocalReference(element, v)
            }

            val importRef = EmberUtils.referenceImports(element, name)
            if (importRef != null) {
                return HbsLocalRenameReference(element, importRef)
            }

            if (PsiTreeUtil.findSiblingBackward(element, HbTokenTypes.OPEN_BLOCK_PARAMS, null) != null) {
                return HbsLocalRenameReference(element, element)
            }

            return referenceBlocks(element, name)
                    ?: resolveToLocalJs(element)
                    ?: let {
                        val resolved = service?.getNavigationFor(document, element, true)?.firstOrNull()?.parent
                        resolved?.let { HbsLocalReference(element, it) }
                    }
        }
    }
}
