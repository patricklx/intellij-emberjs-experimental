package com.emberjs.navigation

import com.emberjs.utils.emberRoot
import com.emberjs.utils.parentModule
import com.intellij.openapi.vfs.VirtualFile

/**
 * Narrows Ember name index results down to the files Ember itself would resolve for an app, so that
 * Cmd+click jumps straight there instead of offering every copy found in node_modules.
 */
object EmberNavigationTargets {

    /** Built-in services that ship with ember-source, as paths inside the ember-source package (newest layout first). */
    private val BUILTIN_SERVICES = mapOf(
        "router" to listOf(
            "dist/packages/@ember/routing/router-service.js",
            "dist/packages/@ember/-internals/routing/lib/services/router.js",
            "types/stable/@ember/routing/router-service.d.ts",
        ),
    )

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

    /** Files for a service that is built into Ember rather than defined by the app or an addon. */
    fun builtinService(context: VirtualFile?, name: String): List<VirtualFile> {
        val candidates = BUILTIN_SERVICES[name] ?: return emptyList()
        val appRoot = context?.let { it.emberRoot ?: it.parentModule } ?: return emptyList()
        val emberSource = appRoot.findFileByRelativePath("node_modules/ember-source") ?: return emptyList()
        return listOfNotNull(candidates.firstNotNullOfOrNull { emberSource.findFileByRelativePath(it) })
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
