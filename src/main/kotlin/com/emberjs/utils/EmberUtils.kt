package com.emberjs.utils

import com.dmarcotte.handlebars.parsing.HbTokenTypes
import com.dmarcotte.handlebars.psi.*
import com.emberjs.EmberAttrDec
import com.emberjs.hbs.HbsLocalReference
import com.emberjs.hbs.HbsModuleReference
import com.emberjs.hbs.ImportNameReferences
import com.emberjs.psi.EmberNamedElement
import com.intellij.lang.Language
import com.intellij.lang.ecmascript6.psi.ES6ImportExportDeclaration
import com.intellij.lang.ecmascript6.resolve.ES6PsiUtil
import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeArgumentList
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.types.JSArrayType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parents
import com.intellij.psi.xml.XmlAttribute


class EmberUtils {
    companion object {

        fun resolveModifier(file: PsiFile): Array<JSFunction?> {
            val func = resolveDefaultExport(file)
            val installer: JSFunction? = PsiTreeUtil.collectElements(func) { it is JSFunction && it.name == "installModifier" }.firstOrNull() as JSFunction?
            val updater: JSFunction? = PsiTreeUtil.collectElements(func, { it is JSFunction && it.name == "updateModifier" }).firstOrNull() as JSFunction?
            val destroyer: JSFunction? = PsiTreeUtil.collectElements(func, { it is JSFunction && it.name == "destroyModifier" }).firstOrNull() as JSFunction?
            return arrayOf(installer, updater, destroyer)
        }

        fun resolveDefaultModifier(file: PsiFile): JSFunction? {
            val modifier = resolveModifier(file)
            val args = modifier.first()?.parameters?.getOrNull(2)
                    ?: modifier[1]?.parameters?.getOrNull(1)
                    ?: modifier[2]?.parameters?.getOrNull(1)
            return modifier.find { it != null && it.parameters.contains(args) }
        }

        fun resolveDefaultExport(file: PsiFile): PsiElement? {
            var exp = ES6PsiUtil.findDefaultExport(file)
            val exportImport = PsiTreeUtil.findChildOfType(file, ES6ImportExportDeclaration::class.java)
            if (exportImport != null && exportImport.children.find { it.text == "default" } != null) {
                exp = ES6PsiUtil.resolveDefaultExport(exportImport).firstOrNull() as JSElement? ?: exp
                exp = exp ?: exportImport.fromClause?.references?.findLast { it.resolve() != null }?.resolve() as JSElement
            }
            var ref: Any? = exp?.children?.find { it is JSReferenceExpression } as JSReferenceExpression?
            while (ref is JSReferenceExpression && ref.resolve() != null) {
                ref = ref.resolve()
            }

            if (ref is JSClass || ref is JSCallExpression || ref is JSObjectLiteralExpression) {
                return ref as PsiElement
            }

            ref = ref as JSElement? ?: exp
            val func = ref?.children?.find { it is JSCallExpression }
            if (func is JSCallExpression) {
                return func
            }
            val cls = ref?.children?.find { it is JSClass }
            if (cls is JSClass) {
                return cls
            }
            val obj = ref?.children?.find { it is JSObjectLiteralExpression }
            if (obj is JSObjectLiteralExpression) {
                return obj
            }
            return ref
        }

        fun resolveHelper(file: PsiFile): JSFunction? {
            val cls = resolveDefaultExport(file)
            if (cls is JSCallExpression && cls.argumentList != null) {
                var func: PsiElement? = cls.argumentList!!.arguments.last()
                while (func is JSReferenceExpression) {
                    func = func.resolve()
                }
                if (func is JSFunction) {
                    return func
                }
            }
            return null
        }

        fun resolveComponent(file: PsiFile): PsiElement? {
            val cls = resolveDefaultExport(file)
            if (cls is JSClass) {
                return cls
            }
            return null
        }

        fun resolveToEmber(file: PsiFile): PsiElement? {
            return resolveComponent(file) ?: resolveHelper(file) ?: resolveDefaultModifier(file) ?: file
        }

        fun findHelperParams(file: PsiFile): Array<JSParameterListElement>? {
            return resolveHelper(file)?.parameters
        }


        fun findDefaultExportClass(file: PsiFile): JSClass? {
            val exp = ES6PsiUtil.findDefaultExport(file)
            var cls: Any? = exp?.children?.find { it is JSClass }
            cls = cls ?: exp?.children?.find { it is JSFunction }
            if (cls == null) {
                val ref: JSReferenceExpression? = exp?.children?.find { it is JSReferenceExpression } as JSReferenceExpression?
                cls = ref?.resolve()
                if (cls is JSClass) {
                    return cls
                }
                cls = PsiTreeUtil.findChildOfType(ref?.resolve(), JSClass::class.java)
                return cls
            }
            return cls as JSClass?
        }


        fun findComponentArgsType(cls: JSElement): JSRecordType? {
            val typeList = PsiTreeUtil.findChildOfType(cls, TypeScriptTypeArgumentList::class.java)
            val type = typeList?.typeArguments?.first()?.calculateType()
            val recordType = type?.asRecordType()
            return recordType
        }


        fun resolveReference(reference: PsiReference?, path: String?): PsiElement? {
            var element = reference?.resolve()
            if (element == null && reference is HbsModuleReference) {
                element = reference.multiResolve(false).firstOrNull()?.element
            }
            if (reference is ImportNameReferences) {
                var name = path
                val hasAs = reference.element.text.contains(" as ")
                if (hasAs) {
                    val nameAs = reference.element.text.split(",").find { it.split(" as ").first() == path }
                    name = nameAs?.split(" as ")?.last()
                }
                element = reference.multiResolve(false).find { (it.element as PsiFileSystemItem).virtualFile.path.endsWith("/$name") }?.element
            }
            return element
        }

        fun followReferences(element: PsiElement?, path: String? = null): PsiElement? {

            if (element is HbParam) {
                return element.children.getOrNull(0)?.children?.getOrNull(0)?.children?.getOrNull(0)?.let { followReferences(it) } ?: element

            }

            if (element is EmberNamedElement) {
                return followReferences(element.target, path)
            }

            val resHelper = handleEmberHelpers(element)
            if (resHelper != null) {
                return followReferences(resHelper, path)
            }

            val resYield = findTagYield(element)
            if (resYield != null) {
                return followReferences(resYield, path)
            }

            if (element?.references != null && element.references.isNotEmpty()) {
                val res = element.references.map { resolveReference(it, path) }
                        .filterNotNull()
                        .firstOrNull()
                if (res == element) {
                    return res
                }
                return followReferences(res, path)
            }
            return element
        }


        fun findFirstHbsParamFromParam(psiElement: PsiElement?): PsiElement? {
            val parent = psiElement?.parents
                    ?.find { it.children.getOrNull(0)?.elementType == HbTokenTypes.OPEN_SEXPR }
                    ?:
                    psiElement?.parents
                            ?.find { it.children.getOrNull(0)?.elementType == HbTokenTypes.OPEN }
            if (parent == null) {
                return null
            }
            // mustache name
            val name = parent.children.getOrNull(1)
            if (name?.references != null && name.references.isNotEmpty()) {
                return name
            }
            return parent.children.getOrNull(1)
        }


        fun referenceImports(element: PsiElement, name: String): PsiElement? {
            val insideImport = element.parents.find { it is HbMustache && it.children.getOrNull(1)?.text == "import"} != null

            if (insideImport && element.text != "from" && element.text != "import") {
                return null
            }
            val hbsView = element.containingFile.viewProvider.getPsi(Language.findLanguageByID("Handlebars")!!)
            val imports = PsiTreeUtil.collectElements(hbsView, { it is HbMustache && it.children[1].text == "import" })
            val ref = imports.find {
                val names = it.children[2].text.split(",")
                val named = names.map {
                    if (it.contains(" as ")) {
                        it.split(" as ").last()
                    } else {
                        it
                    }
                }
                named.contains(name)
            }
            if (ref == null) {
                return null
            }
            val index = Regex("\\b$name\\b").find(ref.children[2].text)!!.range.first
            val file = element.containingFile.findReferenceAt(ref.children[2].textOffset + index)
            return file?.resolve() ?: ref
        }

        fun handleEmberHelpers(element: PsiElement?): PsiElement? {
            if (element is PsiElement && element.parent is HbOpenBlockMustache) {
                val mustacheName = element.parent.children.find { it is HbMustacheName }?.text
                val helpers = arrayOf("let", "each", "with")
                if (helpers.contains(mustacheName)) {
                    val param = PsiTreeUtil.findSiblingBackward(element, HbTokenTypes.PARAM, null)
                    if (param == null) {
                        return null
                    }
                    if (mustacheName == "let" || mustacheName == "with") {
                        return param
                    }
                    if (mustacheName == "each") {
                        val refResolved = param.references.firstOrNull()?.resolve()
                                ?:
                                PsiTreeUtil.collectElements(param, { it.elementType == HbTokenTypes.ID })
                                        .filter { it !is LeafPsiElement }
                                        .lastOrNull()?.references?.firstOrNull()?.resolve()
                        if (refResolved is HbPsiElement) {
                            return param.parent
                        }
                        val jsRef = HbsLocalReference.resolveToJs(refResolved, emptyList(), false)
                        if (jsRef is JSTypeOwner && jsRef.jsType is JSArrayType) {
                            return (jsRef.jsType as JSArrayType).type?.sourceElement
                        }
                    }
                }
            }
            return null
        }

        fun findTagYield(element: PsiElement?): XmlAttribute? {
            if (element is EmberAttrDec && element.name != "as") {
                val tag = element.parent
                return tag.attributes.find { it.name == "as" }
            }
            if (element is XmlAttribute && element.name != "as") {
                val tag = element.parent
                return tag.attributes.find { it.name == "as" }
            }
            return null
        }
    }
}
