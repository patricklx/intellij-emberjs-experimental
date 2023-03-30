package com.emberjs.glint

import com.intellij.lsp.methods.DidChangeMethod
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.ProjectManager

class GlintDocumentListener: DocumentListener {
    override fun documentChanged(event: DocumentEvent) {
        val projects = ProjectManager.getInstance().getOpenProjects();
        val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
        projects.forEach {project ->
            GlintLanguageServiceProvider(project).allServices.forEach {
                val lspServer = it.getDescriptor()?.server
                if (lspServer != null && it.isAcceptable(file)&& lspServer.isFileOpened(file)) {
                    val didChangeMethod = DidChangeMethod.createFull(lspServer, event.document, file)
                    lspServer.invoke(didChangeMethod)
                }
            }

        }

    }
}
