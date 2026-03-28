import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.lang.javascript.linter.JSLinterError
import com.intellij.lang.javascript.linter.JSLinterErrorBase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager

object TemplateLintSuppressionUtil {
    fun getSuppressForLineAction(error: JSLinterErrorBase, modificationStamp: Long): IntentionAction? {
        if (error !is JSLinterError) return null
    val rule = error.code ?: return null

        return object : BaseIntentionAction() {
            override fun getFamilyName(): String {
                return "Disable template-lint rule"
            }

            override fun getText(): String {
                return "Disable template-lint rule $rule"
            }

            override fun isAvailable(project: Project, editor: Editor?, file: com.intellij.psi.PsiFile?): Boolean {
                return editor != null && editor.document.modificationStamp == modificationStamp
            }

            override fun invoke(project: Project, editor: Editor?, file: com.intellij.psi.PsiFile?) {
                if (editor == null) return
                val document = editor.document
                val line = Math.max(0, error.line - 1)
                try {
                    val lineStart = document.getLineStartOffset(line)
                    val comment = "{{!-- template-lint-disable $rule --}}\n"
                    WriteCommandAction.runWriteCommandAction(project) {
                        document.insertString(lineStart, comment)
                        PsiDocumentManager.getInstance(project).commitDocument(document)
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }

            override fun startInWriteAction(): Boolean {
                return true
            }
        }
    }

    fun getSuppressionsForError(error: JSLinterError, modificationStamp: Long): List<IntentionAction> {
        val a = getSuppressForLineAction(error, modificationStamp)
        return if (a != null) listOf(a) else emptyList()
    }
}
