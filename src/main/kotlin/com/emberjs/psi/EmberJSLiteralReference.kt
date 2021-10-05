package com.emberjs.psi

import com.emberjs.index.EmberNameIndex
import com.emberjs.lookup.EmberLookupElementBuilder
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult.createResults
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.search.ProjectScope

class EmberJSLiteralReference(element: JSLiteralExpression, val types: Iterable<String>) :
        PsiPolyVariantReferenceBase<JSLiteralExpression>(element, true) {

    val project = element.project
    private val scope = ProjectScope.getAllScope(project)

    private val psiManager: PsiManager by lazy { PsiManager.getInstance(project) }

    override fun multiResolve(incompleteCode: Boolean): Array<out ResolveResult> {
        val value = element.value
        if (value !is String)
            return emptyArray()

        return createResults(resolve(value.replace(".", "/")))
    }

    private fun resolve(value: String): Collection<PsiElement> {
        val names = arrayOf(value, value.removeSuffix("s"))

        // Collect all matching modules from the index
        return EmberNameIndex.getFilteredProjectKeys(scope) { it.type in types && it.name in names }

                // Filter out components that are not related to this project
                .flatMap { EmberNameIndex.getContainingFiles(it, scope) }

                // Lookup corresponding PsiFiles
                .map { psiManager.findFile(it) }
                .filterNotNull()
    }

    override fun getVariants(): Array<out Any> {
        return EmberNameIndex.getFilteredProjectKeys(scope) { it.type == types.firstOrNull() }

                // Filter out modules that are not related to this project
                .filter { EmberNameIndex.hasContainingFiles(it, scope) }

                // Convert search results for LookupElements
                .map { EmberLookupElementBuilder.create(it) }
                .toTypedArray()
    }
}
