package com.emberjs.hbs

import com.dmarcotte.handlebars.psi.impl.HbPathImpl
import com.emberjs.lookup.EmberLookupInternalElementBuilder
import com.emberjs.utils.originalVirtualFile
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.javascript.nodejs.reference.NodeModuleManager
import com.intellij.psi.PsiManager
import com.intellij.psi.util.parentsWithSelf
import com.intellij.util.ProcessingContext


class HbsBuiltinHelperCompletionProvider(val helpers: List<String>) : CompletionProvider<CompletionParameters>() {
    val lookupElements = helpers

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val virtualFile = parameters.position.originalVirtualFile
        val psiManager = PsiManager.getInstance(parameters.position.project)
        var f = virtualFile
        if (virtualFile is VirtualFileWindow) {
            f = psiManager.findFile((virtualFile as VirtualFileWindow).delegate)?.virtualFile
        }
        val hasHbsImports = NodeModuleManager.getInstance(parameters.position.project).collectVisibleNodeModules(f).find { it.name == "ember-hbs-imports" }
        val useImports = hasHbsImports != null
        val path = parameters.position.parentsWithSelf.toList().find { it is HbPathImpl }
        if (path != null && path.text.contains(".")) return
        result.addAllElements(lookupElements.map { EmberLookupInternalElementBuilder.create(parameters.originalFile, it, useImports) })
    }
}
