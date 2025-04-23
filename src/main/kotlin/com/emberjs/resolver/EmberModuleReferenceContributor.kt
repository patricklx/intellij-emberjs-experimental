package com.emberjs.resolver

import com.emberjs.hbs.TagReferencesProvider
import com.emberjs.utils.*
import com.intellij.javascript.nodejs.reference.NodeModuleManager
import com.intellij.lang.javascript.JavaScriptSupportLoader
import com.intellij.lang.javascript.TypeScriptFileType
import com.intellij.lang.javascript.frameworks.modules.JSExactFileReference
import com.intellij.lang.javascript.psi.impl.JSFileImpl
import com.intellij.lang.javascript.psi.resolve.JSModuleReferenceContributor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findFile
import com.intellij.psi.*
import com.intellij.psi.search.ProjectAwareVirtualFile
import java.util.regex.Pattern

open class EmberJSModuleReference(context: PsiElement, range: TextRange, filePaths: List<String>, val extensions: Array<out String>?) : JSExactFileReference(context, range, filePaths, extensions) {
    override fun acceptFileWithoutExtension(): Boolean {
        return extensions?.isEmpty() == true
    }
}

open class EmberInternalJSModuleReference(context: PsiElement, range: TextRange, val internalFile: PsiFile?) : EmberJSModuleReference(context, range, emptyList(), emptyArray()) {
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        return emptyArray()
    }
}


class ProjectAwareVirtualFile(val virtualFile: VirtualFile): VirtualFile(), ProjectAwareVirtualFile {
    override fun getName() = virtualFile.name
    override fun getFileSystem() = virtualFile.fileSystem
    override fun getPath() = virtualFile.path
    override fun isWritable() = virtualFile.isWritable
    override fun isDirectory() = virtualFile.isDirectory
    override fun isValid() = virtualFile.isValid
    override fun getParent() = virtualFile.parent
    override fun getChildren() = virtualFile.children
    override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long) = virtualFile.getOutputStream(requestor, newModificationStamp, newTimeStamp)
    override fun contentsToByteArray() = virtualFile.contentsToByteArray()
    override fun getTimeStamp() = virtualFile.timeStamp
    override fun getLength() = virtualFile.length
    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) = virtualFile.refresh(asynchronous, recursive, postRunnable)
    override fun getInputStream() = virtualFile.inputStream
    override fun isInProject(project: Project) = true
    override fun getFileType(): FileType {
        return TypeScriptFileType
    }
    override fun getModificationStamp(): Long {
        return 0
    }
}


class ProjectFile(val psiFile: PsiFile): JSFileImpl(psiFile.viewProvider, JavaScriptSupportLoader.TYPESCRIPT) {

    override fun getFileType(): FileType {
        return TypeScriptFileType
    }
    override fun getVirtualFile(): VirtualFile {
        return ProjectAwareVirtualFile(psiFile.virtualFile)
    }

    override fun getContainingFile(): PsiFile {
        return this
    }

    override fun getParent(): PsiDirectory? {
        return this.project.guessProjectDir()?.let { PsiManager.getInstance(project).findDirectory(it) }
    }

    override fun accept(visitor: PsiElementVisitor) {
        return psiFile.accept(visitor)
    }

    override fun getOriginalFile(): PsiFile {
        return this
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

        if (refText == "@ember/helper") {
            val project = host.project
            val helpers = TagReferencesProvider::class.java.getResource("/com/emberjs/external/ember-helpers.ts")?.let { PsiFileFactory.getInstance(project).createFileFromText("intellij-emberjs/internal/helpers-stub", JavaScriptSupportLoader.TYPESCRIPT, it.readText()) }
            val file = ProjectFile(helpers!!)
            return arrayOf(EmberInternalJSModuleReference(host, TextRange(offset, host.textLength - 1), file))
        }
        if (refText == "@ember/modifier") {
            val project = host.project
            val modifiers = TagReferencesProvider::class.java.getResource("/com/emberjs/external/ember-modifiers.ts")?.let { PsiFileFactory.getInstance(project).createFileFromText("intellij-emberjs/internal/modifiers-stub", JavaScriptSupportLoader.TYPESCRIPT, it.readText()) }
            val file = ProjectFile(modifiers!!)
            return arrayOf(EmberInternalJSModuleReference(host, TextRange(offset, host.textLength - 1), file))
        }
        if (refText == "@ember/component") {
            val project = host.project
            val internalComponentsFile = TagReferencesProvider::class.java.getResource("/com/emberjs/external/ember-components.ts")?.let { PsiFileFactory.getInstance(project).createFileFromText("intellij-emberjs/internal/components-stub", JavaScriptSupportLoader.TYPESCRIPT, it.readText()) }
            val file = ProjectFile(internalComponentsFile!!)
            return arrayOf(EmberInternalJSModuleReference(host, TextRange(offset, host.textLength - 1), file))
        }

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

        val modules: MutableList<VirtualFile> = mutableListOf()

        if (hostPackageRoot.findFile("package.json")?.packageJsonData?.name == packageName) {
            modules.add(hostPackageRoot)
        }

        val nodeModules = NodeModuleManager.getInstance(host.project).collectVisibleNodeModules(host.originalVirtualFile)
        nodeModules.find { it.name == packageName && it.virtualFile != null }?.let { modules.add(it.virtualFile!!) }




        /** Search the `/app` and `/addon` directories of the root and each in-repo-addon */
        val roots = modules
                .flatMap {
                    listOfNotNull(
                            it.findChild("addon"),
                            it.findChild("app"),
                            it.findChild("addon-test-support"),
                    )
                }.toMutableList()

        if (host.containingFile.originalVirtualFile?.parentEmberModule?.inRepoAddonDirs != null) {
            val inRepoAddon = host.containingFile.originalVirtualFile?.parentEmberModule?.inRepoAddonDirs?.find {
                it.findChild("package.json")?.packageJsonData?.name == packageName
            }
            if (inRepoAddon != null) {
                roots.add(inRepoAddon)
            }
        }

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
               ref = EmberJSModuleReference(host, TextRange.create(offset, currentOffset + it.length), listOf(currentPath), exts)
               refs.add(ref)
               if (isLast) {
                   currentPath = "$currentPath/index"
                   ref = EmberJSModuleReference(host, TextRange.create(offset, currentOffset + it.length), listOf(currentPath), exts)
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
