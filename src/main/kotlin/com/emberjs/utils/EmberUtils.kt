package com.emberjs.utils


import com.dmarcotte.handlebars.HbLanguage
import com.dmarcotte.handlebars.parsing.HbTokenTypes
import com.dmarcotte.handlebars.psi.*
import com.dmarcotte.handlebars.psi.impl.HbPathImpl
import com.emberjs.cli.EmberCliFrameworkDetector
import com.emberjs.gts.GjsLanguage
import com.emberjs.gts.GtsElementTypes
import com.emberjs.gts.GtsFile
import com.emberjs.gts.GtsFileViewProvider
import com.emberjs.gts.GtsLanguage
import com.emberjs.hbs.*
import com.emberjs.index.EmberNameIndex
import com.emberjs.navigation.EmberGotoRelatedProvider
import com.emberjs.psi.EmberNamedElement
import com.emberjs.resolver.EmberInternalJSModuleReference
import com.emberjs.resolver.EmberJSModuleReference
import com.emberjs.resolver.ProjectFile
import com.emberjs.xml.AttrPsiReference
import com.emberjs.xml.EmberAttrDec
import com.emberjs.xml.EmberXmlElementDescriptor
import com.intellij.application.options.CodeStyle
import com.intellij.framework.detection.impl.FrameworkDetectionManager
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.Language
import com.intellij.lang.ecmascript6.psi.ES6ExportDefaultAssignment
import com.intellij.lang.ecmascript6.psi.ES6ExportSpecifierAlias
import com.intellij.lang.ecmascript6.psi.ES6ImportExportDeclaration
import com.intellij.lang.ecmascript6.psi.ES6ImportSpecifier
import com.intellij.lang.ecmascript6.psi.ES6ImportedBinding
import com.intellij.lang.ecmascript6.psi.impl.ES6ImportDeclarationImpl
import com.intellij.lang.ecmascript6.resolve.ES6PsiUtil
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.javascript.JSLanguageDialect
import com.intellij.lang.javascript.JavaScriptSupportLoader
import com.intellij.lang.javascript.frameworks.modules.JSFileModuleReference
import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.ecma6.*
import com.intellij.lang.javascript.psi.ecma6.impl.TypeScriptClassImpl
import com.intellij.lang.javascript.psi.ecma6.impl.TypeScriptTupleTypeImpl
import com.intellij.lang.javascript.psi.ecma6.impl.TypeScriptUnionOrIntersectionTypeImpl
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.impl.JSDestructuringParameterImpl
import com.intellij.lang.javascript.psi.jsdoc.JSDocComment
import com.intellij.lang.javascript.psi.types.*
import com.intellij.lang.javascript.psi.types.JSRecordTypeImpl.PropertySignatureImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDirectory
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.xml.HtmlCodeStyleSettings
import com.intellij.psi.impl.file.PsiDirectoryImpl
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.templateLanguages.OuterLanguageElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parents
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.intellij.refactoring.suggested.startOffset
import com.intellij.testFramework.LightVirtualFile
import com.intellij.xml.XmlAttributeDescriptor

class ArgData(
        var value: String = "",
        var description: String? = null,
        var reference: AttrPsiReference? = null)

class ComponentReferenceData(
        val hasSplattributes: Boolean = false,
        val yields: MutableList<EmberXmlElementDescriptor.YieldReference> = mutableListOf(),
        val args: MutableList<ArgData> = mutableListOf(),
        val template: PsiFile? = null,
        val component: PsiFile? = null
)

open class PsiElementDelegate<T: PsiElement>(val element: T) : PsiElement by element

class Helper(element: JSFunction) : PsiElementDelegate<JSFunction>(element)
class Modifier(element: JSFunction) : PsiElementDelegate<JSFunction>(element)


class EmberUtils {
    companion object {

        fun isEmber(project: Project): Boolean {
            return project.guessProjectDir()?.isEmber ?: false
        }
        fun isEnabledEmberProject(project: Project): Boolean {
            return EmberCliFrameworkDetector.hasEnabledEmberFramework(project)
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

        fun resolveDefaultExport(ffile: PsiElement?): PsiElement? {
            var file = ffile ?: return null
            if (file is GtsFile) {
                file = file.viewProvider.getPsi(JavaScriptSupportLoader.TYPESCRIPT) ?: file.viewProvider.getPsi(JavaScriptSupportLoader.ECMA_SCRIPT_6)
            }
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
                val resolved = exp ?: exportImport.fromClause?.references?.mapNotNull {
                    (it as? FileReference)?.multiResolve(false)
                            ?.firstOrNull() ?: it.resolve()
                }
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

            val r = ref as PsiElement? ?: exp

            if (r is JSVariable) {
                val v = r.children.getOrNull(1)
                if (v != null) {
                  val viewProvider = file.containingFile.viewProvider
                  val ts = viewProvider.findElementAt(v.textOffset, JavaScriptSupportLoader.TYPESCRIPT)
                  val js = viewProvider.findElementAt(v.textOffset, JavaScriptSupportLoader.ECMA_SCRIPT_6)
                  if (ts is OuterLanguageElement || js is OuterLanguageElement) {
                      return r
                  }
                }
            }

            val func = r?.children?.find { it is JSCallExpression }
            if (func is JSCallExpression) {
                return func
            }
            val cls = r?.children?.find { it is JSClass }
            if (cls is JSClass) {
                return cls
            }
            val obj = r?.children?.find { it is JSObjectLiteralExpression }
            if (obj is JSObjectLiteralExpression) {
                return obj
            }
            return r
        }

        fun resolveHelper(file: PsiElement?): Helper? {
            val cls = (file as? PsiFile)?.let { resolveDefaultExport(it) } ?: file
            if (cls is JSCallExpression && cls.argumentList != null) {
                var func: PsiElement? = cls.argumentList!!.arguments.lastOrNull()
                while (func is JSReferenceExpression) {
                    func = func.resolve()
                }
                if (func is JSVariable) {
                    func = func.initializer
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


        fun findDefaultExportClass(f: PsiFile): JSClass? {
            var file = f
            if (file is GtsFile) {
                file = file.viewProvider.getPsi(JavaScriptSupportLoader.TYPESCRIPT) ?: file.viewProvider.getPsi(JavaScriptSupportLoader.ECMA_SCRIPT_6)
            }
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
                val JS = JavaScriptSupportLoader.ECMA_SCRIPT_6
                val TS = JavaScriptSupportLoader.TYPESCRIPT

                val inJs = view.findElementAt(element.startOffset, TS) ?: view.findElementAt(element.startOffset, JS)
                cls = inJs?.parent as? JSClass
                if (cls == null) {
                    cls = inJs?.parent?.children?.last() as? TypeScriptVariable
                    if (cls != null) {
                        return cls
                    }
                    cls = inJs?.prevSibling?.children?.lastOrNull() as? TypeScriptVariable
                    if (cls != null) {
                        return cls
                    }
                }
                if (cls == null) {
                    cls = (inJs?.parent as? TypeScriptAsExpression)
                    return cls
                }
                return cls
            }
            if (element.originalVirtualFile is VirtualFileWindow) {
                val offset = (element.originalVirtualFile as VirtualFileWindow).documentWindow.hostRanges[0].startOffset
                val psiManager = PsiManager.getInstance(element.project)
                val f = psiManager.findFile((element.originalVirtualFile as VirtualFileWindow).delegate)!!
                val inJs = f.findElementAt(offset)
                cls = PsiTreeUtil.findFirstParent(inJs, { it is JSClass }) as JSElement?
            }
            val file = dir?.findFile("$fileName.ts")
                    ?: dir?.findFile("$fileName.d.ts")
                    ?: dir?.findFile("$fileName.js")
                    ?: dir?.findFile("$fileName.gts")
                    ?: dir?.findFile("$fileName.gjs")
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
            if (cls is TypeScriptVariable) {
                if (cls.jsType.toString().startsWith("TOC<") || cls.jsType.toString().startsWith("TemplateOnlyComponent<")) {
                    val arg = (cls.jsType as JSGenericTypeImpl).arguments[0]
                    return arg.asRecordType().properties.find { it.memberName == "Args" }?.jsType?.asRecordType() ?: arg.asRecordType()
                }
                return cls.jsType?.asRecordType()?.callSignatures?.firstOrNull()?.returnType?.asRecordType()?.properties?.find { it.memberName == "Context" }?.jsType?.asRecordType()?.properties?.find { it.memberName == "args" }?.jsType?.asRecordType()
            }
            if (cls is TypeScriptAsExpression) {
                return cls.type?.jsType?.asRecordType()?.callSignatures?.firstOrNull()?.returnType?.asRecordType()?.properties?.find { it.memberName == "Context" }?.jsType?.asRecordType()?.properties?.find { it.memberName == "args" }?.jsType?.asRecordType()
            }
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
            if (reference is ImportNameReference) {
                var name = path
                val intRange = IntRange(reference.rangeInElement.startOffset, reference.rangeInElement.endOffset-1)
                element = if (reference.element.references.size == 1 || reference.element.text.substring(intRange) == name) {
                    reference.resolve()
                } else {
                    null
                }
            }

            if (element?.references?.firstOrNull() is ImportNameReference) {
                element = element.references.firstNotNullOfOrNull { resolveReference(it, reference?.element?.text) }
            }

            return element
        }

        fun followReferences(element: PsiElement?, path: String? = null): PsiElement? {

            if (element is ES6ExportSpecifierAlias) {
                return followReferences(element.parent.reference?.resolve())
            }

            if (element is ES6ExportDefaultAssignment) {
                return element.namedElement
            }

            if (element is ES6ImportedBinding) {
                val res = element.multiResolve(true).firstOrNull()
                if (res != null && res.element != null) {
                    return res.element
                }
                var ref:PsiReference? = element.declaration?.fromClause?.references?.findLast { it is EmberJSModuleReference && it.rangeInElement.endOffset == it.element.textLength - 1 && it.resolve() != null } as EmberJSModuleReference?
                if (ref == null) {
                    ref = element.declaration?.fromClause?.references?.findLast { it is JSFileModuleReference }
                    val res = ref?.resolve()
                    if (res is PsiFile && res.name.matches(Regex(".*\\.(sass|scss|less|css)\$"))) {
                        return ref?.resolve()
                    }
                }
                if (ref == null || ref.resolve() == null) {
                    var tsFiles = element.declaration?.fromClause?.references?.mapNotNull { (it as? FileReferenceSet)?.resolve() }
                    if (tsFiles?.isEmpty() == true) {
                        tsFiles = element.declaration?.fromClause?.references?.mapNotNull { (it as? JSFileModuleReference)?.resolve() }
                    }
                    return tsFiles?.filterIsInstance<JSFile>()?.maxByOrNull { it.virtualFile.path.length }
                }

                return followReferences(ref.resolve())
            }

            if (element is ES6ImportSpecifier) {
                val results = element.multiResolve(false)
                val internal = (element.parent.parent as ES6ImportDeclarationImpl).fromClause?.references?.find { it is EmberInternalJSModuleReference } as? EmberInternalJSModuleReference
                if (internal != null) {
                    return followReferences(PsiTreeUtil.collectElements(internal.internalFile) { (it is JSElementBase) && it.isExported && it.name == element.name }.firstOrNull())
                }

                return followReferences(results.find { it.element?.containingFile is ProjectFile }?.element ?: results.firstOrNull()?.element)
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

            val resYield: XmlAttributeDescriptor? = findTagYieldAttribute(element)
            if (resYield != null && resYield.declaration != null && element != null) {
                val name = element.text.replace("|", "")
                var types = listOf<PsiElement?>()
                if (resYield.declaration is TypeScriptPropertySignature && (resYield.declaration as TypeScriptPropertySignature).typeDeclaration is TypeScriptTupleType) {
                    val tuple = (resYield.declaration as TypeScriptPropertySignature).typeDeclaration?.jsType as JSTupleType
                    types = tuple.types.map { it.source.sourceElement }
                }
                val yieldParams = resYield.declaration!!.children.filterIsInstance<HbParam>()
                val angleBracketBlock = element.parent as XmlTag
                val startIdx = angleBracketBlock.attributes.indexOfFirst { it.text.startsWith("|") }
                val endIdx = angleBracketBlock.attributes.size
                if (startIdx > -1 && startIdx < endIdx) {
                    val params = angleBracketBlock.attributes.toList().subList(startIdx, endIdx)
                    val refPsi = params.find { Regex("\\|*.*\\b$name\\b.*\\|*").matches(it.text) }
                    val blockParamIdx = params.indexOf(refPsi)
                    return followReferences(yieldParams.getOrNull(blockParamIdx) ?: types.getOrNull(blockParamIdx), path)
                }
            }

            if (element?.references != null && element.references.isNotEmpty()) {
                val res = element.references.firstNotNullOfOrNull { resolveReference(it, path) }
                if (res == element) {
                    return res
                }
                return followReferences(res, path)
            }
            return element
        }


        fun findFirstHbsParamFromParam(psiElement: PsiElement?): PsiElement? {
            val parents = psiElement?.parents(false)
            val parent = parents?.find { it.children.getOrNull(0)?.elementType == HbTokenTypes.OPEN_SEXPR }
                    ?: parents?.find { it.children.getOrNull(0)?.elementType == HbTokenTypes.OPEN }
                    ?: parents?.find { it.children.getOrNull(0)?.elementType == HbTokenTypes.OPEN_BLOCK }
                    ?: return null

            val name = parent.children.getOrNull(1)
            if (name?.text == "component") {
                return parent.children.getOrNull(2)
            }
            return name
        }

        class ArgsAndPositionals {
            val positional: MutableList<String?> = mutableListOf()
            val positionalOptions = mutableMapOf<Int, List<String>>()
            val named: MutableList<String?> = mutableListOf()
            val namedOptions = mutableMapOf<String, List<String>>()
            val namedRefs: MutableList<PsiElement?> = mutableListOf()
            var restparamnames: String? = null
        }


        fun getJsTypeCompletionOptions(jsType: JSType, quote: String = ""): List<Pair<String, List<String>>>? {
            val options = (jsType as? JSTupleType)?.types?.mapIndexed { index, it ->
                if (it is TypeScriptTypeOperatorJSTypeImpl && it.typeText.startsWith("keyof ")) {
                    return@mapIndexed Pair(index.toString(), it.referencedType.asRecordType().propertyNames.map { "${quote}${it}${quote}" })
                }
                val types = (it.asRecordType().sourceElement as? TypeScriptUnionOrIntersectionTypeImpl)?.types
                val typesStr = types?.map { (it as? TypeScriptLiteralType?)?.innerText }?.filterNotNull() ?: arrayListOf()

                return@mapIndexed Pair(index.toString(), typesStr.map { "${quote}${it}${quote}" })
            }
            return options
        }

        fun getArgsAndPositionals(helperhelperOrModifier: PsiElement, positionalen: Int? = null): ArgsAndPositionals {
            val psi = PsiTreeUtil.collectElements(helperhelperOrModifier) { it is HbPsiElement && it.elementType == HbTokenTypes.ID }.firstOrNull() ?: helperhelperOrModifier
            var func = resolveToEmber(followReferences(psi))
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
                val signatures = func.jsType?.asRecordType()?.properties?.firstOrNull()?.jsType?.asRecordType()?.typeMembers
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
                val componentData = getComponentReferenceData(func)
                componentData.args.forEach {
                    data.named.add(it.value)
                    data.namedRefs.add(it.reference?.resolve())
                }
                return data
            }

            val ovf = func?.originalVirtualFile
            if ((func is Helper || func is Modifier) && (func as PsiElementDelegate<*>).element as? JSFunction != null || (ovf is LightVirtualFile && ovf.language is JSLanguageDialect) || func?.containingFile is ProjectFile) {
                func = ((func as? PsiElementDelegate<*>)?.element ?: func) as JSFunction
                var arrayName: String? = null
                var array: JSType?
                var named: MutableSet<String>? = null
                var refs: List<JSRecordType.PropertySignature>? = listOf()

                var args = func.parameters.lastOrNull()?.jsType
                array = null

                if (args is JSTypeImpl) {
                    args = args.asRecordType()
                }

                val settings = CodeStyle.getCustomSettings(helperhelperOrModifier.containingFile, HtmlCodeStyleSettings::class.java)
                val quote = (settings.HTML_QUOTE_STYLE == CodeStyleSettings.QuoteStyle.Double).ifTrue { "\"" } ?: "'"
                if (args is JSRecordType) {
                    array = args.findPropertySignature("positional")?.jsType
                    named = args.findPropertySignature("named")?.jsType?.asRecordType()?.propertyNames
                    refs = args.findPropertySignature("named")?.jsType?.asRecordType()?.properties?.toList()
                    val options = array?.let { this.getJsTypeCompletionOptions(it, quote) }
                    options?.forEach { opt ->
                        val key = opt.first.toDoubleOrNull()?.toInt()
                        key?.let {
                            data.positionalOptions[key] = opt.second
                        }
                    }
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
                refs?.forEach {
                    val type = it.jsType
                    val options = mutableListOf<String>()
                    val types = mutableListOf<JSType?>()
                    if (type is JSCompositeTypeImpl) {
                        types.addAll(type.types)
                    } else {
                        types.add(type)
                    }

                    types.forEach { t ->
                        if (t is JSLiteralType) {
                            options.add(t.typeText)
                        }
                        if (t is JSKeyofType) {
                            options.add("___keyof__")
                        }
                    }

                    data.namedOptions[it.memberName] = options
                }

                val positionalType = array
                if (positionalType is JSTupleType) {
                    var names: List<String?>? = null
                    val destruct = func.parameters.firstOrNull() as? JSDestructuringParameterImpl
                    val destructArray = (destruct?.children?.find { it is JSDestructuringArray } as? JSDestructuringArray)?.elementsWithRest
                    if (positionalType.sourceElement is TypeScriptTupleTypeImpl) {
                        names = (positionalType.sourceElement as TypeScriptTupleTypeImpl).members.mapIndexed { index, it -> it.tupleMemberName ?: destructArray?.getOrNull(index)?.text }
                    }
                    if (positionalType.sourceElement is JSDestructuringArray) {
                        names = (positionalType.sourceElement as JSDestructuringArray).elementsWithRest.map { it.text }
                    }
                    if (names == null) {
                        data.restparamnames = arrayName
                    }
                    val options = this.getJsTypeCompletionOptions(positionalType, quote)
                    options?.forEach { opt ->
                        val key = opt.first.toDoubleOrNull()?.toInt()
                        key?.let {
                            data.positionalOptions[key] = opt.second
                        }
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
            val hbsView = element.containingFile.viewProvider.getPsi(HbLanguage.INSTANCE)
            val imports = PsiTreeUtil.collectElements(hbsView) { it is HbMustache && it.children[1].text == "import" }
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
            } ?: return null
            //val index = Regex("$name").find(ref.children[2].text)!!.range.first
            //val file = element.containingFile.findReferenceAt(ref.children[2].textOffset + index)
            return ref.children[2]
        }

        fun handleEmberProxyTypes(type: JSType?): JSType? {
            var jsType = type
            if (jsType !is JSArrayType && jsType != null) {
                jsType = (jsType.asRecordType().typeMembers.find { (it as? PropertySignatureImpl)?.memberName == "content" } as? PropertySignatureImpl)?.jsType
                if (jsType != null) {
                    val containingClassName = (PsiTreeUtil.findFirstParent(jsType.sourceElement) { it is JSClass} as? JSClass)?.name
                    if (containingClassName == "ArrayProxy") {
                        jsType = (jsType as? JSCompositeTypeImpl)?.types?.firstOrNull()
                        return jsType
                    }
                    if (jsType != null && containingClassName == "ObjectProxy") {
                        jsType = (jsType as? JSCompositeTypeImpl)?.types?.firstOrNull()
                        return jsType
                    }
                }
            }
            return null
        }

        fun handleEmberHelpers(element: PsiElement?): PsiElement? {
            if (element is PsiElement && element.text?.contains(Regex("^(\\(|\\{\\{)component\\b")) == true) {
                val idx = element.children.indexOfFirst { it.text == "component" }
                val param = element.children.get(idx + 1)
                if (param.children.firstOrNull()?.children?.firstOrNull() is HbStringLiteral) {
                    return param.children.firstOrNull()?.children?.firstOrNull()?.reference?.resolve()
                }
                return param
            }
            if (element is PsiElement && element.text?.contains(Regex("^(\\(|\\{\\{)or\\b")) == true) {
                val params = element.children.filter { it is HbParam && !it.text.startsWith("@") }.drop(1)
                return params.find { it.children.firstOrNull()?.children?.firstOrNull()?.references?.isNotEmpty() == true } ?:
                params.find { it.children.firstOrNull()?.children?.firstOrNull()?.children?.firstOrNull()?.references?.isNotEmpty() == true } ?:
                params.find { it.text.contains(Regex("^(\\(|\\{\\{)component\\b")) && !it.children.contains(handleEmberHelpers(it)) }?.let { handleEmberHelpers(it) }
            }
            if (element is PsiElement && element.text?.contains(Regex("^(\\(|\\{\\{)if\\b")) == true) {
                val params = element.children.filter { it is HbParam && !it.text.startsWith("@") }.drop(1)
                return params.find { it.children.firstOrNull()?.children?.firstOrNull()?.references?.isNotEmpty() == true } ?:
                params.find { it.children.firstOrNull()?.children?.firstOrNull()?.children?.firstOrNull()?.references?.isNotEmpty() == true } ?:
                params.find { it.text.contains(Regex("^(\\(|\\{\\{)component\\b")) && !it.children.contains(handleEmberHelpers(it)) }?.let { handleEmberHelpers(it) }
            }
            if (element is PsiElement && element.parent is HbOpenBlockMustache) {
                val mustacheName = element.parent.children.find { it is HbMustacheName }?.text
                val helpers = arrayOf("let", "each", "with", "component", "yield")
                if (helpers.contains(mustacheName)) {
                    val param = element.parent.children.find { it is HbParam }
                    if (element == param) {
                        return null
                    }
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

                        if (jsRef is JSTypeOwner && jsRef.jsType != null) {
                            val jsType = handleEmberProxyTypes(jsRef.jsType) ?: jsRef.jsType
                            if (jsType is JSArrayType) {
                                return jsType.type?.sourceElement
                            }
                        }
                    }
                }
            }
            return null
        }

        fun findTagYieldAttribute(element: PsiElement?): XmlAttributeDescriptor? {
            if (element is EmberAttrDec && element.name != "as") {
                val tag = element.parent
                return tag.attributes.find { it.name == "as" }?.descriptor
            }
            if (element is XmlAttribute && element.name != "as") {
                val tag = element.parent
                return tag.attributes.find { it.name == "as" }?.descriptor
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
                val ref = asAttr.references.find { it is EmberReference }
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
            val defaultExport = resolveDefaultExport(f.containingFile)
            val file = defaultExport?.containingFile ?: f
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

            val possibleFiles = arrayOf(
                    "$name.ts",
                    "$name.d.ts",
                    "$name.gts",
                    "index.ts",
                    "index.d.ts",
                    "index.gts"
            )
            var containingFile = file.containingFile
            if (containingFile == null) {
                return ComponentReferenceData()
            }
            if (containingFile.name.endsWith(".js")) {
                containingFile = dir?.findFile(containingFile.name.replace(".js", ".d.ts"))
                        ?: containingFile
            }

            val tsFile = possibleFiles.firstNotNullOfOrNull { getFileByPath(parentModule, it) } ?:containingFile
            var cls: PsiElement

            if (f is JSElement && f !is PsiFile) {
                cls = f
            } else {
                cls = findDefaultExportClass(tsFile)
                        ?: findDefaultExportClass(containingFile)
                        ?: defaultExport
                        ?: file
            }

            if (cls is PsiFile && cls.name == "intellij-emberjs/internal/components-stub") {
                cls = f
            }
            var jsTemplate: Any? = null
            if (cls is JSElement) {
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
                    jsTemplate = jsTemplate
                            ?:
                            PsiTreeUtil.collectElements(cls) { it.elementType == GtsElementTypes.GTS_OUTER_ELEMENT_TYPE && it.parent == cls }.firstOrNull()

                    if (jsTemplate == null) {
                        val setComponentTemplate = PsiTreeUtil.collectElements(cls.containingFile) {
                            (it is JSCallExpression) && it.children.firstOrNull()?.children?.firstOrNull()?.text == "setComponentTemplate" && it.arguments.lastOrNull()?.reference?.isReferenceTo(cls) == true
                        }.firstOrNull() as? JSCallExpression
                        if (setComponentTemplate != null) {
                            val precompileTemplate = (setComponentTemplate.arguments.firstOrNull() as? JSCallExpression)
                            jsTemplate = precompileTemplate?.arguments?.firstOrNull()
                        }
                    }
                }

                if (cls is TypeScriptVariable && cls.children.getOrNull(1) != null) {
                    jsTemplate = f.containingFile.viewProvider.findElementAt(cls.children[1].textOffset, JavaScriptSupportLoader.TYPESCRIPT)
                } else if (cls is JSVariable && cls.children.getOrNull(1) != null) {
                    jsTemplate = f.containingFile.viewProvider.findElementAt(cls.children[1].textOffset, JavaScriptSupportLoader.ECMA_SCRIPT_6)
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
                jsTemplate = jsTemplate.templateExpression?.value
            }
            if (jsTemplate is JSStringTemplateExpression) {
                val manager = InjectedLanguageManager.getInstance(jsTemplate.project)
                val injected = manager.findInjectedElementAt(jsTemplate.containingFile, jsTemplate.startOffset)?.containingFile
                jsTemplate = injected?.containingFile?.viewProvider?.getPsi(HbLanguage.INSTANCE)?.containingFile ?: jsTemplate
            }
            if (jsTemplate is JSLiteralExpression) {
                jsTemplate = jsTemplate.value
            }

            if (jsTemplate is String) {
                jsTemplate = PsiFileFactory.getInstance(file.project).createFileFromText("$name-virtual", Language.findLanguageByID("Handlebars")!!, jsTemplate)
            }

            var psiRange = template?.textRange ?: (jsTemplate as? PsiFile)?.textRange

            if (jsTemplate is PsiElement && jsTemplate.elementType == GtsElementTypes.GTS_OUTER_ELEMENT_TYPE) {
                template = jsTemplate.containingFile.viewProvider.getPsi(HbLanguage.INSTANCE)
                psiRange = jsTemplate.textRange
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


                if (template?.node?.psi != null && psiRange != null) {
                    val args = PsiTreeUtil.collectElements(template.node.psi) { it is HbData && psiRange.contains(it.textRange) }
                    for (arg in args) {
                        val argName = arg.text.split(".").first()
                        if (tplArgs.find { it.value == argName } == null) {
                            tplArgs.add(ArgData(argName, "", AttrPsiReference(arg)))
                        }
                    }

                    val yields = PsiTreeUtil.collectElements(template.node.psi) { it is HbPathImpl && it.text == "yield" && psiRange.contains(it.textRange) }
                    for (y in yields) {
                        tplYields.add(EmberXmlElementDescriptor.YieldReference(y))
                    }
                }
                val tsClass = (cls as? JSClass)
                var blocks: JSRecordType.PropertySignature? = null
                var args: JSRecordType.PropertySignature? = null
                var arg: JSType? = null
                val supers = tsClass?.superClasses?.asList()?.toTypedArray<JSClass>()
                val classes = mutableListOf<JSClass?>()
                classes.add(tsClass)
                supers?.let { classes.addAll(it) }
                classes.forEach { s ->
                    s?.extendsList?.members?.forEach { m->
                        m.typeArgumentsAsTypes.forEach { typeArgument ->
                            blocks = blocks ?: typeArgument.asRecordType().properties.toList().find { it.memberName == "Blocks" }
                            args = args ?: typeArgument.asRecordType().properties.toList().find { it.memberName == "Args" }
                        }
                    }
                }
                if (cls is TypeScriptVariable) {
                    if (cls.jsType.toString().startsWith("TOC<") || cls.jsType.toString().startsWith("TemplateOnlyComponent<")) {
                        arg = (cls.jsType as JSGenericTypeImpl).arguments[0]
                        blocks = arg.asRecordType().properties.find { it.memberName == "Blocks" }
                        args = arg.asRecordType().properties.find { it.memberName == "Args" }
                    }
                }
                if (blocks != null) {
                    val yields = blocks?.jsType?.asRecordType()?.properties
                    yields?.forEach {
                        it.memberSource.singleElement?.let { tplYields.add(EmberXmlElementDescriptor.YieldReference(it)) }
                    }
                }
                if (args != null || arg != null) {
                    val arguments = args?.jsType?.asRecordType()?.properties ?: args?.jsType?.asRecordType()?.properties
                    arguments?.forEach {
                        if (it.memberSource.singleElement != null) {
                            tplArgs.add(ArgData(it.memberName, "", AttrPsiReference(it.memberSource.singleElement!!)))
                        }
                    }
                }

            }

            val hasSplattributes = template?.text?.contains("...attributes") ?: false
            return ComponentReferenceData(hasSplattributes, tplYields, tplArgs, template, tsFile)
        }

        fun getScopesForFile(file: VirtualFile?): List<VirtualFile>? {
            if (file == null) {
                return null
            }
            val validParents = mutableListOf<VirtualFile>()
            val addonScope = file.parentEmberModule?.findDirectory("addon")
            val appScope = file.parentEmberModule?.findDirectory("app")
            val testScope = file.parentEmberModule?.findDirectory("tests")
            val nodeModules = file.parentEmberModule?.findDirectory("node_modules")
            val inRepoAddonDirs = file.parentEmberModule?.inRepoAddonDirs

            if (nodeModules != null) {
                validParents.add(nodeModules)
            }

            if (inRepoAddonDirs != null && inRepoAddonDirs.any { file.parents.contains(it) }) {
                validParents.add(file.parents.find { inRepoAddonDirs.any { inr -> inr == it } }!!)
            }

            if (addonScope != null && file.parents.contains(addonScope)) {
                validParents.add(addonScope)
                inRepoAddonDirs?.let { validParents.addAll(it) }
            }
            if (appScope != null && file.parents.contains(appScope)) {
                addonScope?.let { validParents.add(it) }
                inRepoAddonDirs?.let { validParents.addAll(it) }
                validParents.add(appScope)
            }
            if (testScope != null && file.parents.contains(testScope)) {
                addonScope?.let { validParents.add(it) }
                appScope?.let { validParents.add(it) }
                validParents.add(testScope)
            }
            return validParents.toList()
        }

        fun isInScope(file: VirtualFile?, list: List<VirtualFile>?): Boolean {
            if (file == null) return true
            if (list.isNullOrEmpty()) return true
            return file.parents.any { list.contains(it) }
        }
    }
}
