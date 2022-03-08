package com.emberjs.utils

import com.dmarcotte.handlebars.parsing.HbTokenTypes
import com.dmarcotte.handlebars.psi.*
import com.dmarcotte.handlebars.psi.impl.HbDataImpl
import com.dmarcotte.handlebars.psi.impl.HbPathImpl
import com.emberjs.*
import com.emberjs.hbs.HbsLocalReference
import com.emberjs.hbs.HbsModuleReference
import com.emberjs.hbs.ImportNameReferences
import com.emberjs.hbs.TagReferencesProvider
import com.emberjs.psi.EmberNamedElement
import com.emberjs.resolver.EmberJSModuleReference
import com.intellij.lang.Language
import com.intellij.lang.ecmascript6.psi.ES6ImportExportDeclaration
import com.intellij.lang.ecmascript6.psi.ES6ImportedBinding
import com.intellij.lang.ecmascript6.resolve.ES6PsiUtil
import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.ecma6.ES6TaggedTemplateExpression
import com.intellij.lang.javascript.psi.ecma6.JSStringTemplateExpression
import com.intellij.lang.javascript.psi.ecma6.TypeScriptField
import com.intellij.lang.javascript.psi.ecma6.TypeScriptTypeArgumentList
import com.intellij.lang.javascript.psi.ecma6.impl.TypeScriptClassImpl
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.jsdoc.JSDocComment
import com.intellij.lang.javascript.psi.types.JSArrayType
import com.intellij.psi.*
import com.intellij.psi.impl.file.PsiDirectoryImpl
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parents
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag

class ArgData(
        var value: String = "",
        var description: String? = null,
        var reference: AttrPsiReference? = null) {}

class ComponentReferenceData(
        val hasSplattributes: Boolean = false,
        val yields: MutableList<EmberXmlElementDescriptor.YieldReference> = mutableListOf(),
        val args: MutableList<ArgData> = mutableListOf()
) {

}


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
                exp = ES6PsiUtil.resolveDefaultExport(exportImport).firstOrNull() as? JSElement? ?: exp
                if (exportImport.fromClause?.text?.endsWith("/template\"") == true) {
                    val hbs = exportImport.fromClause?.references?.find { it.resolve() is HbPsiFile }?.resolve() as? HbPsiFile
                    if (hbs != null) {
                        return hbs
                    }
                }
                exp = exp ?: exportImport.fromClause?.references?.findLast { it.resolve() != null }?.resolve() as? JSElement
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

        fun resolveToEmber(file: PsiFile): PsiElement {
            return resolveComponent(file)
                    ?: resolveHelper(file)
                    ?: resolveDefaultModifier(file)
                    ?: resolveDefaultExport(file)
                    ?: file
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
            val typeList: TypeScriptTypeArgumentList?
            if (cls is TypeScriptClassImpl) {
                typeList = cls.extendsList?.children?.first()?.children?.getOrNull(1) as TypeScriptTypeArgumentList?
            } else {
                typeList = PsiTreeUtil.findChildOfType(cls, TypeScriptTypeArgumentList::class.java)
            }
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
                val resolutions = reference.multiResolve(false)
                element = resolutions.find { (it.element as PsiFileSystemItem).virtualFile.path.endsWith("/$name.ts") }?.element
                            ?: resolutions.find { (it.element as PsiFileSystemItem).virtualFile.path.endsWith("/$name.js") }?.element
                            ?: resolutions.find { (it.element as PsiFileSystemItem).virtualFile.path.endsWith("/$name") }?.element
            }
            return element
        }

        fun followReferences(element: PsiElement?, path: String? = null): PsiElement? {

            if (element is ES6ImportedBinding) {
                val ref = element.declaration?.fromClause?.references?.find { it is EmberJSModuleReference } as EmberJSModuleReference
                return followReferences(ref.fileReferenceSet.lastReference?.multiResolve(false)?.filterNotNull()?.firstOrNull()?.element)
            }

            if (element is EmberNamedElement) {
                return followReferences(element.target, path)
            }

            val resHelper = handleEmberHelpers(element)
            if (resHelper != null) {
                return followReferences(resHelper, path)
            }

            if (element is HbParam) {
                if (element.children.isNotEmpty() && element.children[0].references.isNotEmpty()) {
                    return element.children.getOrNull(0)?.let { followReferences(it) }
                }
                return element.children.getOrNull(0)?.children?.getOrNull(0)?.children?.getOrNull(0)?.let { followReferences(it) } ?: element
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
            val parent = psiElement?.parents(false)
                    ?.find { it.children.getOrNull(0)?.elementType == HbTokenTypes.OPEN_SEXPR }
                    ?:
                    psiElement?.parents(false)
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
            val insideImport = element.parents(false).find { it is HbMustache && it.children.getOrNull(1)?.text == "import"} != null

            if (insideImport && element.text != "from" && element.text != "import") {
                return null
            }
            val hbsView = element.containingFile.viewProvider.getPsi(Language.findLanguageByID("Handlebars")!!)
            val imports = PsiTreeUtil.collectElements(hbsView, { it is HbMustache && it.children[1].text == "import" })
            val ref = imports.find {
                val names = it.children[2].text.replace("'", "").replace("\"", "").split(",")
                val named = names.map {
                    if (it.contains(" as ")) {
                        it.split(" as ").last()
                    } else {
                        it
                    }
                }.map { it.replace(" ", "") }
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
            if (element is PsiElement && element.text.contains(Regex("^(\\(|\\{\\{)component\\b"))) {
                val idx = element.children.indexOfFirst { it.text == "component" }
                val param = element.children.get(idx + 1)
                if (param.children.firstOrNull()?.children?.firstOrNull() is HbStringLiteral) {
                    return TagReferencesProvider.forTagName(param.project, param.text.dropLast(1).drop(1).camelize())
                }
                return param
            }
            if (element is PsiElement && element.text.contains(Regex("^(\\(|\\{\\{)or\\b"))) {
                return element.children.find { it is HbParam && it.text != "or" && it.children[0].children[0].references.isNotEmpty() } ?:
                element.children.find { it is HbParam && it.children[0].children[0] is HbStringLiteral && it.parent.parent.text.contains(Regex("^(\\(|\\{\\{)component\\b")) }?.let { TagReferencesProvider.forTagName(it.project, it.text.dropLast(1).drop(1).camelize()) }
            }
            if (element is PsiElement && element.parent is HbOpenBlockMustache) {
                val mustacheName = element.parent.children.find { it is HbMustacheName }?.text
                val helpers = arrayOf("let", "each", "with", "component")
                if (helpers.contains(mustacheName)) {
                    val param = PsiTreeUtil.findSiblingBackward(element, HbTokenTypes.PARAM, null)
                    if (param == null) {
                        return null
                    }
                    if (mustacheName == "let" || mustacheName == "with" || mustacheName == "component") {
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

        fun findTagYield2(element: PsiElement?): PsiElement? {
            var tag: XmlTag? = null
            var name: String? = null
            if (element is EmberAttrDec && element.name != "as") {
                tag = element.parent
                name = element.name
            }
            if (element is XmlAttribute && element.name != "as") {
                tag = element.parent
                name = element.name
            }
            if (tag != null) {
                val asAttr = tag.attributes.find { it.name == "as" }!!
                val yields = tag.attributes.map { it.text }.joinToString(" ").split("|")[1]
                val idx = yields.split(" ").indexOf(name)
                val ref = asAttr.references.find { it is HbsLocalReference }
                if (ref is HbPathImpl) {
                    val params = ref.children.filter { it is HbParam }
                    return params.getOrNull(idx)
                }
            }

            return null
        }

        fun getFileByPath(directory: PsiDirectory?, path: String): PsiFile? {
            if (directory == null) return null
            var dir: PsiDirectory = directory
            val parts = path.split("/").toMutableList()
            while (parts.isNotEmpty()) {
                val p = parts.removeAt(0)
                val d: Any? = dir.findSubdirectory(p) ?: dir.findFile(p)
                if (d is PsiFile) {
                    return d
                }
                if (d is PsiDirectory) {
                    dir = d
                    continue
                }
                return null
            }
            return null
        }

        fun getComponentReferenceData(f: PsiElement): ComponentReferenceData {
            val file = resolveDefaultExport(f.containingFile)?.containingFile ?: f
            var name = file.containingFile.name.split(".").first()
            val dir = file.containingFile.parent as PsiDirectoryImpl?
            var template: PsiFile? = null
            var path = ""
            var parentModule: PsiDirectory? = file.containingFile.parent
            val tplArgs = emptyArray<ArgData>().toMutableList()
            val tplYields = mutableListOf<EmberXmlElementDescriptor.YieldReference>()

            if (name == "template") {
                name = "component"
            }

            val fullPathToTs = "$name.ts"
            val fullPathToDts = "$name.d.ts"
            var containingFile = file.containingFile
            if (containingFile == null) {
                return ComponentReferenceData()
            }
            if (containingFile.name.endsWith(".js")) {
                containingFile = dir?.findFile(containingFile.name.replace(".js", ".d.ts"))
                        ?: containingFile
            }

            val tsFile = getFileByPath(parentModule, fullPathToTs) ?: getFileByPath(parentModule, fullPathToDts) ?: containingFile
            val cls = findDefaultExportClass(tsFile)
                    ?: findDefaultExportClass(containingFile)
                    ?: file

            var jsTemplate: Any? = null;
            if (cls is JSElement) {
                val argsElem = findComponentArgsType(cls)
                val signatures = argsElem?.properties ?: emptyList()
                for (sign in signatures) {
                    val comment = sign.memberSource.singleElement?.children?.find { it is JSDocComment }
//                val s: TypeScriptSingleTypeImpl? = sign.children.find { it is TypeScriptSingleTypeImpl } as TypeScriptSingleTypeImpl?
                    val attr = sign.toString().split(":").last()
                    val data = tplArgs.find { it.value == attr } ?: ArgData()
                    data.value = attr
                    data.reference = AttrPsiReference(sign.memberSource.singleElement!!)
                    data.description = comment?.text ?: ""
                    if (tplArgs.find { it.value == attr } == null) {
                        tplArgs.add(data)
                    }
                }

                if (cls is JSClass) {
                    jsTemplate = cls.fields.find { it.name == "layout" }
                    if (jsTemplate is TypeScriptField) {
                        jsTemplate = jsTemplate.initializer

                    }
                    if (jsTemplate is ES6TaggedTemplateExpression) {
                        jsTemplate = jsTemplate.templateExpression
                    }
                    if (jsTemplate is JSStringTemplateExpression) {
                        jsTemplate = jsTemplate.text
                    }

                    if (jsTemplate is JSLiteralExpression) {
                        jsTemplate = jsTemplate.text
                    }

                    if (jsTemplate is String) {
                        jsTemplate = jsTemplate.substring(1, jsTemplate.lastIndex)
                        jsTemplate = PsiFileFactory.getInstance(file.project).createFileFromText("$name-virtual", Language.findLanguageByID("Handlebars")!!, jsTemplate)
                    }
                }
            }

            if (dir != null || jsTemplate != null) {
                // co-located
                if (name == "component") {
                    name = "template"
                }
                template = dir?.findFile("$name.hbs")

                val parents = emptyList<PsiFileSystemItem>().toMutableList()
                var fileItem: PsiFileSystemItem? = containingFile
                while (fileItem != null) {
                    if (fileItem is PsiDirectory) {
                        parents.add(fileItem)
                    }
                    fileItem = fileItem.parent
                }
                parentModule = parents.find { it is PsiDirectory && it.virtualFile == file.originalVirtualFile?.parentEmberModule} as PsiDirectory?
                path = parents
                        .takeWhile { it != parentModule }
                        .toList()
                        .reversed()
                        .map { it.name }
                        .joinToString("/")

                val fullPathToHbs = path.replace("app/", "addon/") + "/$name.hbs"

                template = template
                        ?: getFileByPath(parentModule, fullPathToHbs)
                                ?: getFileByPath(parentModule, fullPathToHbs.replace("/components/", "/templates/components/"))
                                ?: jsTemplate as PsiFile?


                if (template?.node?.psi != null) {
                    val args = PsiTreeUtil.collectElementsOfType(template.node.psi, HbDataImpl::class.java)
                    for (arg in args) {
                        val argName = arg.text.split(".").first()
                        if (tplArgs.find { it.value == argName } == null) {
                            tplArgs.add(ArgData(argName, "", AttrPsiReference(arg)))
                        }
                    }

                    val yields = PsiTreeUtil.collectElements(template.node.psi, { it is HbPathImpl && it.text == "yield" })
                    for (y in yields) {
                        tplYields.add(EmberXmlElementDescriptor.YieldReference(y))
                    }
                }
            }

            val hasSplattributes = template?.text?.contains("...attributes") ?: false
            return ComponentReferenceData(hasSplattributes, tplYields, tplArgs)
        }
    }
}
