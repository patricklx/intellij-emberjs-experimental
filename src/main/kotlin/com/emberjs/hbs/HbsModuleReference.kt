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
    val internalHelpersFile = PsiFileFactory.getInstance(project).createFileFromText("intellij-emberjs/internal/helpers-stub", Language.findLanguageByID("TypeScript")!!, this::class.java.getResource("/com/emberjs/external/ember-helpers.ts").readText())
    val internalModifiersFile = PsiFileFactory.getInstance(project).createFileFromText("intellij-emberjs/internal/modifiers-stub", Language.findLanguageByID("TypeScript")!!, this::class.java.getResource("/com/emberjs/external/ember-modifiers.ts").readText())
    val internalComponentsFile = PsiFileFactory.getInstance(project).createFileFromText("intellij-emberjs/internal/components-stub", Language.findLanguageByID("TypeScript")!!, this::class.java.getResource("/com/emberjs/external/ember-components.ts").readText())


    private val internalHelpers = EmberUtils.resolveDefaultExport(internalHelpersFile) as JSObjectLiteralExpression
    private val internalModifiers = EmberUtils.resolveDefaultExport(internalModifiersFile) as JSObjectLiteralExpression
    protected val internalComponents = EmberUtils.resolveDefaultExport(internalComponentsFile) as JSObjectLiteralExpression

    private val psiManager: PsiManager by lazy { PsiManager.getInstance(project) }

    private val validParents = EmberUtils.getScopesForFile(element.containingFile.virtualFile)

    private val hasHbsImports by lazy {
        var f = element.containingFile.originalFile
        if (element.originalVirtualFile is VirtualFileWindow) {
            val psiManager = PsiManager.getInstance(element.project)
            f = psiManager.findFile((element.originalVirtualFile as VirtualFileWindow).delegate)!!
        }
        NodeModuleManager.getInstance(element.project).collectVisibleNodeModules(f.virtualFile).find { it.name == "ember-hbs-imports" }
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
            if (internalComponents.properties.map { it.name }.contains(text)) {
                val prop = internalComponents.properties.find { it.name == text }
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
                .asSequence()
                .filter { EmberUtils.isInScope(it, validParents) }
                .mapNotNull { psiManager.findFile(it) }
                .map { EmberUtils.resolveToEmber(it) }
                .take(1)
                .toList()
                .let(::createResults)
    }

    override fun getVariants(): Array<out Any?> {
        // Collect all components from the index
        return EmberNameIndex.getFilteredProjectKeys(scope) { it.type == moduleType }
                // Convert search results for LookupElements
                .filter { EmberUtils.isInScope(it.virtualFile, validParents) }
                .map { EmberLookupElementBuilder.create(it, this.element.containingFile, dots = false, useImports) }
                .toTypedArray()
    }
}
