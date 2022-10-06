package com.emberjs.cli


import com.emberjs.glint.getGlintDescriptor
import com.emberjs.utils.emberRoot
import com.emberjs.utils.findMainPackageJson
import com.emberjs.utils.isEmber
import com.emberjs.utils.parentEmberModule
import com.intellij.framework.FrameworkType
import com.intellij.framework.detection.DetectedFrameworkDescription
import com.intellij.framework.detection.FileContentPattern
import com.intellij.framework.detection.FrameworkDetectionContext
import com.intellij.framework.detection.FrameworkDetector
import com.intellij.ide.projectView.actions.MarkRootActionBase
import com.intellij.javascript.nodejs.PackageJsonDependency
import com.intellij.javascript.nodejs.packageJson.PackageJsonDependencies
import com.intellij.javascript.nodejs.packageJson.PackageJsonFileManager
import com.intellij.json.JsonFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableModelsProvider
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.util.ProcessingContext
import com.intellij.util.indexing.FileContent

class EmberCliFrameworkDetector : FrameworkDetector("Ember", 2) {
    /** Use package json keys to detect ember */
    override fun getFileType(): FileType = JsonFileType.INSTANCE

    override fun createSuitableFilePattern(): ElementPattern<FileContent> {
        return FileContentPattern.fileContent()
                .with(object : PatternCondition<FileContent>("emberKey") {
                    override fun accepts(fileContent:FileContent, context: ProcessingContext): Boolean {
                        if (fileContent.fileName != "package.json") {
                            return false
                        }
                        if (fileContent.file.parent != fileContent.file.emberRoot) {
                            return false
                        }
                        return fileContent.file.parent.isEmber
                    }
                })
    }

    override fun getFrameworkType(): FrameworkType = EmberFrameworkType

    private fun listenNodeModules(rootDir: VirtualFile, modulesProvider: ModulesProvider) {
        val modifiableModelsProvider = ModifiableModelsProvider.getInstance()
        modulesProvider.modules
                .filter { ModuleRootManager.getInstance(it).contentRoots.contains(rootDir) }
                .forEach { module ->
                    val p = modifiableModelsProvider.getModuleModifiableModel(module).project
                    p.messageBus.connect().subscribe(PackageJsonFileManager.TOPIC, PackageJsonFileManager.PackageJsonChangeListener {
                        ApplicationManager.getApplication().invokeLater {
                            ApplicationManager.getApplication().runWriteAction()
                            {
                                val model = modifiableModelsProvider.getModuleModifiableModel(module)
                                val entry = MarkRootActionBase.findContentEntry(model, rootDir)
                                if (entry != null) {
                                    val e = MarkRootActionBase.findContentEntry(model, rootDir)!!
                                    (e.rootModel as ModifiableRootModel).contentEntries.toList().forEach {
                                        if (it.url.contains("/node_modules/")) {
                                            (e.rootModel as ModifiableRootModel).removeContentEntry(it)
                                        }
                                    }
                                    val pkg = findMainPackageJson(rootDir)
                                    val allDependencies = pkg?.getAllDependencyEntries() ?: mapOf()

                                    rootDir.findChild("node_modules")!!.children.forEach {
                                        if (allDependencies.contains(it.name) && allDependencies[it.name]?.dependencyType == PackageJsonDependency.dependencies) {
                                            (e.rootModel as ModifiableRootModel).addContentEntry(it.url)
                                        }
                                        if (it.name.contains("ember")) {
                                            (e.rootModel as ModifiableRootModel).addContentEntry(it.url)
                                        }
                                        if (it.name.contains("@types")) {
                                            (e.rootModel as ModifiableRootModel).addContentEntry(it.url)
                                        }
                                        if (it.name.contains("glimmer")) {
                                            (e.rootModel as ModifiableRootModel).addContentEntry(it.url)
                                        }
                                    }
                                    modifiableModelsProvider.commitModuleModifiableModel(model)
                                } else {
                                    modifiableModelsProvider.disposeModuleModifiableModel(model)
                                }
                            }
                        }
                    })
                }
    }

    override fun detect(newFiles: MutableCollection<out VirtualFile>, context: FrameworkDetectionContext): MutableList<out DetectedFrameworkDescription> {
        newFiles.removeIf { !it.path.endsWith("package.json") || it.parent != it.emberRoot || !it.parent.isEmber }
        val rootDir = newFiles.firstOrNull()?.parent

        if (rootDir != null && !isConfigured(newFiles, context.project)) {
            return mutableListOf(EmberFrameworkDescription(rootDir, newFiles))
        } else if (rootDir != null) {
            // setup reconfigure on package.json change.
            val modulesProvider = DefaultModulesProvider.createForProject(context.project)
            listenNodeModules(rootDir, modulesProvider)
            getGlintDescriptor(context.project!!).server.start()
        }
        return mutableListOf()
    }

    private fun isConfigured(files: Collection<VirtualFile>, project: Project?): Boolean {
        if (project == null) return false

        return files.any { file ->
            // assume the project has been configured if a /tmp directory is excluded
            val module = ModuleUtilCore.findModuleForFile(file, project) ?: return false
            val excluded = ModuleRootManager.getInstance(module).excludeRootUrls
            return excluded.any { it == file.parent.url + "/tmp" }
        }
    }

    private inner class EmberFrameworkDescription(val root: VirtualFile, val files: Collection<VirtualFile>) : DetectedFrameworkDescription() {
        override fun getDetector() = this@EmberCliFrameworkDetector
        override fun getRelatedFiles() = files
        override fun getSetupText() = "Configure this module for Ember.js development"
        override fun equals(other: Any?) = other is EmberFrameworkDescription && this.files == other.files
        override fun hashCode() = files.hashCode()

        override fun setupFramework(modifiableModelsProvider: ModifiableModelsProvider, modulesProvider: ModulesProvider) {
            listenNodeModules(root, modulesProvider)
            modulesProvider.modules
                .filter { ModuleRootManager.getInstance(it).contentRoots.contains(root) }
                .forEach { module ->
                    val model = modifiableModelsProvider.getModuleModifiableModel(module)
                    val entry = MarkRootActionBase.findContentEntry(model, root)
                    if (entry != null) {
                        EmberCliProjectConfigurator.setupEmber(model.project, entry, root)
                        getGlintDescriptor(model.project).server.start()
                        modifiableModelsProvider.commitModuleModifiableModel(model)
                    } else {
                        modifiableModelsProvider.disposeModuleModifiableModel(model)
                    }
                }
        }
    }
}
