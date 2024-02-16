package com.emberjs.translations

import com.emberjs.Ember
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

object EmberI18n {

    fun findDefaultLocale(file: PsiFile): String? {
        val configFile = Ember.findEnvironmentConfigFile(file.virtualFile) ?: return null
        val configPsiFile = PsiManager.getInstance(file.project).findFile(configFile) ?: return null

        return EmberI18nDefaultLocaleFinder().findIn(configPsiFile)
    }
}
