package com.emberjs.hbs

import com.emberjs.index.EmberNameIndex
import com.emberjs.lookup.EmberLookupElementBuilder
import com.emberjs.resolver.EmberName
import com.emberjs.utils.EmberUtils
import com.emberjs.utils.originalVirtualFile
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.javascript.nodejs.reference.NodeModuleManager
import com.intellij.lang.Language
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.PsiElementResolveResult.createResults
import com.intellij.psi.search.ProjectScope

open class HbsModuleReference(element: PsiElement, val moduleType: String) :
        PsiPolyVariantReferenceBase<PsiElement>(element, TextRange(0, element.textLength), true) {

    val project = element.project
    private val scope = ProjectScope.getAllScope(project)
    private val internalHelpersFile = PsiFileFactory.getInstance(project).createFileFromText("intellij-emberjs/internal/helpers-stub", Language.findLanguageByID("TypeScript")!!, this::class.java.getResource("/com/emberjs/external/ember-helpers.ts").readText())
    private val internalModifiersFile = PsiFileFactory.getInstance(project).createFileFromText("intellij-emberjs/internal/modifiers-stub", Language.findLanguageByID("TypeScript")!!, this::class.java.getResource("/com/emberjs/external/ember-modifiers.ts").readText())
    private val internalComponentsFile = PsiFileFactory.getInstance(project).createFileFromText("intellij-emberjs/internal/components-stub", Language.findLanguageByID("TypeScript")!!, this::class.java.getResource("/com/emberjs/external/ember-components.ts").readText())


    private val internalHelpers = EmberUtils.resolveDefaultExport(internalHelpersFile) as JSObjectLiteralExpression
    private val internalModifiers = EmberUtils.resolveDefaultExport(internalModifiersFile) as JSObjectLiteralExpression
    protected val internalComponents = EmberUtils.resolveDefaultExport(internalComponentsFile) as JSObjectLiteralExpression

    private val psiManager: PsiManager by lazy { PsiManager.getInstance(project) }

    private val hasHbsImports by lazy {
        var f = element.containingFile.originalFile
        if (element.originalVirtualFile is VirtualFileWindow) {
            val psiManager = PsiManager.getInstance(element.project)
            f = psiManager.findFile((element.originalVirtualFile as VirtualFileWindow).delegate)!!
        }
        NodeModuleManager.getInstance(element.project).collectVisibleNodeModules(f.virtualFile).find { it.name == "ember-hbs-imports" || it.name == "ember-template-imports" }
    }
    private val useImports by lazy { hasHbsImports != null }


    open fun matches(module: EmberName) =
            module.type == moduleType && module.name == value.replace("'", "").replace("\"", "")

    override fun multiResolve(incompleteCode: Boolean): Array<out ResolveResult> {
        // Collect all components from the index
        val text = element.text.replace("'", "").replace("\"", "")

        if (moduleType == "helper") {
            if (internalHelpers.properties.map { it.name }.contains(text)) {
                val prop = internalHelpers.properties.find { it.name == text }
                return createResults((prop?.jsType?.sourceElement as JSReferenceExpression).resolve())
            }
        }

        if (moduleType == "component") {
            if (internalHelpers.properties.map { it.name }.contains(text)) {
                val prop = internalHelpers.properties.find { it.name == text }
                return createResults((prop?.jsType?.sourceElement as JSReferenceExpression).resolve())
            }
        }

        if (moduleType == "modifier") {
            if (internalModifiers.properties.map { it.name }.contains(text)) {
                val prop = internalModifiers.properties.find { it.name == text }
                return createResults((prop?.jsType?.sourceElement as JSReferenceExpression).resolve())
            }
        }

        return EmberNameIndex.getFilteredFiles(scope) { matches(it) }
                // Convert search results for LookupElements
                .map { psiManager.findFile(it) }
                .filterNotNull()
                .map { EmberUtils.resolveToEmber(it) }
                .take(1)
                .let(::createResults)
    }

    override fun getVariants(): Array<out Any?> {
        // Collect all components from the index
        return EmberNameIndex.getFilteredProjectKeys(scope) { it.type == moduleType }
                // Convert search results for LookupElements
                .map { EmberLookupElementBuilder.create(it, dots = false, useImports) }
                .toTypedArray()
    }
}
