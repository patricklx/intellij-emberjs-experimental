package com.emberjs.xml

import com.dmarcotte.handlebars.parsing.HbTokenTypes
import com.dmarcotte.handlebars.psi.HbHash
import com.dmarcotte.handlebars.psi.HbMustache
import com.dmarcotte.handlebars.psi.HbParam
import com.dmarcotte.handlebars.psi.HbStringLiteral
import com.dmarcotte.handlebars.psi.impl.HbBlockWrapperImpl
import com.emberjs.gts.GtsFileViewProvider
import com.emberjs.hbs.HbReference
import com.emberjs.icons.EmberIconProvider
import com.emberjs.index.EmberNameIndex
import com.emberjs.lookup.EmberLookupInternalElementBuilder
import com.emberjs.lookup.HbsInsertHandler
import com.emberjs.psi.EmberNamedElement
import com.emberjs.resolver.EmberName
import com.emberjs.utils.EmberUtils
import com.emberjs.utils.originalVirtualFile
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.completion.XmlTagInsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder as LookupElementBuilderIntelij
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.javascript.nodejs.reference.NodeModuleManager
import com.intellij.lang.Language
import com.intellij.lang.ecmascript6.resolve.ES6PsiUtil
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.lang.javascript.JavaScriptSupportLoader
import com.intellij.lang.javascript.completion.JSImportCompletionUtil
import com.intellij.lang.javascript.modules.JSImportPlaceInfo
import com.intellij.lang.javascript.modules.imports.JSImportAction
import com.intellij.lang.javascript.modules.imports.JSImportCandidate
import com.intellij.lang.javascript.modules.imports.JSImportCandidateWithExecutor
import com.intellij.lang.javascript.modules.imports.providers.JSImportCandidatesProvider
import com.intellij.lang.javascript.psi.ecma6.ES6TaggedTemplateExpression
import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parents
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.containers.ContainerUtil
import com.intellij.xml.XmlTagNameProvider
import java.util.function.Predicate


class LookupElementBuilder() {
    companion object {
        fun create(obj: Any): com.intellij.codeInsight.lookup.LookupElementBuilder {
            return LookupElementBuilderIntelij.create(obj).withInsertHandler(XmlTagInsertHandler())
        }
        fun create(obj: Any, name: String): com.intellij.codeInsight.lookup.LookupElementBuilder {
            return LookupElementBuilderIntelij.create(obj, name).withInsertHandler(XmlTagInsertHandler())
        }
    }
}

class EmberTagNameProvider : XmlTagNameProvider {
    private fun resolve(anything: PsiElement?, path: MutableList<String>, result: MutableList<LookupElement>, visited: MutableSet<PsiElement> = mutableSetOf()) {
        var refElement: Any? = anything
        if (anything == null || visited.contains(anything)) {
            return
        }

        visited.add(anything)

        val resolvedHelper = EmberUtils.handleEmberHelpers(anything)
        if (resolvedHelper != null) {
            resolve(resolvedHelper, path, result, visited)
            return
        }

        if (anything is XmlAttribute) {
            resolve(anything.descriptor?.declaration, path, result, visited)
            return
        }

        if (anything is EmberNamedElement) {
            resolve(anything.target, path, result, visited)
            return
        }

        if (anything.references.find { it is HbReference } != null) {
            resolve(anything.references.find { it is HbReference }!!.resolve(), path, result, visited)
            return
        }

        if (anything.reference is HbReference && !visited.contains(anything.reference?.resolve())) {
            resolve(anything.reference?.resolve(), path, result, visited)
            return
        }

        if (refElement is HbMustache && refElement.text.startsWith("{{yield")) {
            refElement.children.filter { it is HbParam }.forEach { resolve(it, path, result, visited) }
            return
        }

        if (refElement is HbParam) {
            if (refElement.children.find { it is HbParam }?.text == "hash") {
                val names = refElement.children.filter { it.elementType == HbTokenTypes.HASH }.map { it.children[0].text }
                result.addAll(names.map {
                    val p = path.toMutableList()
                    p.add(it)
                    LookupElementBuilder.create(p.joinToString("."))
                })
                return
            }
            val ids = PsiTreeUtil.collectElements(refElement, { it.elementType == HbTokenTypes.ID && it !is LeafPsiElement })
            if (ids.size == 1 && ids.first().elementType !is HbStringLiteral) {
                resolve(ids.first(), path, result, visited)
                return
            }
            return
        }

        val dereferenceYield = EmberUtils.findTagYieldAttribute(anything)
        if (dereferenceYield != null) {
            val name = (anything as EmberAttrDec).name.replace("|", "")
            val angleBracketBlock = anything.context
            val startIdx = angleBracketBlock.attributes.indexOfFirst { it.text.startsWith("|") }
            val endIdx = angleBracketBlock.attributes.size
            val params = angleBracketBlock.attributes.toList().subList(startIdx, endIdx)
            val refPsi = params.find { Regex("\\|*.*\\b$name\\b.*\\|*").matches(it.text) }
            val blockParamIdx = params.indexOf(refPsi)
            val param = dereferenceYield.reference?.resolve()?.children?.filter { it is HbParam }?.getOrNull(blockParamIdx)
            resolve(param, path, result, visited)
            return
        }

        val p = path.toMutableList()
        p.add(anything.text)
        result.add(LookupElementBuilder.create(p.joinToString(".")))
    }

    private fun fromLocalParams(element: XmlTag, result: MutableList<LookupElement>) {
        val regex = Regex("\\|.*\\|")
        val txt = element.name.replace("IntellijIdeaRulezzz", "").split(".").first()
        // find all |blocks| from mustache
        val hbsView = element.containingFile.viewProvider.getPsi(Language.findLanguageByID("Handlebars")!!)
        val blocks = PsiTreeUtil.collectElements(hbsView) { it is HbBlockWrapperImpl }
                .filter { it.children[0].text.contains(regex) }
                .filter { it.textRange.contains(element.textRange) }

        // find all |blocks| from component tags, needs html view
        val htmlView = element.containingFile.viewProvider.getPsi(Language.findLanguageByID("HTML")!!)
        val angleBracketBlocks = PsiTreeUtil.collectElements(htmlView, { it is XmlAttribute && it.text.startsWith("|") }).map { it.parent }

        // collect blocks which have the element as a child
        val validBlocks = angleBracketBlocks.filter { it ->
            it.textRange.contains(element.textRange)
        }
        for (block in validBlocks) {
            val attrString = block.children.filter { it is XmlAttribute }.map { it.text }.joinToString(" ")
            val names = Regex("\\|.*\\|").find(attrString)!!.groups[0]!!.value.replace("|", "").split(" ")
            result.addAll(names.map { LookupElementBuilder.create(it) })
            for (attr in block.children.filter { it is XmlAttribute }.reversed()) {
                val tagName = (attr as XmlAttribute).name.replace("|", "")
                if (tagName.startsWith(txt)) {
                    resolve(attr, mutableListOf(tagName), result)
                }
                if (attr.name.startsWith("|")) {
                    break
                }
            }
        }
        for (block in blocks) {
            val refs = block.children[0].children.filter { it.elementType == HbTokenTypes.ID }
            result.addAll(refs.map { LookupElementBuilder.create(it.text) })
            refs.forEach {
                resolve(it, mutableListOf(it.text), result)
            }
        }

        // find from named block yields  {{yield to='x'}}
        val angleComponent = element.parents.find {
            it is XmlTag && it.descriptor is EmberXmlElementDescriptor
        } as XmlTag?
        if (angleComponent != null) {
            val data = (angleComponent.descriptor as EmberXmlElementDescriptor).getReferenceData()
            val tplYields = data.yields

            for (yieldRef in tplYields) {
                val yieldblock = yieldRef.yieldBlock
                val namedYields = yieldblock.children.filter { it is HbHash && it.hashName == "to"}.map { (it as HbHash).children.last().text.replace(Regex("\"|'"), "") }
                val names: List<String>

                // if the tag has already colon, then remove it from the lookup elements, otherwise intellij will
                // add it again and it wil turn into <::name
                if (element.name.startsWith(":")) {
                    names = namedYields
                } else {
                    names = namedYields.map { ":$it" }
                }
                // needs prioritization to appear before common html tags
                result.addAll(names.map { PrioritizedLookupElement.withPriority(LookupElementBuilder.create(it), 2.0) })
            }
        }
    }

    fun fromImports(element: XmlTag, elements: MutableList<LookupElement>) {
        val insideImport = element.parents.find { it is HbMustache && it.children.getOrNull(1)?.text == "import"} != null

        if (insideImport && element.text != "from" && element.text != "import") {
            return
        }
        val hbsView = element.containingFile.viewProvider.getPsi(Language.findLanguageByID("Handlebars")!!)
        val imports = PsiTreeUtil.collectElements(hbsView, { it is HbMustache && it.children[1].text == "import" })
        imports.forEach {
            val names = it.children[2].text.replace("'", "").replace("\"", "").split(",")
            val named = names.map {
                if (it.contains(" as ")) {
                    it.split(" as ").last()
                } else {
                    it
                }
            }.map { it.replace(" ", "") }
            elements.addAll(named.map { LookupElementBuilder.create(it) })
        }
    }

    fun fromLocalJs(element: XmlTag, elements: MutableList<LookupElement>) {
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
            } ?: return
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
            return
        }

        val namedElements = PsiTreeUtil.collectElements(f!!) { it is PsiNameIdentifierOwner && it.name != null }
        val collection = namedElements.map { ES6PsiUtil.createResolver(f).getLocalElements((it as PsiNameIdentifierOwner).name!!, listOf(f)) }.flatten().toMutableList()
        collection += namedElements.map { ES6PsiUtil.createResolver(f).getTopLevelElements((it as PsiNameIdentifierOwner).name!!, false) }.flatten()
        elements.addAll(collection.map { it as? PsiNameIdentifierOwner}.filterNotNull().map { LookupElementBuilder.create(it, it.name!!) })
    }

    fun forGtsFiles(tag: XmlTag, lookupElements: MutableList<LookupElement>) {
        val info = JSImportPlaceInfo(tag.originalElement.containingFile.viewProvider.getPsi(JavaScriptSupportLoader.TYPESCRIPT))
        val tagName = tag.name.replace("IntellijIdeaRulezzz", "")
        val keyFilter = Predicate { name: String? -> name?.first()?.isUpperCase() == true && name.contains(tagName) }
        val providers = JSImportCandidatesProvider.getProviders(info)
        JSImportCompletionUtil.processExportedElements(tag, providers, keyFilter) { candidates: Collection<JSImportCandidate?>, name: String? ->
            candidates.filterNotNull().filter { it.descriptor != null }.forEach { candidate ->
                val lookupElement = LookupElementBuilder.create(candidate.element ?: candidate.name, candidate.name)
                        .withTailText(" from ${candidate.descriptor!!.moduleName }")
                        .withTypeText("component")
                        .withIcon(EmberIconProvider.getIcon("component"))
                        .withCaseSensitivity(true)
                        .withInsertHandler(object : InsertHandler<LookupElement> {
                            override fun handleInsert(context: InsertionContext, item: LookupElement) {
                                val tsFile = context.file.viewProvider.getPsi(JavaScriptSupportLoader.TYPESCRIPT)
                                val action = JSImportAction(context.editor, tag, name!!)
                                val candidateWithExecutors = JSImportCandidateWithExecutor.sortWithExecutors(candidate, tsFile)
                                if (candidateWithExecutors.size == 1) {
                                    action.executeFor((candidateWithExecutors[0] as JSImportCandidateWithExecutor), null)
                                } else {
                                    action.executeForAllVariants(null)
                                }
                                PsiDocumentManager.getInstance(context.project).doPostponedOperationsAndUnblockDocument(context.document)
                                XmlTagInsertHandler.INSTANCE.handleInsert(context, item)
                                context.commitDocument()
                            }

                        })
                lookupElements.add(lookupElement)
            }
            true
        }
    }

    override fun addTagNameVariants(elements: MutableList<LookupElement>?, tag: XmlTag, prefix: String?) {
        if (elements == null) return
        fromLocalParams(tag, elements)
        fromImports(tag, elements)
        fromLocalJs(tag, elements)
        if (tag.name.startsWith(":")) {
            return
        }
        if (tag.containingFile.viewProvider is GtsFileViewProvider) {
            forGtsFiles(tag, elements)
            elements.add(EmberLookupInternalElementBuilder.create(tag.containingFile, "Textarea", false))
            elements.add(EmberLookupInternalElementBuilder.create(tag.containingFile, "Input", false))
            elements.add(EmberLookupInternalElementBuilder.create(tag.containingFile, "LinkTo", false))
            return
        }

        val project = tag.project
        val scope = ProjectScope.getAllScope(project)
//        val useImports = false;
        var virtualFile = tag.containingFile.originalVirtualFile
        if (virtualFile is VirtualFileWindow) {
            val psiManager = PsiManager.getInstance(tag.project)
            virtualFile = psiManager.findFile((virtualFile as VirtualFileWindow).delegate)!!.virtualFile
        }
        val hasHbsImports = NodeModuleManager.getInstance(tag.project).collectVisibleNodeModules(virtualFile).find { it.name == "ember-hbs-imports" || it.name == "ember-template-imports" }
        val useImports = hasHbsImports != null

        elements.add(EmberLookupInternalElementBuilder.create(tag.containingFile, "Textarea", useImports))
        elements.add(EmberLookupInternalElementBuilder.create(tag.containingFile, "Input", useImports))
        elements.add(EmberLookupInternalElementBuilder.create(tag.containingFile, "LinkTo", useImports))

        val componentMap = hashMapOf<String, LookupElement>()

        // Collect all components from the index
        EmberNameIndex.getFilteredProjectKeys(scope) { it.type == "component" }
            // Convert search results for LookupElements
            .map { Pair(it.angleBracketsName, toLookupElement(it, useImports)) }
            .toMap(componentMap)

        // Collect all component templates from the index
        EmberNameIndex.getFilteredProjectKeys(scope) { it.isComponentTemplate }

            // Filter out components that are already in the map
            .filter { !componentMap.containsKey(it.angleBracketsName) }

            // Convert search results for LookupElements
            .map { Pair(it.angleBracketsName, toLookupElement(it, useImports)) }

            .toMap(componentMap)


        elements.addAll(componentMap.values)
    }
}

class PathKeyClass : Key<String>("PATH")
class FullPathKeyClass : Key<String>("FULLPATH")
class CandidateKeyKeyClass : Key<JSImportCandidate>("CANDIDATE")
class InsideKeyClass : Key<String>("INSIDE")
val PathKey = PathKeyClass()
val FullPathKey = FullPathKeyClass()
val CandidateKey = CandidateKeyKeyClass()
val InsideKey = InsideKeyClass()



fun toLookupElement(name: EmberName, useImports: Boolean, priority: Double = 90.0): LookupElement {
    var tagName = name.angleBracketsName
    if (useImports) {
        tagName = name.tagName
    }
    val lookupElement = LookupElementBuilder.create(name, tagName)
            .withTailText(" from ${name.importPath}")
            .withTypeText("component")
            .withIcon(EmberIconProvider.getIcon("component"))
            .withCaseSensitivity(true)
            .withInsertHandler(HbsInsertHandler())
    lookupElement.putUserData(PathKey, name.importPath)
    lookupElement.putUserData(FullPathKey, name.fullImportPath)
    return PrioritizedLookupElement.withPriority(lookupElement, priority)
}
