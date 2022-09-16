package com.emberjs.hbs

import com.dmarcotte.handlebars.parsing.HbTokenTypes
import com.dmarcotte.handlebars.psi.HbData
import com.dmarcotte.handlebars.psi.HbMustache
import com.dmarcotte.handlebars.psi.HbParam
import com.dmarcotte.handlebars.psi.HbStringLiteral
import com.dmarcotte.handlebars.psi.impl.HbBlockWrapperImpl
import com.dmarcotte.handlebars.psi.impl.HbPathImpl
import com.emberjs.glint.GlintLanguageServiceProvider
import com.emberjs.lookup.HbsInsertHandler
import com.emberjs.psi.EmberNamedElement
import com.emberjs.utils.*
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.Language
import com.intellij.lang.ecmascript6.psi.ES6ImportDeclaration
import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.ecma6.JSTypedEntity
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.impl.JSVariableImpl
import com.intellij.lang.javascript.psi.jsdoc.impl.JSDocCommentImpl
import com.intellij.lang.javascript.psi.types.JSRecordTypeImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.css.CssRulesetList
import com.intellij.psi.css.CssSelector
import com.intellij.psi.css.impl.CssRulesetImpl
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlAttribute
import com.intellij.util.ProcessingContext
import com.intellij.codeInsight.lookup.LookupElementBuilder as IntelijLookupElementBuilder


class LookupElementBuilder {
    companion object {
        fun create(name: String): com.intellij.codeInsight.lookup.LookupElementBuilder {
            return IntelijLookupElementBuilder.create(name)
                    .withInsertHandler(HbsInsertHandler())
        }
    }
}


class HbsLocalCompletion : CompletionProvider<CompletionParameters>() {

    fun resolveJsType(jsType: JSType?, result: CompletionResultSet, suffix: String = "") {
        val jsRecordType = jsType?.asRecordType()
        if (jsRecordType is JSRecordTypeImpl) {
            val names = jsRecordType.propertyNames
            result.addAllElements(names.map { LookupElementBuilder.create(it + suffix) })
        }
        if (jsType?.sourceElement is JSDocCommentImpl) {
            val doc = jsType.sourceElement as JSDocCommentImpl
            if (doc.tags[0].value?.reference?.resolve() != null) {
                resolve(doc.tags[0].value?.reference?.resolve()!!, result)
            }
        }
    }

    fun resolve(anything: PsiElement?, result: CompletionResultSet) {
        var refElement: Any? = anything
        if (anything == null) {
            return
        }

        if (anything is PsiFile) {
            val styleSheetLanguages = arrayOf("sass", "scss", "less")
            if (styleSheetLanguages.contains(anything.language.id.lowercase())) {
                PsiTreeUtil.collectElements(anything) { it is CssRulesetList }.first().children.forEach { (it as CssRulesetImpl).selectors.forEach {
                    result.addElement(LookupElementBuilder.create(it.text.substring(1)))
                }}
            }
            return
        }

        if (anything is CssSelector && anything.ruleset?.block != null) {
            anything.ruleset!!.block!!.children.map { it as? CssRulesetImpl }.filterNotNull().forEach{ it.selectors.forEach {
                result.addElement(LookupElementBuilder.create(it.text.substring(1)))
            }}
        }

        if (anything.references.isNotEmpty() && anything.references[0] is HbsLocalRenameReference && anything.references[0].resolve() != anything) {
            resolve(anything.references[0].resolve(), result)
            return
        }

        if (anything is EmberNamedElement) {
            resolve(anything.target, result)
            return
        }

        if (anything.references.find { it is HbsLocalReference } != null) {
            resolve((anything.references.find { it is HbsLocalReference } as HbsLocalReference).resolveYield(), result)
            resolve(anything.references.find { it is HbsLocalReference }!!.resolve(), result)
        }

        if (anything.reference is HbsLocalReference) {
            resolve((anything.reference as HbsLocalReference?)?.resolveYield(), result)
            resolve(anything.reference?.resolve(), result)
        }

        if (refElement is HbParam) {
            if (refElement.children.find { it is HbParam }?.text == "hash") {
                val names = refElement.children.filter { it.elementType == HbTokenTypes.HASH }.map { it.children[0].text }
                result.addAllElements(names.map { LookupElementBuilder.create(it) })
            }
            val ids = PsiTreeUtil.collectElements(refElement, { it.elementType == HbTokenTypes.ID && it !is LeafPsiElement })
            if (ids.isNotEmpty()) {
                resolve(ids.last(), result)
            }
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

    fun addHelperCompletions(element: PsiElement, result: CompletionResultSet) {
        val file = EmberUtils.followReferences(element.children.firstOrNull())
        var func: JSFunction? = null
        if (file is JSFunction) {
            func = file
        }
        if (file is JSClass) {
            val ref = EmberUtils.getComponentReferenceData(file.containingFile)
            result.addAllElements(ref.args.map { LookupElementBuilder.create(it.value + "=") })
        }
        if (file is PsiFile) {
            func = EmberUtils.resolveHelper(file)
        }

        if (func != null) {
            val hash = func.parameterList?.parameters?.last()
            resolveJsType(hash?.jsType ?: hash?.inferredType, result, "=")
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
                val validExtensions = arrayOf("css", "js", "ts")
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
        val f = psiManager.findFile((element.originalVirtualFile as VirtualFileWindow).delegate)
        f?.children?.forEach {

            if (it is JSVarStatement) {
                result.addAllElements(it.declarations.map { LookupElementBuilder.create(it.name!!) })
            }

            if (it is ES6ImportDeclaration) {
                result.addAllElements(it.importSpecifiers.map { LookupElementBuilder.create(it.alias?.name ?: it.name!! )})
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

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val regex = Regex("\\|.*\\|")
        var element = parameters.position
        if (element is LeafPsiElement) {
            element = element.parent
        }
        val languageService = GlintLanguageServiceProvider(element.project)
        val txt = (element.parents.find { it is HbPathImpl || it is HbStringLiteral }?.text ?: element.text).replace("IntellijIdeaRulezzz", "")

        val helperElement = EmberUtils.findFirstHbsParamFromParam(element)
        if (helperElement != null) {
            addHelperCompletions(helperElement, result)
            val r = EmberUtils.handleEmberHelpers(helperElement.parent)
            if (r != null) {
                addHelperCompletions(r.children[0].children[0], result)
            }
        }

        if (parameters.position.parent.prevSibling.elementType == HbTokenTypes.SEP) {
            resolve(parameters.position.parent.prevSibling?.prevSibling, result)
            val items = languageService.getService(element.originalVirtualFile!!)?.updateAndGetCompletionItems(element.originalVirtualFile!!, parameters)?.get() ?: arrayListOf()
            result.addAllElements(items.map { it.intoLookupElement() })
            return
        }

        if (element.parent is HbData) {
            addArgsCompletion(element, result)
            return
        }

        addArgsCompletion(element, result)
        addImportPathCompletions(element, result)
        addImportCompletions(element, result)

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
            result.addAllElements(names.map { LookupElementBuilder.create(it) })
        }
        for (block in blocks) {
            val refs = block.children[0].children.filter { it.elementType == HbTokenTypes.ID }
            result.addAllElements(refs.map { LookupElementBuilder.create(it.text) })
        }
        if ("this".startsWith(txt)) {
            result.addElement(LookupElementBuilder.create("this"))
        }

        val mustache = parameters.position.parent
        val res = mustache?.references?.find { it.resolve() != null }
        if (res?.resolve() != null) {
            val psiExp = res.resolve()
            if (psiExp != null) {
                resolve(psiExp, result)
            }
        }

        addLocalJSCompletion(element, result)
    }
}
