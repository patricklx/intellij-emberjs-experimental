package com.emberjs.navigation

import com.emberjs.utils.EmberUtils
import com.intellij.lang.ecmascript6.psi.ES6ExportDeclaration
import com.intellij.lang.ecmascript6.psi.ES6FromClause
import com.intellij.lang.ecmascript6.resolve.ES6PsiUtil
import com.intellij.lang.javascript.JavaScriptSupportLoader
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSVarStatement
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
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

    /**
     * The declaration a module's default export ends up at, following re-exports such as ember-data's
     * `app/services/store.js` -> `export { default } from 'ember-data/store'` -> `export { Store as default } from
     * './-private'` -> `export class Store`. Returns null if the chain can't be followed.
     */
    fun defaultExportDefinition(file: PsiFile): PsiElement? = exportedDeclaration(file, "default", 0)

    private fun exportedDeclaration(file: PsiFile, name: String, depth: Int): PsiElement? {
        if (depth > 8) return null
        val js = jsPsi(file) ?: return null
        val exports = js.children.filterIsInstance<ES6ExportDeclaration>()
        for (export in exports) {
            val specifier = export.exportSpecifiers.firstOrNull { (it.alias?.name ?: it.referenceName) == name } ?: continue
            val importedName = specifier.referenceName ?: return null
            val from = export.fromClause
            return if (from != null) {
                moduleFile(from)?.let { exportedDeclaration(it, importedName, depth + 1) }
            } else {
                followAliases(specifier.reference?.resolve())
            }
        }
        if (name == "default") {
            EmberUtils.findDefaultExportClass(file)?.let { return it }
            ES6PsiUtil.findDefaultExport(js)?.let { return it }
        } else {
            js.children.firstOrNull { (it as? JSClass)?.name == name || (it as? JSFunction)?.name == name }?.let { return it }
            js.children.filterIsInstance<JSVarStatement>().flatMap { it.variables.toList() }.firstOrNull { it.name == name }?.let { return it }
        }
        // export * from '...'
        return exports.filter { it.isExportAll }.firstNotNullOfOrNull { e -> e.fromClause?.let(::moduleFile)?.let { exportedDeclaration(it, name, depth + 1) } }
    }

    private fun jsPsi(file: PsiFile): PsiFile? = when (file) {
        is JSFile -> file
        else -> file.viewProvider.getPsi(JavaScriptSupportLoader.TYPESCRIPT) ?: file.viewProvider.getPsi(JavaScriptSupportLoader.ECMA_SCRIPT_6)
    }

    // Module references come per path segment ('ember-data', 'store' / 'ember-data/store'); only the ones that end at
    // the end of the specifier point at the module itself, an earlier segment would e.g. resolve 'ember-data' to its
    // main index.js.
    private fun moduleFile(from: ES6FromClause): PsiFile? {
        val end = from.references.maxOfOrNull { it.rangeInElement.endOffset } ?: return null
        return from.references.filter { it.rangeInElement.endOffset == end }.firstNotNullOfOrNull { ref -> moduleTarget(ref.resolve()) }
    }

    private fun moduleTarget(target: PsiElement?): PsiFile? =
        when (target) {
            is PsiFile -> target
            is PsiDirectory -> INDEX_FILES.firstNotNullOfOrNull { target.findFile(it) }
            else -> null
        }

    private val INDEX_FILES = listOf("index.ts", "index.gts", "index.js", "index.gjs", "index.d.ts")

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
