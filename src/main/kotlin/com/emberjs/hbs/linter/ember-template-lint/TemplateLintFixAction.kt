
import com.dmarcotte.handlebars.file.HbFileViewProvider
import com.emberjs.gts.GtsFileViewProvider
import com.emberjs.icons.EmberIcons
import com.intellij.lang.javascript.linter.JSLinterFixAction
import com.intellij.lang.javascript.linter.JSLinterInput
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

class TemplateLintFixAction : JSLinterFixAction(
        { TemplateLintBundle.message("hbs.lint.configurable.name") },
        { TemplateLintBundle.message("hbs.lint.action.fix.problems.description") },
        EmberIcons.ICON_16
) {
    override fun getConfiguration(project: Project) = TemplateLintConfiguration.getInstance(project)

    override fun needRefreshFilesAfter(): Boolean {
        return false
    }

    override fun createTask(project: Project, filesToProcess: MutableCollection<out VirtualFile>, completeCallback: Runnable, modalProgress: Boolean): Task {
        return makeModalOrBackgroundTask(project, modalProgress) { indicator ->
            filesToProcess.forEach { file ->
                indicator.checkCanceled()
                indicator.text = "processing file ${file.name}"
                val psiFile = ReadAction.compute<PsiFile?, RuntimeException> {
                    PsiManager.getInstance(project).findFile(file)
                }
                if (psiFile != null) {
                    fixFile(psiFile)
                }
            }

            completeCallback.run()
        }
    }

    private fun makeModalOrBackgroundTask(project: Project, modalProgress: Boolean, body: (ProgressIndicator) -> Unit): Task {
        val title = TemplateLintBundle.message("hbs.lint.action.modal.title")
        return if (modalProgress) {
            object : Task.Modal(project, title, true) {
                override fun run(indicator: ProgressIndicator) {
                    body(indicator)
                }
            }
        } else {
            object : Task.Backgroundable(project, title, true) {
                override fun run(indicator: ProgressIndicator) {
                    body(indicator)
                }
            }
        }
    }

    override fun isFileAccepted(project: Project, file: VirtualFile): Boolean {
        val f = PsiManager.getInstance(project).findFile(file)
        return f?.viewProvider is GtsFileViewProvider || f?.viewProvider is HbFileViewProvider || f is JSFile
    }

    private fun fixFile(psiFile: PsiFile) {
        ApplicationManager.getApplication().run {
            val input = JSLinterInput.create(psiFile, TemplateLintConfiguration.getInstance(psiFile.project).extendedState.state, null)
            TemplateLintExternalRunner().fixFile(input)
        }
    }
}
