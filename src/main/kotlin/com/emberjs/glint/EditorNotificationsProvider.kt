package com.emberjs.glint

import com.emberjs.gts.GtsFileViewProvider
import com.intellij.ide.util.PropertiesComponent
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.javascript.linter.JSLinterEditorNotifications
import com.intellij.lang.javascript.linter.JSLinterFileLevelAnnotation
import com.intellij.lang.javascript.linter.JSLinterInspection
import com.intellij.lang.javascript.linter.JSLinterStandardFixes
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigUtil
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import java.awt.Color
import java.util.function.Function
import javax.swing.JComponent

class GlintEditorNotificationsProvider(val project: Project): EditorNotificationProvider {

    private fun isNotificationDismissed(file: VirtualFile): Boolean {
        return PropertiesComponent.getInstance(project).getBoolean(file.path)
    }

    private fun dismissNotification(file: VirtualFile) {
        PropertiesComponent.getInstance(project).setValue(file.path, true)
        EditorNotifications.getInstance(project).updateAllNotifications()
    }

    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        return Function { fileEditor: FileEditor ->
            if (isNotificationDismissed(file)) {
                null
            } else {
                val f = PsiManager.getInstance(project).findFile(file)
                if (f != null && f.viewProvider is GtsFileViewProvider) {
                    val config = TypeScriptConfigUtil.getConfigForFile(project, file)
                    if (config == null) {
                        val panel = EditorNotificationPanel(fileEditor, null as Color?, EditorColors.GUTTER_BACKGROUND, EditorNotificationPanel.Status.Warning)
                        panel.text = "This file is not included in your tsconfig"
                        panel.createActionLabel("dismiss", { dismissNotification(file) }, false)
                        panel
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        }
    }
}