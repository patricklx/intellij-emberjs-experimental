package com.emberjs.navigation

import com.emberjs.utils.emberRoot
import com.emberjs.utils.parentModule
import com.intellij.lang.ecmascript6.psi.ES6ExportSpecifier
import com.intellij.lang.ecmascript6.psi.ES6ExportSpecifierAlias
import com.intellij.lang.ecmascript6.psi.ES6ImportedBinding
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.ecma6.TypeScriptInterface
import com.intellij.lang.javascript.psi.ecma6.TypeScriptModule
import com.intellij.lang.javascript.psi.ecma6.TypeScriptPropertySignature
import com.intellij.lang.javascript.psi.stubs.JSClassIndex
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil

/**
 * Narrows Ember name index results down to the files Ember itself would resolve for an app, so that
 * Cmd+click jumps straight there instead of offering every copy found in node_modules.
 */
object EmberNavigationTargets {

    /**
     * Like Ember's resolver:
     *  1. files from the app itself win over anything in node_modules;
     *  2. of addon files, keep a package's own files (not e.g. test fixtures inside it), and only the copies
     *     the app's own node_modules/<package> resolves to (pnpm keeps one copy per version/peer combination
     *     under node_modules/.pnpm);
     *  3. within one addon, prefer the implementation over the app/ re-export.
     * Falls back to the less filtered list rather than returning nothing.
     */
    fun preferResolvedByApp(context: VirtualFile?, files: Collection<VirtualFile>): List<VirtualFile> {
        val unique = files.distinctBy { it.canonicalPath ?: it.path }
        if (unique.size <= 1) return unique

        val (inApp, inNodeModules) = unique.partition { "/node_modules/" !in it.path }
        if (inApp.isNotEmpty()) return inApp

        // Only a package's own files count, not e.g. test fixtures that are separate projects inside it.
        val ownFiles = inNodeModules.filter { file -> packageRoot(file)?.let { it == file.parentModule } == true }
                .ifEmpty { inNodeModules }

        val appRoot = context?.let { it.emberRoot ?: it.parentModule }
        val resolvedByApp = if (appRoot == null) ownFiles else ownFiles.filter { file ->
            val packageRoot = packageRoot(file) ?: return@filter false
            val installed = appRoot.findFileByRelativePath("node_modules/${packageName(packageRoot)}") ?: return@filter false
            (installed.canonicalPath ?: installed.path) == (packageRoot.canonicalPath ?: packageRoot.path)
        }.ifEmpty { ownFiles }

        return resolvedByApp
            .groupBy { packageRoot(it)?.path }
            .flatMap { (_, samePackage) -> samePackage.filterNot { isAppReExport(it) }.ifEmpty { samePackage } }
    }

    /**
     * A service registered in Ember's service registry types, e.g. the built-in `router`:
     * ```
     * declare module '@ember/service' { interface Registry { router: RouterService } }
     * ```
     * This is what `@service('router')` is typed against, so it covers built-in services and any app or addon that
     * registers its services for TypeScript/Glint, without knowing where a package keeps its files. Resolves the
     * registry entry and then its type, i.e. the service class.
     */
    fun registeredService(project: Project, name: String): PsiElement? {
        if (DumbService.isDumb(project)) return null
        val entries = mutableListOf<TypeScriptPropertySignature>()
        JSClassIndex.processElements("Registry", project, GlobalSearchScope.allScope(project)) { element ->
            val registry = element as? TypeScriptInterface
            if (registry != null && isEmberServiceModule(registry)) {
                registry.body?.children?.filterIsInstance<TypeScriptPropertySignature>()
                        ?.filterTo(entries) { it.name?.trim('\'', '"') == name }
            }
            true
        }
        val entry = entries.firstOrNull() ?: return null
        // `router: RouterService` -> RouterService, following an `import type RouterService from '...'` if needed
        val type = PsiTreeUtil.findChildrenOfType(entry, JSReferenceExpression::class.java).firstOrNull()?.resolve()
        return followAliases(type) ?: entry
    }

    /** Follows `import X from '...'` and `export { X as default }` to the declaration they refer to. */
    private fun followAliases(element: PsiElement?): PsiElement? {
        var current = element
        repeat(5) {
            current = when (val c = current) {
                is ES6ImportedBinding -> c.findReferencedElements().firstOrNull() ?: return c
                is ES6ExportSpecifierAlias -> (c.parent as? ES6ExportSpecifier)?.reference?.resolve() ?: return c
                else -> return c
            }
        }
        return current
    }

    /** Whether [registry] is declared for the `@ember/service` module (an augmentation or its own types package). */
    private fun isEmberServiceModule(registry: TypeScriptInterface): Boolean {
        val module = PsiTreeUtil.getParentOfType(registry, TypeScriptModule::class.java)
        // ambient module names are reported as e.g. `module:@ember/service`
        if (module != null) return module.name?.removePrefix("module:")?.trim('\'', '"') == "@ember/service"
        val path = registry.containingFile?.virtualFile?.path ?: return false
        return "/@types/ember__service/" in path || "/@ember/service/" in path
    }

    /** The package directory directly below a node_modules folder (or below its @scope folder). */
    private fun packageRoot(file: VirtualFile): VirtualFile? {
        var dir: VirtualFile? = file.parent
        while (dir != null) {
            val parent = dir.parent ?: return null
            if (parent.name == "node_modules") return dir
            if (parent.name.startsWith("@") && parent.parent?.name == "node_modules") return dir
            dir = parent
        }
        return null
    }

    private fun packageName(packageRoot: VirtualFile): String {
        val scope = packageRoot.parent?.name?.takeIf { it.startsWith("@") }
        return if (scope != null) "$scope/${packageRoot.name}" else packageRoot.name
    }

    private fun isAppReExport(file: VirtualFile): Boolean {
        val packageRoot = packageRoot(file) ?: return false
        return file.path.removePrefix(packageRoot.path).startsWith("/app/")
    }
}
