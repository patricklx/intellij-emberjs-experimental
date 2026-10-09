package com.emberjs.hbs

import com.emberjs.index.EmberNameIndex
import com.emberjs.navigation.EmberNavigationTargets
import com.emberjs.resolver.EmberName
import com.emberjs.utils.EmberUtils
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.ProjectScope

/**
 * Route templates (`app/templates/<route>.hbs/.gjs/.gts`, or a pod's `template.*`) receive `@controller` and
 * `@model`: the route's controller instance and the value returned by the route's `model()` hook. This is also
 * what `RouteTemplate(<template>...</template>)` from ember-route-template passes in.
 */
object EmberRouteTemplates {

    /** The route name of the template [element] is in, or null if it isn't a route template. */
    fun routeName(element: PsiElement): String? {
        val file = element.containingFile?.originalFile?.virtualFile ?: return null
        val name = EmberName.from(file) ?: return null
        if (name.type != "template" || name.isComponentTemplate) return null
        return name.name
    }

    /** The class of the route's controller, for `@controller`. */
    fun controllerClass(element: PsiElement): JSClass? = defaultExportClass(element, "controller")

    /** The route's `model()` hook, for `@model` (and `@controller.model` when the controller doesn't declare it). */
    fun modelHook(element: PsiElement): JSFunction? = defaultExportClass(element, "route")?.findFunctionByName("model")

    /** Whether [element] is declared in the app's own code, rather than in a library or type definitions. */
    fun isAppSource(element: PsiElement): Boolean {
        val file = element.containingFile?.originalFile?.virtualFile ?: return false
        return !file.name.endsWith(".d.ts") && "/node_modules/" !in file.path
    }

    private fun defaultExportClass(element: PsiElement, type: String): JSClass? {
        val route = routeName(element) ?: return null
        val project = element.project
        val context = element.containingFile?.originalFile?.virtualFile
        val files = EmberNameIndex.getFilteredFiles(ProjectScope.getAllScope(project)) { it.type == type && it.name == route }
        val psiManager = PsiManager.getInstance(project)
        return EmberNavigationTargets.preferResolvedByApp(context, files)
                .firstNotNullOfOrNull { file -> psiManager.findFile(file)?.let { EmberUtils.findDefaultExportClass(it) } }
    }
}
