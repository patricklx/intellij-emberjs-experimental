package com.emberjs.hbs

import com.dmarcotte.handlebars.parsing.HbTokenTypes
import com.dmarcotte.handlebars.psi.*
import com.dmarcotte.handlebars.psi.impl.HbBlockWrapperImpl
import com.dmarcotte.handlebars.psi.impl.HbPathImpl
import com.emberjs.glint.GlintLanguageServiceProvider
import com.emberjs.gts.GtsFileViewProvider
import com.emberjs.lookup.EmberLookupElementBuilderWithCandidate
import com.emberjs.psi.EmberNamedElement
import com.emberjs.utils.*
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.ide.highlighter.HtmlFileType
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.Language
import com.intellij.lang.ecmascript6.psi.ES6ImportDeclaration
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.javascript.JavaScriptSupportLoader
import com.intellij.lang.javascript.completion.JSImportCompletionUtil
import com.intellij.lang.javascript.modules.JSImportPlaceInfo
import com.intellij.lang.javascript.modules.imports.JSImportCandidate
import com.intellij.lang.javascript.modules.imports.providers.JSImportCandidatesProvider
import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.JSRecordType.PropertySignature
import com.intellij.lang.javascript.psi.ecma6.ES6TaggedTemplateExpression
import com.intellij.lang.javascript.psi.ecma6.JSTypedEntity
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.impl.JSUseScopeProvider
import com.intellij.lang.javascript.psi.impl.JSVariableImpl
import com.intellij.lang.javascript.psi.jsdoc.impl.JSDocCommentImpl
import com.intellij.lang.javascript.psi.types.JSRecordTypeImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.css.CssRulesetList
import com.intellij.psi.css.CssSelector
import com.intellij.psi.css.impl.CssRulesetImpl
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.isAncestor
import com.intellij.psi.xml.XmlAttribute
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.ProcessingContext
import java.util.function.Predicate


class HbsLocalCompletion : CompletionProvider<CompletionParameters>() {

    fun resolveJsType(type: JSType?, result: MutableList<LookupElement>, suffix: String = "") {
        val jsType = EmberUtils.handleEmberProxyTypes(type) ?: type
        val jsRecordType = jsType?.asRecordType()
        type?.asRecordType()?.propertyNames?.map { LookupElementBuilder.create(it + suffix) }?.toCollection(result)
        if (jsRecordType is JSRecordTypeImpl) {
            val names = jsRecordType.propertyNames
            result.addAll(names.map { LookupElementBuilder.create(it + suffix) })
        }
        if (jsType?.sourceElement is JSDocCommentImpl) {
            val doc = jsType.sourceElement as JSDocCommentImpl
            if (doc.tags[0].value?.reference?.resolve() != null) {
                resolve(doc.tags[0].value?.reference?.resolve()!!, result)
            }
        }
    }

    fun resolve(any: Any?, result: MutableList<LookupElement>) {
        val anything = any as? PsiElement
        var refElement: Any? = any
        if (any == null) {
            return
        }

        if (anything is PsiFile) {
            val styleSheetLanguages = arrayOf("sass", "scss", "less")
            if (styleSheetLanguages.contains(anything.language.id.lowercase())) {
                PsiTreeUtil.collectElements(anything) { it is CssRulesetList }.first().children.forEach { (it as? CssRulesetImpl)?.selectors?.forEach {
                    result.add(LookupElementBuilder.create(it.text.substring(1)))
                }}
            }
            return
        }

        if (anything is CssSelector && anything.ruleset?.block != null) {
            anything.ruleset!!.block!!.children.map { it as? CssRulesetImpl }.filterNotNull().forEach{ it.selectors.forEach {
                result.add(LookupElementBuilder.create(it.text.substring(1)))
            }}
        }

        if (anything is PsiElement && anything.references.isNotEmpty() && anything.references[0] is HbsLocalRenameReference && anything.references[0].resolve() != anything) {
            resolve(anything.references[0].resolve(), result)
            return
        }

        if (anything is EmberNamedElement) {
            resolve(anything.target, result)
            return
        }

        if (anything is PsiElement && anything.references.find { it is HbsLocalReference } != null) {
            resolve((anything.references.find { it is HbsLocalReference } as HbsLocalReference).resolveYield(), result)
            resolve((anything.references.find { it is HbsLocalReference } as HbsLocalReference).resolved as? PropertySignature, result)
            resolve(anything.references.find { it is HbReference }!!.resolve(), result)
        }

        if (anything is PsiElement && anything.reference is HbsLocalReference) {
            resolve((anything.reference as HbsLocalReference?)?.resolveYield(), result)
            resolve(anything.reference?.resolve(), result)
        }

        if (refElement is HbParam) {
            if (refElement.children.find { it is HbParam }?.text == "hash") {
                val names = refElement.children.filter { it.elementType == HbTokenTypes.HASH }.map { it.children[0].text }
                result.addAll(names.map { LookupElementBuilder.create(it) })
            }
            val ids = PsiTreeUtil.collectElements(refElement, { it.elementType == HbTokenTypes.ID && it !is LeafPsiElement })
            if (ids.isNotEmpty()) {
                resolve(ids.last(), result)
            }
        }

        if (anything is HbData) {
            resolve(anything.children.firstOrNull(), result)
        }

        if (anything is XmlAttribute) {
            resolve(anything.descriptor?.declaration, result)
        }

        val dereferencedHelper = EmberUtils.handleEmberHelpers(anything)
        if (dereferencedHelper != null) {
            resolve(dereferencedHelper, result)
        }

        if (refElement is JSFunction) {
            resolveJsType(refElement.returnType, result)
        }

        if (refElement is JSTypeOwner) {
            resolveJsType(refElement.jsType, result)
        }

        if (refElement is JSTypedEntity) {
            resolveJsType(refElement.jsType, result)
        }

        if (refElement is JSField) {
            if (refElement is JSVariableImpl<*, *> && refElement.doGetExplicitlyDeclaredType() != null) {
                val jstype = refElement.doGetExplicitlyDeclaredType()
                resolveJsType(jstype, result)
            }
        }

        val followed = EmberUtils.followReferences(anything)

        if (followed == anything) {
            return
        }

        resolve(followed, result)
    }

    fun addHelperCompletions(element: PsiElement, result: MutableList<LookupElement>, currentParam: PsiElement?) {
        val file = EmberUtils.followReferences(element.children.firstOrNull())
        val hashNames = element.parent.children.filter { it is HbHash }.map { (it as HbHash).hashName }
        val params = element.parent.children.filter { it is HbParam }

        val isLiteral = PsiTreeUtil.findChildOfType(currentParam, HbStringLiteral::class.java) != null
                || PsiTreeUtil.findChildOfType(currentParam, HbNumberLiteral::class.java) != null

        val map = EmberUtils.getArgsAndPositionals(element)

        if (currentParam is HbHash) {
            val options = map.namedOptions.getOrDefault(currentParam.hashName, emptyList()).toMutableList()
            if (options.contains("___keyof__")) {
                options.remove("___keyof__")
                if (element.text == "each") {
                    val typeRef = EmberUtils.handleEmberHelpers(element)
                    resolve(typeRef, result)
                }
            }
            if (isLiteral) {
                options.replaceAll { it.replace("'", "").replace("\"", "") }
            }
            result.addAll(options.map { LookupElementBuilder.create(it) })
        }

        if (!isLiteral && element.parent !is HbHash) {
            if (file is JSClass) {
                val ref = EmberUtils.getComponentReferenceData(file.containingFile)
                val args = ref.args.filter { !hashNames.contains(it.value) }
                result.addAll(args.map { LookupElementBuilder.create("${it.value}=") })
            }
            val named = map.named.filter { !hashNames.contains(it) }.map { LookupElementBuilder.create("$it=") }
            result.addAll(named.map { PrioritizedLookupElement.withPriority(it, 100.0) })
        }

        if (currentParam is HbParam) {
            val pos = params.indexOf(currentParam)
            map.positionalOptions.getOrDefault(pos, null)
                    ?.map { isLiteral.ifTrue { it.replace("'", "").replace("\"", "") } ?: it }
                    ?.map { LookupElementBuilder.create(it) }?.toCollection(result)
        }
    }

    fun addImportPathCompletions(element: PsiElement, result: CompletionResultSet) {
        if (element.elementType == HbTokenTypes.STRING && element.parents.find { it is HbMustache && it.children[1].text == "import"} != null) {
            var text = element.text.replace("IntellijIdeaRulezzz ", "")
            text = text.substring(1, text.length)
            val name = findMainProjectName(element.originalVirtualFile!!)
            if (text == "") {
                result.addElement(LookupElementBuilder.create("~/"))
                result.addElement(LookupElementBuilder.create("."))
                result.addElement(LookupElementBuilder.create(name + "/"))
            }
            var rootFolder = element.originalVirtualFile?.parentEmberModule
            if (text.startsWith(".")) {
                rootFolder = element.originalVirtualFile?.parent
                var i = 1
                while (text[i] == '.') {
                    rootFolder = rootFolder?.parent
                    i++
                }
            }

            if (text.split("/").first() == name) {
                text = text.replace(Regex("^$name/"), "~/")
            }
            if (!text.startsWith(".") && !text.startsWith("~")) {
                var parent = element.originalVirtualFile!!.parentEmberModule
                while (parent?.parentEmberModule != null) {
                    parent = parent.parentEmberModule
                }
                rootFolder = parent?.findChild("node_modules")
            }
            var path = text.split("/")
            path = path.dropLast(1)
            path.forEach {
                if (rootFolder !=  null && rootFolder!!.isEmberAddonFolder) {
                    rootFolder = rootFolder?.findChild("addon")
                }
                rootFolder = rootFolder?.findChild(it) ?: rootFolder
            }
            if (rootFolder != null) {
                val validExtensions = arrayOf("css", "js", "ts", "gts", "gjs")
                val names = rootFolder!!.children.filter { it.isDirectory || validExtensions.contains(it.name.split(".").last()) }
                        .map {
                            val name = it.name
                            LookupElementBuilder.create(name.split(".").first())
                        }
                result.addAllElements(names)
            }
        }
    }

    fun addImportCompletions(element: PsiElement, result: CompletionResultSet) {
        val imports = PsiTreeUtil.collectElements(element.containingFile, { it is HbMustache && it.children[1].text == "import" }).map { it }
        imports.forEach() {
            val importNames = it.children[2].text
                    .replace("\"", "")
                    .replace("'", "")
                    .replace("}", "")
                    .replace("{", "")
            if (importNames.contains("*")) {
                val name = importNames.split(" as ").last()
                result.addElement(LookupElementBuilder.create(name))
            }
            val names = importNames.split(",").map { it.replace(" ", "") }
            result.addAllElements(names.map { LookupElementBuilder.create(it) })
        }
    }

    fun addLocalJSCompletion(element: PsiElement, result: CompletionResultSet) {
        if (element.originalVirtualFile !is VirtualFileWindow) {
            return
        }
        val psiManager = PsiManager.getInstance(element.project)
        val f = psiManager.findFile((element.originalVirtualFile as VirtualFileWindow).delegate) ?: return
        val manager = InjectedLanguageManager.getInstance(element.project)
        val templates = PsiTreeUtil.collectElements(f) { it is ES6TaggedTemplateExpression && it.tag?.text == "hbs" }.mapNotNull { (it as ES6TaggedTemplateExpression).templateExpression }
        val tpl = templates.find {
            val injected = manager.findInjectedElementAt(f, it.startOffset)?.containingFile ?: return@find false
            val virtualFile = injected.virtualFile
            return@find virtualFile is VirtualFileWindow && virtualFile == (element.originalVirtualFile as VirtualFileWindow)
        } ?: return

        val children = PsiTreeUtil.collectElements(f) { it is JSVariable || it is ES6ImportDeclaration }
        children.forEach {
            if (it is JSVariable) {
                val useScope = JSUseScopeProvider.getBlockScopeElement(it)
                if (useScope.isAncestor(tpl)) {
                    result.addElement(LookupElementBuilder.create(it.name!!))
                }
            }

            if (it is ES6ImportDeclaration) {
                result.addAllElements(it.importSpecifiers.mapNotNull {it.alias?.name ?: it.name!! }.map { LookupElementBuilder.create(it)})
                result.addAllElements(it.importedBindings.mapNotNull { it.name }.map { LookupElementBuilder.create(it)})
            }
        }
    }

    fun addArgsCompletion(element: PsiElement, result: CompletionResultSet) {
        val cls = EmberUtils.findBackingJsClass(element)
        if (cls != null) {
            val args = EmberUtils.findComponentArgsType(cls as JSElement)
            if (args?.properties != null) {
                if (element.parent is HbData && !result.prefixMatcher.prefix.startsWith("@")) {
                    result.addAllElements(args.properties.map { LookupElementBuilder.create(it.memberName) })
                } else {
                    result.addAllElements(args.properties.map { LookupElementBuilder.create("@${it.memberName}") })
                }
            }
            val modelProp = (cls as? JSClass)?.let { it.jsType.asRecordType().properties.find { it.memberName == "model" } }
            modelProp?.let {
                if (element.parent is HbData && !result.prefixMatcher.prefix.startsWith("@")) {
                    result.addElement(LookupElementBuilder.create(it.memberName))
                } else {
                    result.addElement(LookupElementBuilder.create("@${it.memberName}"))
                }
            }
        }
    }

    fun addCandidates(element: PsiElement, name: String, completionResultSet: CompletionResultSet) {
        val file = element.containingFile
        if (file.viewProvider !is GtsFileViewProvider) {
            return
        }
        if (name.contains(".")) {
            return
        }
        if (element.parent is HbData) {
            return
        }
        val view = file.viewProvider
        val f = view.getPsi(JavaScriptSupportLoader.TYPESCRIPT) ?: view.getPsi(JavaScriptSupportLoader.JAVASCRIPT.language)
        val candidates = mutableListOf<JSImportCandidate>()
        ApplicationManager.getApplication().runReadAction {
            val keyFilter = Predicate { n: String? -> n != null && n.contains(name) }
            val info = JSImportPlaceInfo(f)
            val providers = JSImportCandidatesProvider.getProviders(info)
            JSImportCompletionUtil.processExportedElements(f, providers, keyFilter) { elements: Collection<JSImportCandidate?>, name: String? ->
                candidates.addAll(elements.filterNotNull())
            }
        }
        completionResultSet.addAllElements(candidates.map { EmberLookupElementBuilderWithCandidate.create(it, file) })
    }

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, completionResultSet: CompletionResultSet) {
        val regex = Regex("\\|.*\\|")
        var element = parameters.position
        if (element is LeafPsiElement) {
            element = element.parent
        }
        val languageService = GlintLanguageServiceProvider(element.project)
        val service = languageService.getService(element.originalVirtualFile!!)
        val txt = (element.parents.find { it is HbPathImpl || it is HbStringLiteral }?.text ?: element.text).replace("IntellijIdeaRulezzz", "")

        if(parameters.isExtendedCompletion) {
            val items = languageService.getService(element.originalVirtualFile!!)?.updateAndGetCompletionItems(element.originalVirtualFile!!, parameters)?.get()
                    ?: arrayListOf()
            if (items.size < 100) {
                completionResultSet.addAllElements(items.map { it.intoLookupElement() })
            }
        }

        if (element.containingFile.fileType is HtmlFileType && parameters.isExtendedCompletion) {
            val results = service?.updateAndGetCompletionItems(element.originalVirtualFile!!, parameters)?.get()?.map {
                if (completionResultSet.prefixMatcher.prefix == "@") {
                    LookupElementBuilder.create("@" + it.name)
                } else {
                    LookupElementBuilder.create(it.name)
                }

            }
            if (results != null && results.size < 100) {
                completionResultSet.addAllElements(results)
            }
            return
        }

        val result: MutableList<LookupElement> = mutableListOf()

        val helperElement = EmberUtils.findFirstHbsParamFromParam(element)
        if (helperElement != null && parameters.position.parent.prevSibling.elementType != HbTokenTypes.SEP) {
            val params = helperElement.parent.children.filter { it is HbParam || it is HbHash }
            val currentParam = params.find { it.textRange.contains(element.textRange) }
            addHelperCompletions(helperElement, result, currentParam)
            val r = EmberUtils.handleEmberHelpers(helperElement.parent)
            if (r != null) {
                addHelperCompletions(r.children[0].children[0], result, currentParam)
            }
        }

        if (parameters.position.parent.prevSibling.elementType == HbTokenTypes.SEP) {
            resolve(parameters.position.parent.prevSibling?.prevSibling, result)
            completionResultSet.addAllElements(result)
            return
        }

        if (element.parent is HbData) {
            addArgsCompletion(element, completionResultSet)
            completionResultSet.addAllElements(result)
            return
        }

        addArgsCompletion(element, completionResultSet)
        addImportPathCompletions(element, completionResultSet)
        addImportCompletions(element, completionResultSet)
        addCandidates(element, txt, completionResultSet)

        // find all |blocks| from mustache
        val blocks = PsiTreeUtil.collectElements(element.containingFile) { it is HbBlockWrapperImpl }
                .filter { it.children[0].text.contains(regex) }
                .filter { it.textRange.contains(element.textRange) }

        // find all |blocks| from component tags, needs html view
        val htmlView = parameters.originalFile.viewProvider.getPsi(Language.findLanguageByID("HTML")!!)
        val angleBracketBlocks = PsiTreeUtil.collectElements(htmlView, { it is XmlAttribute && it.text.startsWith("|") }).map { it.parent }

        // collect blocks which have the element as a child
        val validBlocks = angleBracketBlocks.filter { it ->
            it.textRange.contains(element.textRange)
        }
        for (block in validBlocks) {
            val attrString = block.children.filter { it is XmlAttribute }.map { it.text }.joinToString(" ")
            val names = Regex("\\|.*\\|").find(attrString)!!.groups[0]!!.value.replace("|", "").split(" ")
            completionResultSet.addAllElements(names.map { LookupElementBuilder.create(it) })
        }
        for (block in blocks) {
            val refs = block.children[0].children.filter { it.elementType == HbTokenTypes.ID }
            completionResultSet.addAllElements(refs.map { LookupElementBuilder.create(it.text) })
        }
        if ("this".startsWith(txt)) {
            completionResultSet.addElement(LookupElementBuilder.create("this"))
        }

        val mustache = parameters.position.parent
        val res = mustache?.references?.find { it.resolve() != null }
        if (res?.resolve() != null) {
            val psiExp = res.resolve()
            if (psiExp != null) {
                resolve(psiExp, result)
            }
        }

        completionResultSet.addAllElements(result)
        addLocalJSCompletion(element, completionResultSet)
    }
}
