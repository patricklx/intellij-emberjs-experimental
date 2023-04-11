package com.emberjs.glint

import com.emberjs.utils.emberRoot
import com.intellij.lang.javascript.JavaScriptBundle
import com.intellij.lang.typescript.compiler.languageService.TypeScriptMessageBus
import com.intellij.lang.typescript.tsconfig.TypeScriptStatusBarWidget
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import com.intellij.openapi.wm.impl.status.widget.StatusBarEditorBasedWidgetFactory
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls

class GlintStatusBarWidgetFactory : StatusBarEditorBasedWidgetFactory() {
    @NonNls
    override fun getId(): String {
        return "GlintInfo"
    }

    @Nls
    override fun getDisplayName(): String {
        return "Glint"
    }

    override fun createWidget(project: Project): StatusBarWidget {
        return GlintStatusBarWidget(project)
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }
}


class GlintStatusBarWidget(project: Project) : EditorBasedStatusBarPopup(project, false) {
    @NonNls
    override fun ID(): String {
        return "GlintInfo"
    }

    override fun getWidgetState(file: VirtualFile?): WidgetState {
        var service: GlintTypeScriptService? = null
        if (file != null) {
            service = GlintLanguageServiceProvider(project).getService(file)
        }
        if (service == null) {
            service = this.service
        }
        val var10000: WidgetState
        return if (service == null) {
            var10000 = WidgetState.HIDDEN
            var10000
        } else {
            val text = service.getStatusText()
            if (text == null) {
                var10000 = WidgetState.HIDDEN
                var10000
            } else {
                WidgetState(text, text, true)
            }
        }
    }

    override fun registerCustomListeners() {
        TypeScriptMessageBus.get(this.project).register { this.update() }
        this.project.messageBus.connect().subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
            override fun exitDumbMode() {
                this@GlintStatusBarWidget.update()
            }
        })
        super.registerCustomListeners()
    }

    override fun createPopup(context: DataContext): ListPopup? {
        val group = createGroup(context)
        return JBPopupFactory.getInstance().createActionGroupPopup(null as String?, group, context, ActionSelectionAid.SPEEDSEARCH, false)
    }

    private fun createGroup(context: DataContext): ActionGroup {
        return object : ActionGroup() {
            override fun getChildren(e: AnActionEvent?): Array<AnAction> {
                val actions: MutableList<AnAction> = mutableListOf()
                val service: GlintTypeScriptService? = this@GlintStatusBarWidget.service
                if (service != null) {
                    actions.add(object : GlintRestartServiceAction() {
                        override fun setEnableAndVisible(presentation: Presentation, isEnabled: Boolean) {
                            presentation.isEnabled = isEnabled
                        }
                    })
                }
                return actions.toTypedArray()
            }
        }
    }

    override fun createInstance(project: Project): StatusBarWidget {
        return GlintStatusBarWidget(project)
    }

    private val service: GlintTypeScriptService?
    get() {
        val s = GlintLanguageServiceProvider(this.project).allServices.firstOrNull()
        if (s?.showStatusBar() == true) {
            return s
        }
        return null
    }

    companion object {
        val ID = "GlintInfo"
    }
}


open class GlintRestartServiceAction : AnAction("Restart Glint", "Restarts the glint language service", null) {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        if (project != null) {
            GlintLanguageServiceProvider(project).allServices.forEach { it.restart(true) }
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project != null) {
            val hasStarted = GlintLanguageServiceProvider(project).allServices.firstOrNull()?.isServiceCreated() == true
            val presentation = e.presentation
            if (project.guessProjectDir()?.emberRoot != null) {
                presentation.isVisible = true
            }
            setEnableAndVisible(presentation, hasStarted)
        }
    }

    protected open fun setEnableAndVisible(presentation: Presentation, isEnabled: Boolean) {
        presentation.isEnabledAndVisible = isEnabled
    }
}
