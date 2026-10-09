package com.emberjs.navigation

import com.emberjs.index.EmberNameIndex
import com.emberjs.utils.dasherize
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSField
import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.ecmal4.JSAttributeListOwner
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.ProjectScope

/**
 * Cmd+click on the name of an injected service field navigates to the service, e.g. `session` in
 * `@service session;`, `@service('session') mySession;` or `@service declare session: SessionService;`.
 */
class EmberServiceGotoDeclarationHandler : GotoDeclarationHandler {

    override fun getGotoDeclarationTargets(source: PsiElement?, offset: Int, editor: Editor?): Array<PsiElement>? {
        val field = source?.parent as? JSField ?: return null
        if (field.nameIdentifier != source) return null
        val project = field.project
        if (DumbService.isDumb(project)) return null

        val serviceName = injectedServiceName(field) ?: return null
        val psiManager = PsiManager.getInstance(project)
        val targets = EmberNameIndex.getFilteredFiles(ProjectScope.getAllScope(project)) { it.type == "service" && it.name == serviceName }
                .mapNotNull { psiManager.findFile(it) }
        return targets.takeIf { it.isNotEmpty() }?.toTypedArray()
    }

    companion object {
        private val SERVICE_DECORATORS = setOf("service", "inject")

        /** The service a decorated field injects: the decorator's string argument, or else the dasherized field name. */
        fun injectedServiceName(field: JSField): String? {
            val attributeList = (field as? JSAttributeListOwner)?.attributeList?.takeIf { it.decorators.isNotEmpty() }
                    ?: (field.parent as? JSAttributeListOwner)?.attributeList
                    ?: return null
            val decorator = attributeList.decorators.firstOrNull { it.decoratorName in SERVICE_DECORATORS } ?: return null
            val argument = (decorator.expression as? JSCallExpression)?.arguments?.firstOrNull() as? JSLiteralExpression
            val name = argument?.stringValue ?: field.name?.dasherize() ?: return null
            return name.replace(".", "/")
        }
    }
}
