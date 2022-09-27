package com.emberjs.hbs

import com.emberjs.index.EmberNameIndex
import com.emberjs.lookup.EmberLookupElementBuilder
import com.emberjs.resolver.EmberName
import com.emberjs.utils.EmberUtils
import com.emberjs.utils.emberRoot
import com.emberjs.utils.originalVirtualFile
import com.intellij.javascript.nodejs.reference.NodeModuleManager
import com.intellij.lang.Language
import com.intellij.lang.ecmascript6.psi.impl.ES6ExportDefaultAssignmentImpl
import com.intellij.lang.ecmascript6.resolve.ES6PsiUtil
import com.intellij.lang.javascript.ecmascript6.TypeScriptUtil
import com.intellij.lang.javascript.psi.JSElement
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSRecordType
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.ecma6.JSTypedEntity
import com.intellij.lang.javascript.psi.ecma6.TypeScriptImplicitModule
import com.intellij.lang.javascript.psi.types.JSTypeofTypeImpl
import com.intellij.lang.javascript.refactoring.inline.TypescriptInlineTypeHandler
import com.intellij.lang.typescript.compiler.TypeScriptLanguageServiceProvider
import com.intellij.lang.typescript.compiler.TypeScriptService
import com.intellij.lang.typescript.compiler.languageService.TypeScriptLanguageServiceUtil
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
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

    private val hasHbsImports = NodeModuleManager.getInstance(element.project).collectVisibleNodeModules(element.originalVirtualFile).find { it.name == "ember-hbs-imports" || it.name == "ember-template-imports" }
    private val useImports = hasHbsImports != null


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
