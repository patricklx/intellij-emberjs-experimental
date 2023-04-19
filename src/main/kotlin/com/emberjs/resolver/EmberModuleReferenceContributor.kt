package com.emberjs.resolver

import com.emberjs.cli.EmberCliProjectConfigurator
import com.emberjs.utils.*
import com.intellij.javascript.nodejs.reference.NodeModuleManager
import com.intellij.lang.javascript.frameworks.modules.JSExactFileReference
import com.intellij.lang.javascript.psi.resolve.JSModuleReferenceContributor
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import java.util.regex.Pattern

open class EmberJSModuleReference(context: PsiElement, range: TextRange, filePaths: List<String>, val extensions: Array<out String>?) : JSExactFileReference(context, range, filePaths, extensions) {
    override fun acceptFileWithoutExtension(): Boolean {
        if (extensions?.isEmpty() == true) {
            return true
        }
        return false
    }
}
/**
 * Resolves absolute imports from the ember application root, e.g.
 * ```
 * import FooController from 'my-app/controllers/foo'
 * ```
 *
 * Navigating to `FooController` will browse to `/app/controllers/foo.js`
 */
class EmberModuleReferenceContributor : JSModuleReferenceContributor {

    override fun getAllReferences(unquotedRefText: String, host: PsiElement, offset: Int, provider: PsiReferenceProvider?): Array<out PsiReference> {
        // return early for relative imports
        if (unquotedRefText.startsWith('.')) {
            return emptyArray()
        }

        val refText = unquotedRefText;

        // e.g. `my-app/controllers/foo` -> `my-app`
        var packageName = refText.substringBefore('/')

        // e.g. `my-app/controllers/foo` -> `controllers/foo`
        var importPath = refText.removePrefix("$packageName/")
        if (packageName.startsWith("@")) {
            val parts = importPath.split("/").toMutableList()
            val first = parts.removeAt(0)
            importPath = parts.joinToString("/")
            packageName += "/$first"
        }

        // find root package folder of current file (ignoring package.json in in-repo-addons)
        val hostPackageRoot = host.containingFile.originalVirtualFile?.parents
                ?.find { it.findChild("package.json") != null && !it.isInRepoAddon }
                ?: return emptyArray()

        val nodeModules = NodeModuleManager.getInstance(host.project).collectVisibleNodeModules(host.originalVirtualFile)
        val modules = if (getAppName(hostPackageRoot) == packageName) {
            // local import from this app/addon
            listOf(hostPackageRoot) + EmberCliProjectConfigurator.inRepoAddons(hostPackageRoot)
        } else {
            // check node_modules
            val first = nodeModules.find { it.name == packageName }
            listOfNotNull(first?.virtualFile)
        }

        /** Search the `/app` and `/addon` directories of the root and each in-repo-addon */
        val roots = modules
                .flatMap { listOfNotNull(it.findChild("addon"), it.findChild("app"), it.findChild("addon-test-support")) }

        val exts = arrayOf(".ts", ".js", ".hbs", ".gts", ".gjs")
        return roots.map { root ->
            val refs = mutableListOf<EmberJSModuleReference>()
            var ref = EmberJSModuleReference(host, TextRange.create(offset, offset + packageName.length), listOf(root.path), emptyArray())
            refs.add(ref)
            var currentOffset = offset + packageName.length + 1
            var currentPath = root.path
            val parts =  importPath.split("/")
           parts.mapIndexed { index, it ->
               currentPath = "$currentPath/$it"
               val isLast = index == parts.lastIndex
               ref = EmberJSModuleReference(host, TextRange.create(currentOffset, currentOffset + it.length), listOf(currentPath), isLast.ifTrue { exts } ?: emptyArray())
               refs.add(ref)
               if (isLast) {
                   currentPath = "$currentPath/index"
                   ref = EmberJSModuleReference(host, TextRange.create(currentOffset, currentOffset + it.length), listOf(currentPath), isLast.ifTrue { exts } ?: emptyArray())
               }
                currentOffset += it.length + 1
                refs.add(ref)
            }
            return@map refs
        }.flatten().toTypedArray()
    }

    override fun isApplicable(host: PsiElement): Boolean = EmberUtils.isEmber(host.project)

    /** Detect the name of the ember application */
    private fun getAppName(appRoot: VirtualFile): String? = getModulePrefix(appRoot) ?: getAddonName(appRoot)

    private fun getModulePrefix(appRoot: VirtualFile): String? {
        val env = appRoot.findFileByRelativePath("config/environment.js") ?: return null
        return env.inputStream.use { stream ->
            stream.reader().useLines { lines ->
                lines.mapNotNull { line ->
                    val matcher = ModulePrefixPattern.matcher(line)
                    if (matcher.find()) matcher.group(1) else null
                }.firstOrNull()
            }
        }
    }

    /** Captures `my-app` from the string `modulePrefix: 'my-app'` */
    private val ModulePrefixPattern = Pattern.compile("modulePrefix:\\s*['\"](.+?)['\"]")

    private fun getAddonName(appRoot: VirtualFile): String? {
        val index = appRoot.findFileByRelativePath("index.js") ?: return null
        return index.inputStream.use { stream ->
            stream.reader().useLines { lines ->
                lines.mapNotNull { line ->
                    val matcher = NamePattern.matcher(line)
                    if (matcher.find()) matcher.group(1) else null
                }.firstOrNull()
            }
        }
    }

    /** Captures `my-app` from the string `name: 'my-app'` */
    private val NamePattern = Pattern.compile("name:\\s*['\"](.+?)['\"]")
}
