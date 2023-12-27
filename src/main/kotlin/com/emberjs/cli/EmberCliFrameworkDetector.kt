package com.emberjs.cli
import com.emberjs.glint.getGlintDescriptor
import com.emberjs.utils.emberRoot
import com.emberjs.utils.isEmber
import com.intellij.framework.FrameworkType
import com.intellij.framework.detection.DetectedFrameworkDescription
import com.intellij.framework.detection.FileContentPattern
import com.intellij.framework.detection.FrameworkDetectionContext
import com.intellij.framework.detection.FrameworkDetector
import com.intellij.ide.projectView.actions.MarkRootActionBase
import com.intellij.javascript.nodejs.packageJson.PackageJsonFileManager
import com.intellij.json.JsonFileType
import com.intellij.lang.javascript.library.JSLibraryManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableModelsProvider
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.util.ProcessingContext
import com.intellij.util.indexing.FileContent
import com.intellij.webcore.libraries.ScriptingLibraryModel

val detectedFrameworks = HashMap<Project, List<EmberCliFrameworkDetector.EmberFrameworkDescription>>()

class EmberCliFrameworkDetector : FrameworkDetector("Ember", 2) {
    /** Use package json keys to detect ember */

    override fun getFileType(): FileType = JsonFileType.INSTANCE

    override fun createSuitableFilePattern(): ElementPattern<FileContent> {
        return FileContentPattern.fileContent()
                .with(object : PatternCondition<FileContent>("emberKey") {
                    override fun accepts(fileContent: FileContent, context: ProcessingContext): Boolean {
                        if (fileContent.file.path.contains("node_modules")) {
                            return false
                        }
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
                    p.messageBus.connect().subscribe(PackageJsonFileManager.CHANGES_TOPIC, PackageJsonFileManager.PackageJsonChangesListener {
                        ApplicationManager.getApplication().invokeLater {
                            ApplicationManager.getApplication().runWriteAction()
                            {
                                val model = modifiableModelsProvider.getModuleModifiableModel(module)
                                val entry = MarkRootActionBase.findContentEntry(model, rootDir)
                                if (entry != null) {
                                    EmberCliProjectConfigurator.setupEmber(model.project, entry, rootDir)
                                    val moduleLibraryTable = JSLibraryManager.getInstance(model.project)
                                    val libraries = moduleLibraryTable.getLibraries(ScriptingLibraryModel.LibraryLevel.PROJECT)

                                    libraries.toList().forEach { ce ->
                                        if (ce.sourceFiles.size == 0 && ce.originalLibrary != null) {
                                            moduleLibraryTable.removeLibrary(ce)
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

        val frameworkDescriptions = mutableListOf<EmberFrameworkDescription>()

        newFiles.forEach {
            val rootDir = it.parent
            if (rootDir != null && context.project != null && !isConfigured(newFiles, context.project)) {
                frameworkDescriptions.add(EmberFrameworkDescription(rootDir, newFiles, context.project!!))
            } else if (rootDir != null) {
                context.project?.let {
                    ApplicationManager.getApplication().invokeLaterOnWriteThread {
                        getGlintDescriptor(it).ensureStarted()
                    }
                }
                frameworkDescriptions.add(EmberFrameworkDescription(rootDir, newFiles, context.project!!))
            }
        }
        detectedFrameworks[context.project!!] = frameworkDescriptions
        return frameworkDescriptions
    }

    public fun isConfigured(files: Collection<VirtualFile>, project: Project?): Boolean {
        if (project == null) return false

        return files.any { file ->
            // assume the project has been configured if a /tmp directory is excluded
            val module = ModuleUtilCore.findModuleForFile(file, project) ?: return false
            val excluded = ModuleRootManager.getInstance(module).excludeRootUrls
            return excluded.any { it == file.parent.url + "/tmp" }
        }
    }

    public inner class EmberFrameworkDescription(val root: VirtualFile, val files: Collection<VirtualFile>, val project: Project) : DetectedFrameworkDescription() {
        override fun getDetector() = this@EmberCliFrameworkDetector
        override fun getRelatedFiles() = files
        override fun getSetupText() = "Configure this module for Ember.js development"
        override fun equals(other: Any?) = other is EmberFrameworkDescription && this.files == other.files
        override fun hashCode() = files.hashCode()

        override fun canSetupFramework(allDetectedFrameworks: MutableCollection<out DetectedFrameworkDescription>): Boolean {
            return !this.isConfigured()
        }

        fun isConfigured(): Boolean {
            return detector.isConfigured(files, project)
        }

        override fun setupFramework(modifiableModelsProvider: ModifiableModelsProvider, modulesProvider: ModulesProvider) {
            ModuleUtilCore.findModuleForFile(root, project)?.let { module ->
                val model = modifiableModelsProvider.getModuleModifiableModel(module)
                val entry = MarkRootActionBase.findContentEntry(model, root)
                if (entry != null) {
                    EmberCliProjectConfigurator.setupEmber(model.project, entry, root)
                    getGlintDescriptor(model.project).ensureStarted()
                    modifiableModelsProvider.commitModuleModifiableModel(model)
                } else {
                    modifiableModelsProvider.disposeModuleModifiableModel(model)
                }
            }
        }
    }

    companion object {
        fun hasEnabledEmberFramework(project: Project): Boolean {
            return detectedFrameworks.getOrDefault(project, emptyList()).any { it.isConfigured() }
        }
    }
}
