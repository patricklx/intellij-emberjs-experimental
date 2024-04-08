
import com.emberjs.hbs.linter.eslint.GtsEsLintFixAction
import com.emberjs.settings.EmberApplicationOptions
import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveFileDocumentManagerListener.ActionOnSave
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiManager

class EsLintActionOnSave : ActionOnSave() {
    override fun isEnabledForProject(project: Project): Boolean = isFixOnSaveEnabled(project)

    override fun processDocuments(project: Project, documents: Array<Document>) {
        if (!this.isEnabledForProject(project)) return

        val manager = FileDocumentManager.getInstance()
        val psiManager = PsiManager.getInstance(project)
        val fileIndex = ProjectFileIndex.getInstance(project)
        val action = GtsEsLintFixAction()
        val files = documents.mapNotNull { manager.getFile(it) }
                .filter { it.isInLocalFileSystem && fileIndex.isInContent(it) && action.isFileAccepted(project, it) }
                .toTypedArray()
        if (files.isNotEmpty()) {
            action.processFiles(project, files, false, true)
        }
    }

    companion object {
        fun isFixOnSaveEnabled(project: Project): Boolean {
            return EmberApplicationOptions.fixOnSave
        }
    }
}
