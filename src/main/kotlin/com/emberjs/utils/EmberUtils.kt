package com.emberjs.utils

import com.dmarcotte.handlebars.parsing.HbTokenTypes
import com.dmarcotte.handlebars.psi.*
import com.dmarcotte.handlebars.psi.impl.HbDataImpl
import com.dmarcotte.handlebars.psi.impl.HbPathImpl
import com.emberjs.cli.EmberCliFrameworkDetector
import com.emberjs.gts.GtsFileViewProvider
import com.emberjs.hbs.HbReference
import com.emberjs.hbs.HbsLocalReference
import com.emberjs.hbs.HbsModuleReference
import com.emberjs.hbs.ImportNameReferences
import com.emberjs.index.EmberNameIndex
import com.emberjs.navigation.EmberGotoRelatedProvider
import com.emberjs.psi.EmberNamedElement
import com.emberjs.resolver.EmberJSModuleReference
import com.emberjs.xml.AttrPsiReference
import com.emberjs.xml.EmberAttrDec
import com.emberjs.xml.EmberXmlElementDescriptor
import com.intellij.framework.detection.impl.FrameworkDetectionManager
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.javascript.JSModuleBaseReference
import com.intellij.lang.Language
import com.intellij.lang.ecmascript6.psi.ES6ImportExportDeclaration
import com.intellij.lang.ecmascript6.psi.ES6ImportedBinding
import com.intellij.lang.ecmascript6.resolve.ES6PsiUtil
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.javascript.frameworks.modules.JSFileModuleReference
import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.ecma6.*
import com.intellij.lang.javascript.psi.ecma6.impl.TypeScriptClassImpl
import com.intellij.lang.javascript.psi.ecma6.impl.TypeScriptTupleTypeImpl
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.jsdoc.JSDocComment
import com.intellij.lang.javascript.psi.types.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.*
import com.intellij.psi.impl.file.PsiDirectoryImpl
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parents
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.intellij.refactoring.suggested.startOffset

class ArgData(
        var value: String = "",
        var description: String? = null,
        var reference: AttrPsiReference? = null) {}

class ComponentReferenceData(
        val hasSplattributes: Boolean = false,
        val yields: MutableList<EmberXmlElementDescriptor.YieldReference> = mutableListOf(),
        val args: MutableList<ArgData> = mutableListOf(),
        val template: PsiFile? = null,
        val component: PsiFile? = null
) {

}

open class PsiElementDelegate<T: PsiElement>(val element: T) : PsiElement by element {}

class Helper(element: JSFunction) : PsiElementDelegate<JSFunction>(element) {}
class Modifier(element: JSFunction) : PsiElementDelegate<JSFunction>(element) {}


class EmberUtils {
    companion object {

        fun isEmber(project: Project): Boolean {
            return project.guessProjectDir()?.isEmber ?: false
        }
        fun isEnabledEmberProject(project: Project): Boolean {
            return FrameworkDetectionManager.getInstance(project).detectedFrameworks.find { it.detector is EmberCliFrameworkDetector && (it.detector as EmberCliFrameworkDetector).isConfigured(it.relatedFiles, project) } != null
        }

        fun resolveModifier(file: PsiElement?): Array<JSFunction?> {
            val func = (file as? PsiFile)?.let { resolveDefaultExport(it) } ?: file
            val installer: JSFunction? = PsiTreeUtil.collectElements(func) { it is JSFunction && it.name == "installModifier" }.firstOrNull() as JSFunction?
            val updater: JSFunction? = PsiTreeUtil.collectElements(func, { it is JSFunction && it.name == "updateModifier" }).firstOrNull() as JSFunction?
            val destroyer: JSFunction? = PsiTreeUtil.collectElements(func, { it is JSFunction && it.name == "destroyModifier" }).firstOrNull() as JSFunction?
            return arrayOf(installer, updater, destroyer)
        }

        fun resolveDefaultModifier(file: PsiElement?): Modifier? {
            val modifier = resolveModifier(file)
            val args = modifier.first()?.parameters?.getOrNull(2)
                    ?: modifier[1]?.parameters?.getOrNull(1)
                    ?: modifier[2]?.parameters?.getOrNull(1)
            return modifier.find { it != null && it.parameters.contains(args) }?.let { Modifier(it) }
        }

        fun resolveDefaultExport(file: PsiElement?): PsiElement? {
            if (file == null) return null
            var exp: PsiElement? = ES6PsiUtil.findDefaultExport(file)
            val exportImport = PsiTreeUtil.findChildOfType(file, ES6ImportExportDeclaration::class.java)
            if (exportImport != null && exportImport.children.find { it.text == "default" } != null) {
                exp = ES6PsiUtil.resolveDefaultExport(exportImport).firstOrNull() as? JSElement? ?: exp
                if (exportImport.fromClause?.text?.endsWith("/template\"") == true) {
                    val hbs = exportImport.fromClause?.references?.find { it.resolve() is HbPsiFile }?.resolve() as? HbPsiFile
                    if (hbs != null) {
                        return hbs
                    }
                }
                val resolved = exp ?: exportImport.fromClause?.references
                        ?.map { (it as? FileReference)?.multiResolve(false)
                                ?.firstOrNull() ?: it.resolve() }
                        ?.filterNotNull()
                        ?.lastOrNull()
                if (resolved is ResolveResult) {
                    exp = resolved.element
                }
                if (exp is PsiFile) {
                    return resolveDefaultExport(exp)
                }
            }
            var ref: Any? = exp?.children?.find { it is JSReferenceExpression } as JSReferenceExpression?
            while (ref is JSReferenceExpression && ref.resolve() != null) {
                ref = ref.resolve()
            }

            if (ref is JSClass || ref is JSCallExpression || ref is JSObjectLiteralExpression) {
                return ref as PsiElement
            }

            ref = ref as PsiElement? ?: exp
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

        fun resolveHelper(file: PsiElement?): Helper? {
            val cls = (file as? PsiFile)?.let { resolveDefaultExport(it) } ?: file
            if (cls is JSCallExpression && cls.argumentList != null) {
                var func: PsiElement? = cls.argumentList!!.arguments.lastOrNull()
                while (func is JSReferenceExpression) {
                    func = func.resolve()
                }
                if (func is JSFunction) {
                    return Helper(func)
                }
            }
            val computeFunc = PsiTreeUtil.collectElements(cls) { it is JSFunction && it.name == "compute" }.firstOrNull() as JSFunction?
            if (computeFunc is JSFunction) {
                return Helper(computeFunc)
            }
            return null
        }

        fun resolveTemplateExport(file: PsiFile): ES6TaggedTemplateExpression? {
            val cls = resolveDefaultExport(file)
            if (cls is JSCallExpression && cls.argumentList != null) {
                var func: PsiElement? = cls.argumentList!!.arguments.lastOrNull()
                while (func is JSReferenceExpression) {
                    func = func.resolve()
                }
                if (func is JSVariable) {
                    return func.children.find { it is ES6TaggedTemplateExpression } as ES6TaggedTemplateExpression?
                }
            }
            return PsiTreeUtil.findChildOfType(cls, ES6TaggedTemplateExpression::class.java)
        }

        fun resolveComponent(file: PsiElement?): PsiElement? {
            val cls = (file as? PsiFile)?.let { resolveDefaultExport(it) } ?: file
            if (cls is JSClass) {
                return cls
            }
            return null
        }

        fun resolveToEmber(file: PsiElement?): PsiElement? {
            return resolveHelper(file)
                    ?: resolveDefaultModifier(file)
                    ?: resolveComponent(file)
                    ?: resolveDefaultExport(file)
                    ?: file
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

        fun findBackingJsClass(element: PsiElement): PsiElement? {
            val fname = element.containingFile.name.split(".").first()
            var fileName = fname
            if (fileName == "template") {
                fileName = "component"
            }
            var dir = element.containingFile.originalFile.containingDirectory
            var cls: JSElement? = null
            if (element.containingFile.viewProvider is GtsFileViewProvider) {
                val view = element.containingFile.viewProvider
                val JS = Language.findLanguageByID("JavaScript")!!
                val TS = Language.findLanguageByID("TypeScript")!!

                val inJs = view.findElementAt(element.startOffset, TS) ?: view.findElementAt(element.startOffset, JS)
                cls = PsiTreeUtil.findFirstParent(inJs, { it is JSClass }) as JSElement?
            }
            if (element.originalVirtualFile is VirtualFileWindow) {
                val offset = (element.originalVirtualFile as VirtualFileWindow).documentWindow.hostRanges[0].startOffset
                val psiManager = PsiManager.getInstance(element.project)
                val f = psiManager.findFile((element.originalVirtualFile as VirtualFileWindow).delegate)!!
                val inJs = f.findElementAt(offset + 1)
                cls = PsiTreeUtil.findFirstParent(inJs, { it is JSClass }) as JSElement?
            }
            val file = dir?.findFile("$fileName.ts")
                    ?: dir?.findFile("$fileName.d.ts")
                    ?: dir?.findFile("$fileName.js")
                    ?: dir?.findFile("controller.ts")
                    ?: dir?.findFile("controller.js")
            val relatedItems = EmberGotoRelatedProvider().getItems(element.originalVirtualFile!!, element.project)
            val related = (relatedItems.find { it.first.type == "component" } ?:
            relatedItems.find { it.first.type == "controller" }  ?:
            relatedItems.find { it.first.type == "router" })?.second
            val relatedFile = related?.let { PsiManager.getInstance(element.project).findFile(it) }
            return cls ?: file?.let { findDefaultExportClass(it) } ?: relatedFile?.let { findDefaultExportClass(it) }
        }

        fun findComponentArgsType(element: JSElement): JSRecordType? {
            var cls: PsiElement? = element
            if (cls !is TypeScriptClassImpl) {
                cls = PsiTreeUtil.findChildOfType(cls, TypeScriptClassImpl::class.java)
            }
            if (cls is TypeScriptClassImpl) {
                return cls.jsType.asRecordType().properties.find { it.memberName == "args" }?.jsType?.asRecordType()
            }
            return null
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
                var ref:EmberJSModuleReference? = element.declaration?.fromClause?.references?.findLast { it is EmberJSModuleReference && it.rangeInElement.endOffset == it.element.textLength - 1 && it.resolve() != null } as EmberJSModuleReference?
                if (ref == null) {
                    var tsFiles = element.declaration?.fromClause?.references?.mapNotNull { (it as? FileReferenceSet)?.resolve() }
                    if (tsFiles?.isEmpty() == true) {
                        tsFiles = element.declaration?.fromClause?.references?.mapNotNull { (it as? JSFileModuleReference)?.resolve() }
                    }
                    return tsFiles?.filter { it is JSFile }?.maxByOrNull { it.virtualFile.path.length }
                }

                return followReferences(ref.resolve())
            }

            if (element is EmberNamedElement) {
                return followReferences(element.target, path)
            }

            val resHelper = handleEmberHelpers(element)
            if (resHelper != null) {
                return followReferences(resHelper, path)
            }

            if (element is HbParam && element.references.isEmpty()) {
                if (element.children.isNotEmpty() && element.children[0].references.isNotEmpty()) {
                    return element.children.getOrNull(0)?.let { followReferences(it) }
                }
                return element.children.getOrNull(0)?.children?.getOrNull(0)?.children?.getOrNull(0)?.let { followReferences(it) } ?: element
            }

            val resYield: XmlAttribute? = findTagYieldAttribute(element)
            if (resYield != null && resYield.reference != null && element != null && resYield.reference!!.resolve() != null) {
                val name = element.text.replace("|", "")
                val yieldParams = resYield.reference!!.resolve()!!.children.filter { it is HbParam }
                val angleBracketBlock = resYield.parent
                val startIdx = angleBracketBlock.attributes.indexOfFirst { it.text.startsWith("|") }
                val endIdx = angleBracketBlock.attributes.size
                val params = angleBracketBlock.attributes.toList().subList(startIdx, endIdx)
                val refPsi = params.find { Regex("\\|*.*\\b$name\\b.*\\|*").matches(it.text) }
                val blockParamIdx = params.indexOf(refPsi)
                return followReferences(yieldParams.getOrNull(blockParamIdx), path)
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

            val name = parent.children.getOrNull(1)
            if (name?.text == "component") {
                return parent.children.getOrNull(2)
            }
            return name
        }

        class ArgsAndPositionals {
            val positional: MutableList<String?> = mutableListOf()
            val named: MutableList<String?> = mutableListOf()
            val namedRefs: MutableList<PsiElement?> = mutableListOf()
            var restparamnames: String? = null
        }

        fun getArgsAndPositionals(helperhelperOrModifier: PsiElement, positionalen: Int? = null): ArgsAndPositionals {
            val psi = PsiTreeUtil.collectElements(helperhelperOrModifier) { it.elementType == HbTokenTypes.ID }.firstOrNull() ?: helperhelperOrModifier
            var func = followReferences(psi)
            if ((func == null || func == psi) && psi.children.isNotEmpty()) {
                func = followReferences(psi.children[0])
                if (func == psi.children[0]) {
                    val id = PsiTreeUtil.collectElements(psi) { it !is LeafPsiElement && it.elementType == HbTokenTypes.ID }.lastOrNull()
                    func = followReferences(id, psi.text)
                }
            }

            val data = ArgsAndPositionals()

            if (func is TypeScriptVariable) {
                if (func.jsType is JSGenericTypeImpl) {
                    val args = (func.jsType as JSGenericTypeImpl).arguments[0].asRecordType().properties.find { it.memberName == "Args" }
                    val positional = (args?.jsType?.asRecordType()?.properties?.find { it.memberName == "Positional" }?.jsType?.sourceElement as? TypeScriptTupleType)?.members?.map { it.tupleMemberName }
                    val namedRecord = args?.jsType?.asRecordType()?.properties?.find { it.memberName == "Args" }?.jsType?.asRecordType()
                    val named = namedRecord?.propertyNames
                    positional?.forEach { data.positional.add(it) }
                    named?.forEach { data.named.add(it) }
                    namedRecord?.properties?.forEach { data.namedRefs.add(it.memberSource.singleElement) }
                    return data

                }
                val signatures = func.jsType?.asRecordType()?.properties?.firstOrNull()?.jsType?.asRecordType()?.typeMembers;
                signatures?.map { it as? TypeScriptCallSignature }?.forEachIndexed { index, it ->
                    if (it ==null) return@forEachIndexed
                    val namedRecord = it.parameters.firstOrNull()?.jsType?.asRecordType()
                    val namedParams = namedRecord?.propertyNames
                    val positional = (it.parameters.size > 1).ifTrue { it.parameters.slice(IntRange(1, it.parameters.lastIndex)).map { it.name } }
                    if (positionalen != null && positional?.size != positionalen && index != signatures.lastIndex) {
                        return@forEachIndexed
                    }
                    positional?.forEach { data.positional.add(it) }
                    namedParams?.forEach { data.named.add(it) }
                    namedRecord?.properties?.forEach { data.namedRefs.add(it.memberSource.singleElement) }
                    return data
                }
            }

            if (func is JSClass) {
                val args = findComponentArgsType(func)
                args?.propertyNames?.forEach { data.named.add(it) }
                args?.properties?.forEach { data.namedRefs.add(it.memberSource.singleElement) }
                return data
            }

            if ((func is Helper || func is Modifier) && (func as PsiElementDelegate<*>).element as? JSFunction != null) {
                func = func.element as JSFunction
                var arrayName: String? = null
                var array: JSType?
                var named: MutableSet<String>? = null
                var refs: List<JSRecordType.PropertySignature>? = listOf()

                var args = func.parameters.lastOrNull()?.jsType
                array = null

                if (args is JSTypeImpl) {
                    args = args.asRecordType()
                }

                if (args is JSRecordType) {
                    array = args.findPropertySignature("positional")?.jsType
                    named = args.findPropertySignature("named")?.jsType?.asRecordType()?.propertyNames
                    refs = args.findPropertySignature("named")?.jsType?.asRecordType()?.properties?.toList()
                    arrayName = "positional"
                }

                if (array == null) {
                    arrayName = func.parameters.firstOrNull()?.name ?: arrayName
                    array = func.parameters.firstOrNull()?.jsType
                    if (func.parameters.size > 1) {
                        named = func.parameters.last().jsType?.asRecordType()?.propertyNames
                        refs = func.parameters.last().jsType?.asRecordType()?.properties?.toList()
                    }
                }
                named?.forEach { data.named.add(it) }
                refs?.forEach { data.namedRefs.add(it.memberSource.singleElement) }

                val positionalType = array
                if (positionalType is JSTupleType) {
                    var names: List<String?>? = null
                    if (positionalType.sourceElement is TypeScriptTupleTypeImpl) {
                        names = (positionalType.sourceElement as TypeScriptTupleTypeImpl).members.map { it.tupleMemberName }
                    }
                    if (positionalType.sourceElement is JSDestructuringArray) {
                        names = (positionalType.sourceElement as JSDestructuringArray).elementsWithRest.map { it.text }
                    }
                    if (names == null) {
                        data.restparamnames = arrayName
                    }
                    names?.forEach { data.positional.add(it) }
                    return data
                }

                if (positionalType is JSArrayType) {
                    data.restparamnames = arrayName
                    return data
                }
            }

            if (func is JSFunction) {
                val positionals = func.parameters
                positionals.forEach { data.positional.add(it.name) }
                return data
            }

            return data
        }

        fun referenceImports(element: PsiElement, name: String): PsiElement? {
            val insideImport = element.parents(false).find { it is HbMustache && it.children.getOrNull(1)?.text == "import"} != null

            if (insideImport && element.text != "from" && element.text != "import") {
                return null
            }
            val hbsView = element.containingFile.viewProvider.getPsi(Language.findLanguageByID("Handlebars")!!)
            val imports = PsiTreeUtil.collectElements(hbsView, { it is HbMustache && it.children[1].text == "import" })
            val ref = imports.find {
                val names = it.children[2].text
                        .replace("'", "")
                        .replace("\"", "")
                        .replace("{", "")
                        .replace("}", "")
                        .split(",")
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
            //val index = Regex("$name").find(ref.children[2].text)!!.range.first
            //val file = element.containingFile.findReferenceAt(ref.children[2].textOffset + index)
            return ref.children[2]
        }

        fun handleEmberHelpers(element: PsiElement?): PsiElement? {
            if (element is PsiElement && element.text.contains(Regex("^(\\(|\\{\\{)component\\b"))) {
                val idx = element.children.indexOfFirst { it.text == "component" }
                val param = element.children.get(idx + 1)
                if (param.children.firstOrNull()?.children?.firstOrNull() is HbStringLiteral) {
                    return param.children.firstOrNull()?.children?.firstOrNull()?.reference?.resolve()
                }
                return param
            }
            if (element is PsiElement && element.text.contains(Regex("^(\\(|\\{\\{)or\\b"))) {
                val params = element.children.filter { it is HbParam && !it.text.startsWith("@") }.drop(1)
                return params.find { it.children.firstOrNull()?.children?.firstOrNull()?.references?.isNotEmpty() == true } ?:
                params.find { it.children.firstOrNull()?.children?.firstOrNull()?.children?.firstOrNull()?.references?.isNotEmpty() == true } ?:
                params.find { it.text.contains(Regex("^(\\(|\\{\\{)component\\b")) && !it.children.contains(handleEmberHelpers(it)) }?.let { handleEmberHelpers(it) }
            }
            if (element is PsiElement && element.text.contains(Regex("^(\\(|\\{\\{)if\\b"))) {
                val params = element.children.filter { it is HbParam && !it.text.startsWith("@") }.drop(1)
                return params.find { it.children.firstOrNull()?.children?.firstOrNull()?.references?.isNotEmpty() == true } ?:
                params.find { it.children.firstOrNull()?.children?.firstOrNull()?.children?.firstOrNull()?.references?.isNotEmpty() == true } ?:
                params.find { it.text.contains(Regex("^(\\(|\\{\\{)component\\b")) && !it.children.contains(handleEmberHelpers(it)) }?.let { handleEmberHelpers(it) }
            }
            if (element is PsiElement && element.parent is HbOpenBlockMustache) {
                val mustacheName = element.parent.children.find { it is HbMustacheName }?.text
                val helpers = arrayOf("let", "each", "with", "component", "yield")
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

        fun findTagYieldAttribute(element: PsiElement?): XmlAttribute? {
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
                val ref = asAttr.references.find { it is HbReference }
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

            val fullPathToIndexTs = "index.ts"
            val fullPathToIndexDts = "index.d.ts"

            var containingFile = file.containingFile
            if (containingFile == null) {
                return ComponentReferenceData()
            }
            if (containingFile.name.endsWith(".js")) {
                containingFile = dir?.findFile(containingFile.name.replace(".js", ".d.ts"))
                        ?: containingFile
            }

            val tsFile = getFileByPath(parentModule, fullPathToTs) ?: getFileByPath(parentModule, fullPathToDts) ?:
            getFileByPath(parentModule, fullPathToIndexTs) ?: getFileByPath(parentModule, fullPathToIndexDts) ?:containingFile
            var cls = findDefaultExportClass(tsFile)
                    ?: findDefaultExportClass(containingFile)
                    ?: file

            if (f is JSElement && f !is PsiFile) {
                cls = f;
            }

            if (cls is PsiFile && cls.name == "intellij-emberjs/internal/components-stub") {
                cls = f;
            }
            var jsTemplate: Any? = null;
            if (cls is JSElement) {

                val scope = ProjectScope.getAllScope(cls.project)
                val emberName = EmberNameIndex.getFilteredPairs(scope) { it.type == "component" }.find { it.second == cls.containingFile }?.first

                jsTemplate = cls as? JSStringTemplateExpression ?: PsiTreeUtil.findChildOfType(cls, JSStringTemplateExpression::class.java)

                if (cls is JSClass) {
                    val argsElem = findComponentArgsType(cls)
                    val signatures = argsElem?.properties ?: emptyList()
                    for (sign in signatures) {
                        val comment = sign.memberSource.singleElement?.children?.find { it is JSDocComment }
//                val s: TypeScriptSingleTypeImpl? = sign.children.find { it is TypeScriptSingleTypeImpl } as TypeScriptSingleTypeImpl?
                        val attr = sign.memberName
                        val data = tplArgs.find { it.value == attr } ?: ArgData()
                        data.value = attr
                        data.reference = AttrPsiReference(sign.memberSource.singleElement!!)
                        data.description = comment?.text ?: ""
                        if (tplArgs.find { it.value == attr } == null) {
                            tplArgs.add(data)
                        }
                    }

                    jsTemplate = cls.fields.find { it.name == "layout" } ?: jsTemplate
                    jsTemplate = jsTemplate ?: cls.fields.find { it.name == "template" }
                    jsTemplate = followReferences(jsTemplate as PsiElement?)
                }
            }

            template = dir?.findFile("$name.hbs") ?: dir?.findFile("template.hbs") ?: dir?.findFile("template.ts") ?: dir?.findFile("template.js")
            if (template is JSFile) {
                jsTemplate = resolveTemplateExport(template)
            }

            if (jsTemplate is TypeScriptField) {
                jsTemplate = jsTemplate.initializer
            }
            if (jsTemplate is ES6TaggedTemplateExpression) {
                jsTemplate = jsTemplate.templateExpression
            }
            if (jsTemplate is JSStringTemplateExpression) {
                val manager = InjectedLanguageManager.getInstance(jsTemplate.project)
                val injected = manager.findInjectedElementAt(jsTemplate.containingFile, jsTemplate.startOffset + 1)?.containingFile
                jsTemplate = injected?.containingFile?.viewProvider?.getPsi(Language.findLanguageByID("Handlebars")!!)?.containingFile
            }
            if (jsTemplate is JSLiteralExpression) {
                jsTemplate = jsTemplate.text
            }

            if (jsTemplate is String) {
                jsTemplate = jsTemplate.substring(1, jsTemplate.lastIndex)
                jsTemplate = PsiFileFactory.getInstance(file.project).createFileFromText("$name-virtual", Language.findLanguageByID("Handlebars")!!, jsTemplate)
            }

            if (dir != null || jsTemplate != null) {
                // co-located
                if (name == "component") {
                    name = "template"
                }

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

                template = jsTemplate as? PsiFile?
                        ?: template
                                ?: getFileByPath(parentModule, fullPathToHbs)
                                ?: getFileByPath(parentModule, fullPathToHbs.replace("/components/", "/templates/components/"))


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
            return ComponentReferenceData(hasSplattributes, tplYields, tplArgs, template, tsFile)
        }
    }
}
