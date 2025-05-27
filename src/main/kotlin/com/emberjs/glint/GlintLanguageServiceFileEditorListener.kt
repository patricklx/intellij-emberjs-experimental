package com.emberjs.glint

import com.emberjs.gts.GjsLanguage
import com.emberjs.gts.GtsFileViewProvider
import com.emberjs.gts.GtsLanguage
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager

class GlintLanguageServiceFileEditorListener : FileEditorManagerListener {
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        val project = source.project
        val provider = GlintLanguageServiceProvider(project)
        openEditorIfFileRelevant(file, provider)
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        if (source.getAllEditors(file).isEmpty()) {
            val project = source.project
            val provider = GlintLanguageServiceProvider(project)
            if (provider.isHighlightingCandidate(file)) {
                val service = provider.getService(file)
                service?.closeLastEditor(file)
            }
        }
    }

    companion object {
        private fun openEditorIfFileRelevant(file: VirtualFile, provider: GlintLanguageServiceProvider) {
            if (provider.isHighlightingCandidate(file)) {
                val service = provider.getService(file)
                service?.openEditor(file)
            }
        }
    }
}