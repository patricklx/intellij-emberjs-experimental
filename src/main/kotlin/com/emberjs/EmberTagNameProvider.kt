package com.emberjs

import com.dmarcotte.handlebars.parsing.HbTokenTypes
import com.dmarcotte.handlebars.psi.HbHash
import com.dmarcotte.handlebars.psi.HbMustache
import com.dmarcotte.handlebars.psi.HbParam
import com.dmarcotte.handlebars.psi.HbStringLiteral
import com.dmarcotte.handlebars.psi.impl.HbBlockWrapperImpl
import com.emberjs.hbs.HbsLocalReference
import com.emberjs.hbs.TagReference
import com.emberjs.icons.EmberIconProvider
import com.emberjs.index.EmberNameIndex
import com.emberjs.lookup.HbsInsertHandler
import com.emberjs.psi.EmberNamedElement
import com.emberjs.resolver.EmberName
import com.emberjs.utils.EmberUtils
import com.emberjs.utils.parentModule
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.Language
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.html.HtmlFileImpl
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parents
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.XmlTagNameProvider

class EmberTagNameProvider : XmlTagNameProvider {
    private fun resolve(anything: PsiElement?, path: MutableList<String>, result: MutableList<LookupElement>) {
        var refElement: Any? = anything
        if (anything == null) {
            return
        }

        val resolvedHelper = EmberUtils.handleEmberHelpers(anything)
        if (resolvedHelper != null) {
            resolve(resolvedHelper, path, result)
            return
        }

        if (anything is XmlAttribute) {
            resolve(anything.descriptor?.declaration, path, result)
            return
        }

        if (anything is EmberNamedElement) {
            resolve(anything.target, path, result)
            return
        }

        if (anything.references.find { it is HbsLocalReference } != null) {
            resolve(anything.references.find { it is HbsLocalReference }!!.resolve(), path, result)
            return
        }

        if (anything.reference is HbsLocalReference) {
            resolve(anything.reference?.resolve(), path, result)
            return
        }

        if (anything.references.find { it is TagReference } != null) {
            resolve(anything.references.find { it is TagReference }!!.resolve(), path, result)
            return
        }

        if (anything.reference is TagReference) {
            resolve(anything.reference?.resolve(), path, result)
            return
        }

        if (refElement is HbMustache && refElement.text.startsWith("{{yield")) {
            refElement.children.filter { it is HbParam }.forEach { resolve(it, path, result) }
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
                resolve(ids.first(), path, result)
                return
            }
            return
        }

        val dereferenceYield = EmberUtils.findTagYield(anything)
        if (dereferenceYield != null) {
            val name = (anything as EmberAttrDec).name.replace("|", "")
            val angleBracketBlock = anything.context
            val startIdx = angleBracketBlock.attributes.indexOfFirst { it.text.startsWith("|") }
            val endIdx = angleBracketBlock.attributes.size
            val params = angleBracketBlock.attributes.toList().subList(startIdx, endIdx)
            val refPsi = params.find { Regex("\\|*.*$name.*\\|*").matches(it.text) }
            val blockParamIdx = params.indexOf(refPsi)
            val param = dereferenceYield.reference?.resolve()?.children?.filter { it is HbParam }?.getOrNull(blockParamIdx)
            resolve(param, path, result)
            return
        }

        if (anything is XmlAttribute) {
            resolve(anything.descriptor?.declaration, path, result)
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
        val blocks = PsiTreeUtil.collectElements(element.containingFile) { it is HbBlockWrapperImpl }
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

    override fun addTagNameVariants(elements: MutableList<LookupElement>?, tag: XmlTag, prefix: String?) {
        if (elements == null) return
        fromLocalParams(tag, elements)
        fromImports(tag, elements)
        if (tag.name.startsWith(":")) {
            return
        }

        elements.add(LookupElementBuilder.create("Textarea"))
        elements.add(LookupElementBuilder.create("Input"))
        elements.add(LookupElementBuilder.create("LinkTo"))

        if (!tag.containingFile.name.endsWith(".gjs")) {
            val containingFile = tag.containingFile as? HtmlFileImpl ?: return
            val language = containingFile.contentElementType?.language ?: return
            if (language.id !== "Handlebars") return
        }

        val project = tag.project
        val scope = ProjectScope.getAllScope(project)
//        val useImports = false;
        val useImports = (project.projectFile?.parentModule?.findChild("node_modules")?.findChild("ember-hbs-imports") != null)


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
val PathKey = PathKeyClass()
val FullPathKey = PathKeyClass()



fun toLookupElement(name: EmberName, useImports: Boolean, priority: Double = 90.0): LookupElement {
    var tagName = name.angleBracketsName
    if (useImports) {
        tagName = name.tagName
    }
    val lookupElement = LookupElementBuilder
            .create(tagName)
            .withTailText(" from ${name.importPath.split("/").first()}")
            .withTypeText("component")
            .withIcon(EmberIconProvider.getIcon("component"))
            .withCaseSensitivity(true)
            .withInsertHandler(HbsInsertHandler())
    lookupElement.putUserData(PathKey, name.importPath)
    lookupElement.putUserData(FullPathKey, name.fullImportPath)
    return PrioritizedLookupElement.withPriority(lookupElement, priority)
}
