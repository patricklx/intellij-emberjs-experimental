package com.emberjs.cli

import com.emberjs.icons.EmberIcons
import com.intellij.ide.util.projectWizard.SettingsStep
import com.intellij.javascript.nodejs.util.NodePackage
import com.intellij.lang.javascript.boilerplate.NpmPackageProjectGenerator
import com.intellij.lang.javascript.boilerplate.NpxPackageDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ProjectGeneratorPeer
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import org.intellij.lang.annotations.Language
import java.io.File
import javax.swing.JPanel

open class EmberCliProjectGenerator : NpmPackageProjectGenerator() {
    private val CREATE_ADDON_KEY = Key.create<Boolean>("emberjs.create.addon")
    private val USE_TYPESCRIPT_KEY = Key.create<Boolean>("emberjs.use.typescript")
    private val USE_BLUEPRINT_KEY = Key.create<String>("emberjs.use.blueprint")
    private val USE_EMBROIDER_KEY = Key.create<Boolean>("emberjs.use.embroider")
    private val USE_LANGUAGE_KEY = Key.create<String>("emberjs.use.language")

    override fun getName() = "Ember"

    @Language("HTML")
    override fun getDescription() = "<html>A framework for creating ambitious web applications: <a href=\"http://emberjs.com/\">http://emberjs.com/</a></html>"

    override fun getIcon() = EmberIcons.ICON_16

    override fun packageName() = "ember-cli"
    override fun presentablePackageName() = "Ember &CLI:"

    override fun executable(pkg: NodePackage): String {
        return pkg.findBinFile("ember", "bin${File.separator}ember")?.path
                ?: "${pkg.systemDependentPath}${File.separator}bin${File.separator}ember"
    }

    override fun generatorArgs(project: Project, baseDir: VirtualFile, settings: Settings): Array<out String> {
        val isAddon = settings.getUserData(CREATE_ADDON_KEY) ?: false
        val lang = settings.getUserData(USE_LANGUAGE_KEY) ?: ""
        val blueprint = settings.getUserData(USE_BLUEPRINT_KEY) ?: ""
        val typescript = settings.getUserData(USE_TYPESCRIPT_KEY)
        val embroider = settings.getUserData(USE_EMBROIDER_KEY)
        val list = mutableListOf<String>()
        list.add("init")
        if (isAddon) {
            list.add("--blueprint=addon")
        } else if (blueprint != "") {
            list.add("--blueprint=$blueprint")
        }
        if (lang != "") {
            list.add("--lang=$lang")
        }
        if (typescript != null) {
            list.add("--typescript=$typescript")
        }
        if (embroider != null) {
            list.add("--embroider=$embroider")
        }
        return list.toTypedArray()
    }

    override fun createPeer(): ProjectGeneratorPeer<Settings> {
        val createAddonCheckbox = JBCheckBox("Generate Addon", false)
        val useTypeScript = JBCheckBox("Use TypeScript", true)
        val useEmbroider = JBCheckBox("Use Embroider", false)
        val useBlueprint = JBTextField()
        val useLanguage = JBTextField()
        return object : NpmPackageGeneratorPeer() {
            override fun createPanel(): JPanel {
                val panel = super.createPanel()
                panel.add(createAddonCheckbox)
                panel.add(useTypeScript)
                panel.add(useEmbroider)
                panel.add(useBlueprint)
                panel.add(useLanguage)
                return panel
            }

            override fun buildUI(settingsStep: SettingsStep) {
                super.buildUI(settingsStep)
                settingsStep.addSettingsComponent(createAddonCheckbox)
                settingsStep.addSettingsComponent(useTypeScript)
                settingsStep.addSettingsComponent(useEmbroider)
                settingsStep.addSettingsComponent(JBLabel("Provide a custom blueprint"))
                settingsStep.addSettingsComponent(useBlueprint)
                settingsStep.addSettingsComponent(JBLabel("Provide a language of the application via index.html"))
                settingsStep.addSettingsComponent(useLanguage)
            }

            override fun getSettings(): Settings {
                val settings = super.getSettings()
                settings.putUserData(CREATE_ADDON_KEY, createAddonCheckbox.isSelected)
                settings.putUserData(USE_TYPESCRIPT_KEY, useTypeScript.isSelected)
                settings.putUserData(USE_EMBROIDER_KEY, useEmbroider.isSelected)
                settings.putUserData(USE_LANGUAGE_KEY, useLanguage.text!!)
                return settings
            }
        }
    }

    override fun filters(project: Project, baseDir: VirtualFile) = arrayOf(EmberCliFilter(project, baseDir.path))

    override fun customizeModule(baseDir: VirtualFile, entry: ContentEntry?) = Unit

    override fun generateProject(project: Project, baseDir: VirtualFile, settings: NpmPackageProjectGenerator.Settings, module: Module) {
        EmberCliProjectConfigurator.setupEmber(project, module, baseDir)
        super.generateProject(project, baseDir, settings, module)
    }

    override fun getNpxCommands(): List<NpxPackageDescriptor.NpxCommand> {
        return listOf(
                NpxPackageDescriptor.NpxCommand("ember-cli", "ember-cli")
        )
    }
}