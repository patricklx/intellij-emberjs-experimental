package com.emberjs.hbs

import com.dmarcotte.handlebars.parsing.HbTokenTypes
import com.dmarcotte.handlebars.psi.*
import com.dmarcotte.handlebars.psi.impl.HbOpenBlockMustacheImpl
import com.dmarcotte.handlebars.psi.impl.HbStatementsImpl
import com.emberjs.EmberAttrDec
import com.emberjs.psi.EmberNamedAttribute
import com.emberjs.psi.EmberNamedElement
import com.emberjs.refactoring.SimpleNodeFactory
import com.emberjs.utils.EmberUtils
import com.emberjs.utils.originalVirtualFile
import com.emberjs.utils.parents
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.Language
import com.intellij.lang.ecmascript6.resolve.ES6PsiUtil
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSType
import com.intellij.lang.javascript.psi.JSTypeOwner
import com.intellij.lang.javascript.psi.ecma6.JSTypedEntity
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.impl.JSVariableImpl
import com.intellij.lang.javascript.psi.types.JSRecordTypeImpl
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.impl.source.html.HtmlTagImpl
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeDecl
import com.intellij.psi.xml.XmlTag
import org.mozilla.javascript.annotations.JSGetter
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
            val r = blockParams.find { it.text.matches(Regex("^\\|*$name\\|*$")) }
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

            if (any is PsiElement) {
                val resolvedHelper = EmberUtils.handleEmberHelpers(any)
                if (resolvedHelper != null) {
                    return resolveToJs(resolvedHelper, path)
                }

                val refYield = EmberUtils.findTagYield(any)
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
                        val ref = PsiTreeUtil.collectElements(res, { it.references.find { it is HbsLocalReference } != null }).firstOrNull()
                        if (ref != null) {
                            val hbsRef = ref.references.find { it is HbsLocalReference }!!
                            return resolveToJs(hbsRef.resolve(), path.subList(1, max(path.lastIndex, 1)), resolveIncomplete)
                        }
                        return res
                    }
                }
                if (any.children[0] is HbMustacheName) {
                    val lastId = any.children[0].children[0].children.findLast { it.elementType == HbTokenTypes.ID }
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

            if (any is JSClass) {
                val n = path.first()
                val f = any.fields.find { it.name == n } ?: any.functions.find { it.name == n }
                if (f == null && resolveIncomplete) {
                    return any;
                }
                return resolveToJs(f, path.subList(1, max(path.lastIndex, 1)), resolveIncomplete)
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
            return null
        }

        fun referenceBlocks(element: PsiElement, name: String): PsiReference? {
            // any |block param|
            // as mustache
            val hbblockRefs = PsiTreeUtil.collectElements(element.containingFile, { it is HbOpenBlockMustacheImpl })
                    .filter {
                        PsiTreeUtil.collectElements(PsiTreeUtil.getNextSiblingOfType(it, HbStatementsImpl::class.java), { it == element }).isNotEmpty()
                    }

            // as html tag
            val htmlView = element.containingFile.viewProvider.getPsi(Language.findLanguageByID("HTML")!!)
            val angleBracketBlocks = PsiTreeUtil.collectElements(htmlView, { it is XmlAttribute && it.text.startsWith("|") })
                    .filter{ (it.parent as HtmlTag).attributes.map { it.text }.joinToString(" ").contains(Regex("\\|.*$name.*\\|")) }
                    .map { it.parent }

            // validate if the element is a child of the tag
            val validBlock = angleBracketBlocks.filter { it ->
                it.textRange.contains(element.textRange)
            }.firstOrNull()

            val blockRef = hbblockRefs.find { it.text.contains(Regex("\\|.*$name.*\\|")) }
            val blockVal = blockRef?.children?.filter { it.elementType == HbTokenTypes.ID }?.find { it.text == name }


            if (blockRef != null || blockVal != null || validBlock != null) {
                if (validBlock != null) {
                    val tag  = validBlock as HtmlTagImpl
                    val index = tag.attributes.indexOfFirst { it.text == "as" }
                    val blockParams = tag.attributes.toList().subList(index + 1, tag.attributes.size)
                    val r = blockParams.find { it.text.matches(Regex("^\\|*$name\\|*$")) }
                    return r?.let { HbsLocalReference(element, it) }
                }
                return HbsLocalReference(element, blockVal ?: blockRef)
            }
            return null
        }

        fun createReference(element: PsiElement): PsiReference? {
            val name = element.text.replace("IntellijIdeaRulezzz", "")
            val sibling = PsiTreeUtil.findSiblingBackward(element, HbTokenTypes.ID, null)
            if (name == "this" && sibling == null) {
                val fname = element.containingFile.name.split(".").first()
                var fileName = fname
                if (fileName == "template") {
                    fileName = "component"
                }
                val dir = element.containingFile.originalFile.containingDirectory
                val file = dir?.findFile("$fileName.ts")
                        ?: dir?.findFile("$fileName.js")
                        ?: dir?.findFile("controller.ts")
                        ?: dir?.findFile("controller.js")
                if (file != null) {
                    return HbsLocalReference(element, resolveToJs(file, listOf()))
                }
            }

            val importRef = EmberUtils.referenceImports(element, name)
            if (importRef != null) {
                return HbsLocalRenameReference(element, importRef)
            }

            // for this.x.y
            if (sibling != null && sibling.references.find { it is HbsLocalReference } != null) {
                val ref = sibling.references.find { it is HbsLocalReference } as HbsLocalReference
                val yieldRef = ref.resolveYield()
                if (yieldRef != null) {
                    return HbsLocalReference(element, resolveToJs(yieldRef, listOf(element.text)))
                }
                return HbsLocalReference(element, resolveToJs(ref.resolve(), listOf(element.text)))
            }

            if (element.parent is HbOpenBlockMustache) {
                return HbsLocalRenameReference(element, element)
            }

            return referenceBlocks(element, name) ?:
                resolveToLocalJs(element)
        }
    }
}
