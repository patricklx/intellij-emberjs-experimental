package com.emberjs.hbs.linter.eslint

import com.dmarcotte.handlebars.file.HbFileViewProvider
import com.dmarcotte.handlebars.psi.HbPsiFile
import com.emberjs.gts.GtsFile
import com.emberjs.gts.GtsFileViewProvider
import com.emberjs.settings.EmberApplicationOptions
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.util.IntentionName
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.javascript.JavaScriptBundle
import com.intellij.lang.javascript.linter.*
import com.intellij.lang.javascript.linter.eslint.*
import com.intellij.lang.javascript.validation.JSAnnotatorProblemGroup
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.ObjectUtils
import com.intellij.util.containers.ContainerUtil
import icons.JavaScriptLanguageIcons
import javax.swing.Icon


class MyEslintFixSingleErrorAction(toolName: @IntentionName String, file: PsiFile, val fixInfo: EslintError.FixInfo, errorCode: String?, modificationStamp: Long) : EslintFixSingleErrorAction(toolName, file, fixInfo, errorCode, modificationStamp) {
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (StringUtil.equals(myErrorCode, "linebreak-style")) {
            return false
        }

        return editor != null && editor.document.modificationStamp == myModificationStamp
    }
}


class FakeVirtualVile(val virtualFile: VirtualFile): VirtualFile() {

    override fun isInLocalFileSystem() = true
    override fun getExtension() = "ts"
    override fun getName() = virtualFile.name
    override fun getFileSystem() = virtualFile.fileSystem
    override fun getPath() = virtualFile.path.replace(".gts", ".ts").replace(".hbs", ".ts")
    override fun isWritable() = virtualFile.isWritable
    override fun isDirectory() = virtualFile.isDirectory
    override fun isValid() = virtualFile.isValid
    override fun getParent() = virtualFile.parent
    override fun getChildren() = virtualFile.children
    override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long) = virtualFile.getOutputStream(requestor, newModificationStamp, newTimeStamp)
    override fun contentsToByteArray() = virtualFile.contentsToByteArray()
    override fun getTimeStamp() = virtualFile.timeStamp
    override fun getLength() = virtualFile.length
    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) = virtualFile.refresh(asynchronous, recursive, postRunnable)
    override fun getInputStream() = virtualFile.inputStream
    override fun getFileType() = virtualFile.fileType
    override fun getModificationStamp() = 0.0.toLong()
}

class FakeFile(val psiFile: PsiFile): PsiFile by psiFile {
    override fun getVirtualFile() = FakeVirtualVile(psiFile.virtualFile)
}

class GtsEsLintFixAction : EsLintFixAction() {

    public override fun fixFile(psiFile: PsiFile): String? {
        return super.fixFile(FakeFile(psiFile))
    }

    public override fun isFileAccepted(project: Project, file: VirtualFile): Boolean {
        val f = PsiManager.getInstance(project).findFile(file)
        return f?.viewProvider is GtsFileViewProvider || f?.viewProvider is HbFileViewProvider
    }

    override fun asIntentionAction(): IntentionAction {
        val x = super.asIntentionAction()
        return object : IntentionAction by x {
            override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
                return file?.viewProvider is GtsFileViewProvider || file?.viewProvider is HbFileViewProvider
            }
        }
    }
}

class GtsEslintExternalAnnotator: EslintExternalAnnotator() {

    override fun createInfo(psiFile: PsiFile, state: EslintState, colorsScheme: EditorColorsScheme?): JSLinterInput<EslintState> {
        return super.createInfo(FakeFile(psiFile.viewProvider.getPsi(HTMLLanguage.INSTANCE)), state, colorsScheme)
    }
    override fun acceptPsiFile(file: PsiFile): Boolean {
        if (file is GtsFile && EmberApplicationOptions.runEslintOnGts) return true
        if (file.viewProvider is HbFileViewProvider && file is HbPsiFile && EmberApplicationOptions.runEslintOnHbs) return true
        return false
    }

    override fun apply(file: PsiFile, annotationResult: JSLinterAnnotationResult?, holder: AnnotationHolder) {
        if (annotationResult != null) {
            val fixFileAction = GtsEsLintFixAction().asIntentionAction()
            val toolName = JavaScriptBundle.message("settings.javascript.linters.eslint.configurable.name", *arrayOfNulls(0))
            val icon = JavaScriptLanguageIcons.FileTypes.Eslint
            apply(file, annotationResult, holder, fixFileAction, toolName, icon, false, null as String?, this.inspectionClass)
        }
    }

    companion object {
        fun apply(file: PsiFile, annotationResult: JSLinterAnnotationResult, holder: AnnotationHolder, fixFileAction: IntentionAction, toolName: String, icon: Icon?, editConfig: Boolean, editSettingCaption: String?, inspectionClass: Class<out JSLinterInspection?>?) {
            val document = PsiDocumentManager.getInstance(file.project).getDocument(file)
            val documentModificationStamp = document?.modificationStamp ?: -1L
            val configurable = EslintConfigurable(file.project, true)
            val editSettingsAction = JSLinterEditSettingsAction(configurable, (ObjectUtils.coalesce(editSettingCaption, configurable.displayName) as String), icon)
            val fixes = JSLinterStandardFixes().setEditConfig(editConfig).setEditSettingsAction(editSettingsAction).setShowEditSettings(false).setErrorToIntentionConverter { error: JSLinterErrorBase ->
                if (error !is EslintError) {
                    return@setErrorToIntentionConverter ContainerUtil.emptyList<IntentionAction>()
                } else {
                    val result: MutableList<IntentionAction> = mutableListOf()
                    val fixInfo: EslintError.FixInfo? = error.fixInfo
                    if (fixInfo != null) {
                        if (document != null && !holder.isBatchMode) {
                            result.add(MyEslintFixSingleErrorAction(toolName, file, fixInfo, error.getCode(), documentModificationStamp))
                        }
                        result.add(fixFileAction)
                    } else if (error.suggestions.isNotEmpty()) {
                        val var11: Iterator<*> = error.suggestions.iterator()
                        while (var11.hasNext()) {
                            val suggestion = var11.next() as EslintError.FixInfo
                            result.add(MyEslintFixSingleErrorAction(toolName, file, suggestion, error.getCode(), documentModificationStamp))
                        }
                    } else if (!holder.isBatchMode) {
                        ContainerUtil.addIfNotNull(result, ESLintSuppressionUtil.INSTANCE.getSuppressForLineAction(error, documentModificationStamp))
                    }
                    return@setErrorToIntentionConverter result
                }
            }.setProblemGroup { error: JSLinterErrorBase? ->
                if (holder.isBatchMode) {
                    return@setProblemGroup null
                } else if (error is EslintError) {
                    val intentionActions = ESLintSuppressionUtil.INSTANCE.getSuppressionsForError((error as EslintError?)!!, documentModificationStamp)
                    return@setProblemGroup JSAnnotatorProblemGroup(intentionActions, null as String?)
                } else {
                    return@setProblemGroup null
                }
            }
            JSLinterAnnotationsBuilder(file, annotationResult, holder, configurable, "$toolName: ", inspectionClass!!, fixes).setHighlightingGranularity(HighlightingGranularity.element).setDefaultFileLevelErrorIcon(icon).apply()
        }
    }

}