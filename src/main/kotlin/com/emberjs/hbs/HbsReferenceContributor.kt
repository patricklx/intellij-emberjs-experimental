package com.emberjs.hbs

import com.dmarcotte.handlebars.psi.HbMustache
import com.dmarcotte.handlebars.psi.HbParam
import com.dmarcotte.handlebars.psi.impl.HbStatementsImpl
import com.emberjs.EmberAttrDec
import com.emberjs.glint.GlintLanguageServiceProvider
import com.emberjs.index.EmberNameIndex
import com.emberjs.psi.EmberNamedAttribute
import com.emberjs.psi.EmberNamedElement
import com.emberjs.translations.EmberTranslationHbsReferenceProvider
import com.emberjs.utils.*
import com.intellij.lang.Language
import com.intellij.lang.javascript.psi.JSElementBase
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.ecma6.TypeScriptModule
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.XmlTagPattern
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeDecl
import com.intellij.psi.xml.XmlTag
import com.intellij.util.ProcessingContext

fun filter(element: PsiElement, fn: (PsiElement) -> PsiReference?): PsiReference? {
    if (element.text.contains(".")) {
        return null
    }
    val res = HbsLocalReference.createReference(element)
    if (res?.resolve() != null && (res.resolve() as? EmberNamedElement)?.target != element) {
        return null
    }
    return fn(element)
}


class ImportPathReferencesProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<RangedReference> {
        val psiManager: PsiManager by lazy { PsiManager.getInstance(element.project) }
        var path = element.text.substring(1, element.text.lastIndex)
        var resolvedFile: PsiFileSystemItem? = element.originalElement.containingFile.parent ?: element.originalElement.containingFile.originalFile.parent
        val parts = path.split("/")

        if (element.originalVirtualFile == null) {
            return arrayOf()
        }

        if (element.originalVirtualFile?.parentEmberModule == null) {
            return arrayOf()
        }

        val name = findMainProjectName(element.originalVirtualFile!!)
        var isInTestFolder = false
        if (element.originalVirtualFile!!.path.contains("$name/tests") || element.originalVirtualFile!!.path.contains("$name/test-app")) {
            isInTestFolder = true
        }

        if (path.startsWith("~") && isInTestFolder) {
            val folder = element.originalVirtualFile!!.parents.find { it.path.endsWith("$name/tests/dummy") }
            resolvedFile = psiManager.findDirectory(folder!!)
        }

        if (path.startsWith(element.originalVirtualFile!!.parentEmberModule!!.name)) {
            path = path.replace(Regex("^${element.originalVirtualFile!!.parentEmberModule!!.name}/"), "~/")
        }

        if (path.startsWith("~")) {
            resolvedFile = psiManager.findDirectory(element.originalVirtualFile!!.parentEmberModule!!)
        }

        if (!path.startsWith("~") && !path.startsWith(".")) {
            val parent = element.originalVirtualFile!!.emberRoot
            resolvedFile = parent?.let { psiManager.findDirectory(it) }
            resolvedFile = resolvedFile?.findSubdirectory("node_modules")
        }

        val files = parts.mapIndexed { i, s ->
            if (s == "." || s == "~") {
                // do nothing
            }
            else if (i == 0 && s == name) {
                val parent = element.originalVirtualFile!!.emberRoot
                resolvedFile = parent?.let { psiManager.findDirectory(it) }
            }
            else if (s == "..") {
                resolvedFile = resolvedFile?.parent
            } else {
                if (resolvedFile is PsiDirectory) {
                    var dir = resolvedFile as PsiDirectory?
                    if (resolvedFile!!.virtualFile.isEmberAddonFolder || resolvedFile!!.virtualFile.isEmberFolder) {
                        dir = dir?.findSubdirectory("addon") ?: dir?.findSubdirectory("app")
                    }
                    val subdir = dir?.findSubdirectory(s)
                    val file = dir?.children?.find { it is PsiFile && (it.name == s || it.name.split(".").first() == s) } as PsiFileSystemItem?
                    resolvedFile = if (i == parts.count() - 1) {
                        file ?: subdir
                    } else {
                        subdir ?: file
                    }
                } else {
                    resolvedFile = null
                }
            }
            resolvedFile
        }.toMutableList()

        if (resolvedFile is PsiDirectory) {
            resolvedFile = (resolvedFile as PsiDirectory).findFile("index.ts")
                            ?: (resolvedFile as PsiDirectory).findFile("index.d.ts")
                            ?: (resolvedFile as PsiDirectory).findFile("index.hbs")
                            ?: (resolvedFile as PsiDirectory).findFile("component.ts")
                            ?: (resolvedFile as PsiDirectory).findFile("component.d.ts")
                            ?: (resolvedFile as PsiDirectory).findFile("template.hbs")
                            ?: resolvedFile
        }

        var file: PsiFile?
        if (resolvedFile is PsiDirectory) {
            val resolvedPath = resolvedFile!!.virtualFile.path.replace("addon/", "").replace("app/", "")
            val scope = GlobalSearchScope.allScope(element.project)
            file = EmberNameIndex.getFilteredFiles(scope) { it.type == "component" && it.importPath.isNotBlank() && resolvedPath.endsWith(it.importPath) }
                    // Filter out files that are not related to this pr
                    .map { psiManager.findFile(it) }
                    .firstOrNull()
            file = file ?: EmberNameIndex.getFilteredFiles(scope) { it.type == "template" && it.importPath.isNotBlank() && resolvedPath.endsWith(it.importPath) }
                    // Filter out files that are not related to this pr
                    .map { psiManager.findFile(it) }
                    .firstOrNull()
        } else {
            file = resolvedFile as PsiFile?
        }
        files[files.lastIndex] = file ?: files.last()
        fun glintRes(it: PsiElement?): PsiElement? {
            val psiFile = PsiManager.getInstance(element.project).findFile(element.originalVirtualFile!!)
            val document = PsiDocumentManager.getInstance(element.project).getDocument(psiFile!!)!!
            val languageService = GlintLanguageServiceProvider(element.project)
            val service = languageService.getService(element.originalVirtualFile!!)
            val res = service?.getNavigationFor(document, element)?.firstOrNull()
            return res?.let {
                if (it.parent is JSLiteralExpression) {
                    return@let it.parent.parent as? TypeScriptModule ?: it
                }
                it.containingFile ?: it
            } ?: it
        }
        return files.mapIndexed() { index, it ->
            val p = parts.subList(0, index).joinToString("/")
            val offset: Int
            if (p.isEmpty()) {
                offset = 1
            } else {
                offset = p.length + 2
            }
            val range = TextRange(offset, offset + parts[index].length)
            if (index == files.lastIndex || it == null) {
                return@mapIndexed RangedReference(element, glintRes(it), range)
            }
            RangedReference(element, it, range)
        }.toTypedArray()
    }
}

class ContentReferencesProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val range = element.textRange
        val htmlView = element.containingFile.viewProvider.getPsi(Language.findLanguageByID("HTML")!!)
        val tags = PsiTreeUtil.collectElements(htmlView) { it is XmlTag && range.contains(it.textRange) }
        return tags.map { it as XmlTag }.filter { it.references.find { it is HbReference } != null }.map {
            RangedReference(element, it.references.find { it is HbReference }!!, TextRange(it.textOffset + 1, it.textOffset + 1 + it.name.length))
        }.toTypedArray()
    }
}

class ImportNameReferencesProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val lookInFile = element.text.contains("{") || element.text.contains("}")
        val names = element.text
                .replace("'", "")
                .replace("\"", "")
                .replace("{", "")
                .replace("}", "")
                .split(",")
        val named = names.map {
            if (it.contains(" as ")) {
                it.split(" as ").first()
            } else {
                it
            }
        }.map { it.lowercase().replace(" ", "") }
        val mustache = element.parents.find { it is HbMustache }!!
        val path = mustache.children.findLast { it is HbParam }
        var fileRef = path?.children?.get(0)?.children?.get(0)?.references?.lastOrNull()?.resolve()
        if (fileRef is EmberNamedElement) {
            fileRef = fileRef.target
        }
        if (fileRef is PsiDirectory) {
            return named
                    .map { fileRef.findFile(it) ?: fileRef.findSubdirectory(it) ?: fileRef }
                    .mapIndexed { index, it ->
                        if (it is PsiDirectory) {
                            val subdir = it.findSubdirectory(named[index])
                            if (subdir != null) {
                                subdir.files.find { it.name.split(".").first() == "component" }
                                ?: subdir.files.find { it.name.split(".").first() == "template" }
                            } else{
                                it.files.find { it.name.split(".").first() == named[index] }
                                        ?: it.files.find { it.name.split(".").first() == "component" }
                                        ?: it.files.find { it.name.split(".").first() == "template" }
                            }

                        } else {
                            it
                        }
                    }
                    .map { (it as? PsiFile)?.let { EmberUtils.resolveToEmber(it) }  }
                    .mapIndexed { index, it -> val r = Regex("\\b${named[index]}\\b", RegexOption.IGNORE_CASE).find(element.text)!!.range; RangedReference(element, it, TextRange(r.first, r.last + 1)) }
                    .toTypedArray()
        }

        if ((fileRef is PsiFile || fileRef is TypeScriptModule) && lookInFile) {
            return named
                    .mapNotNull { name ->
                        PsiTreeUtil.collectElements(fileRef) { (it as? JSElementBase)?.isExported == true }
                        .map { it as JSElementBase }
                        .find { it.name == name }
                    }
                    .mapIndexed { index, it ->
                        it
                    }
                    .map { EmberUtils.resolveToEmber(it as? PsiElement) }
                    .mapIndexed { index, it -> val r = Regex("\\b${named[index]}\\b", RegexOption.IGNORE_CASE).find(element.text)!!.range; RangedReference(element, it, TextRange(r.first, r.last + 1)) }
                    .toTypedArray()
        }
        if (fileRef == null) {
            return emptyArray()
        }
        val ref = EmberUtils.resolveToEmber(fileRef)
        return arrayOf(RangedReference(element, ref, TextRange(0, element.textLength)))
    }
}

class HbsReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        with(registrar) {
            registerReferenceProvider(XmlTagPattern.Capture(), TagReferencesProvider())
            register(PlatformPatterns.psiElement(XmlAttribute::class.java)) { toAttributeReference(it as XmlAttribute) }
            register(HbsPatterns.SIMPLE_MUSTACHE_NAME) { filter(it) { HbsComponentReference(it) } }
            register(HbsPatterns.BLOCK_MUSTACHE_NAME) { filter(it) { HbsComponentReference(it) } }
            register(HbsPatterns.MUSTACHE_ID) { HbsLocalReference.createReference(it) }
            register(HbsPatterns.SIMPLE_MUSTACHE_NAME) { filter(it) { HbsModuleReference(it, "helper") } }
            register(HbsPatterns.SIMPLE_MUSTACHE_NAME) { filter(it) { HbsModuleReference(it, "modifier") } }
            register(HbsPatterns.SUB_EXPR_NAME) { filter(it) { HbsModuleReference(it, "helper") } }
            register(HbsPatterns.COMPONENT_KEY) { HbsComponentReference(it) }
            register(HbsPatterns.COMPONENT_KEY_IN_SEXPR) { HbsComponentReference(it) }
//            registerReferenceProvider(HbsPatterns.CONTENT, ContentReferencesProvider())
            registerReferenceProvider(HbsPatterns.IMPORT_NAMES, ImportNameReferencesProvider())
            registerReferenceProvider(HbsPatterns.IMPORT_PATH_REF, ImportPathReferencesProvider())
            registerReferenceProvider(HbsPatterns.LINK_TO_BLOCK_TARGET, HbsLinkToReferenceProvider())
            registerReferenceProvider(HbsPatterns.LINK_TO_SIMPLE_TARGET, HbsLinkToReferenceProvider())
            registerReferenceProvider(HbsPatterns.TRANSLATION_KEY, EmberTranslationHbsReferenceProvider())
            registerReferenceProvider(HbsPatterns.TRANSLATION_KEY_IN_SEXPR, EmberTranslationHbsReferenceProvider())
        }
    }

    private fun PsiReferenceRegistrar.register(pattern: ElementPattern<out PsiElement>, fn: (PsiElement) -> PsiReference?) {
        registerReferenceProvider(pattern, object : PsiReferenceProvider() {
            override fun getReferencesByElement(element: PsiElement, context: ProcessingContext) = arrayOf(fn(element)).filterNotNull().toTypedArray()
        })
    }
}

