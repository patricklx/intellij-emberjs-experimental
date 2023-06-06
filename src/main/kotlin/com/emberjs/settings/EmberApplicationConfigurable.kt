package com.emberjs.settings

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.components.JBCheckBox
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.JPanel

class EmberApplicationConfigurable private constructor() : SearchableConfigurable {
    private var runEslintOnHbsFiles: JBCheckBox? = null
    private var runEslintOnGtsFiles: JBCheckBox? = null
    private var fixOnSave: JBCheckBox? = null

    init {
        runEslintOnHbsFiles = JBCheckBox("Run eslint on Hbs files")
        runEslintOnGtsFiles = JBCheckBox("Run eslint on Gts files")
        fixOnSave = JBCheckBox("run eslint --fix on save")
    }

    @Nls
    override fun getDisplayName() = "Ember.js"

    override fun enableSearch(option: String) = null
    override fun getId() = "com.emberjs.settings.application"
    override fun getHelpTopic() = this.id

    override fun createComponent(): JComponent? {
        val panel = JPanel(VerticalFlowLayout())
        panel.add(runEslintOnHbsFiles!!)
        panel.add(runEslintOnGtsFiles!!)
        panel.add(fixOnSave!!)
        return panel
    }

    override fun isModified(): Boolean {
        return runEslintOnHbsFiles!!.isSelected != EmberApplicationOptions.runEslintOnHbs ||
                runEslintOnGtsFiles!!.isSelected != EmberApplicationOptions.runEslintOnGts ||
                fixOnSave!!.isSelected != EmberApplicationOptions.fixOnSave
    }

    override fun apply() {
        EmberApplicationOptions.runEslintOnHbs = runEslintOnHbsFiles!!.isSelected
        EmberApplicationOptions.runEslintOnGts = runEslintOnGtsFiles!!.isSelected
        EmberApplicationOptions.fixOnSave = fixOnSave!!.isSelected
    }

    override fun reset() {
        runEslintOnHbsFiles!!.isSelected = EmberApplicationOptions.runEslintOnHbs
        runEslintOnGtsFiles!!.isSelected = EmberApplicationOptions.runEslintOnGts
        fixOnSave!!.isSelected = EmberApplicationOptions.fixOnSave
    }

    override fun disposeUIResources() {
        runEslintOnHbsFiles = null
        runEslintOnGtsFiles = null
        fixOnSave = null
    }
}
