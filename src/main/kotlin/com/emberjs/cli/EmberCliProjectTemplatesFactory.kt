package com.emberjs.cli

import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.lang.javascript.boilerplate.JavaScriptNewTemplatesFactoryBase
import com.intellij.openapi.module.WebModuleBuilder
import com.intellij.platform.ProjectTemplate
import com.intellij.platform.ProjectTemplatesFactory

class EmberCliProjectTemplatesFactory : JavaScriptNewTemplatesFactoryBase() {
    override fun createTemplates(context: WizardContext?): Array<ProjectTemplate> = arrayOf(EmberCliProjectGenerator())
}
