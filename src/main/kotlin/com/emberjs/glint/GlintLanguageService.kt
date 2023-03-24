package com.emberjs.glint

import com.dmarcotte.handlebars.file.HbFileType
import com.emberjs.gts.GtsFileType
import com.emberjs.utils.emberRoot
import com.emberjs.utils.originalVirtualFile
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.injected.editor.DocumentWindow
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.javascript.nodejs.reference.NodeModuleManager
import com.intellij.lang.javascript.JavaScriptFileType
import com.intellij.lang.javascript.TypeScriptFileType
import com.intellij.lang.javascript.integration.JSAnnotationError
import com.intellij.lang.javascript.integration.JSAnnotationError.*
import com.intellij.lang.javascript.psi.JSFunctionType
import com.intellij.lang.javascript.service.*
import com.intellij.lang.javascript.service.protocol.JSLanguageServiceAnswer
import com.intellij.lang.javascript.service.protocol.JSLanguageServiceCommand
import com.intellij.lang.javascript.service.protocol.JSLanguageServiceObject
import com.intellij.lang.javascript.service.protocol.JSLanguageServiceProtocol
import com.intellij.lang.javascript.service.ui.JSLanguageServiceToolWindowManager
import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.typescript.compiler.TypeScriptService
import com.intellij.lang.typescript.compiler.languageService.TypeScriptMessageBus
import com.intellij.lang.typescript.compiler.languageService.TypeScriptServerServiceImpl
import com.intellij.lang.typescript.tsconfig.TypeScriptConfigService
import com.intellij.lsp.LspServer
import com.intellij.lsp.LspServerDescriptor
import com.intellij.lsp.LspServerManager
import com.intellij.lsp.data.LspCompletionItem
import com.intellij.lsp.data.LspDiagnostic
import com.intellij.lsp.data.LspSeverity
import com.intellij.lsp.methods.HoverMethod
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.Consumer
import icons.JavaScriptLanguageIcons.Typescript
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.Future
import java.util.stream.Stream

class GlintLanguageServiceProvider(val project: Project) : JSLanguageServiceProvider {

    override fun isHighlightingCandidate(file: VirtualFile) = file.fileType is HbFileType || file.fileType is JavaScriptFileType || file.fileType is TypeScriptFileType || file.fileType is GtsFileType

    override fun getService(file: VirtualFile) = allServices.firstOrNull()

    override fun getAllServices() =
            if (project.guessProjectDir()?.emberRoot != null) listOf(GlintTypeScriptService.getInstance(project)) else emptyList()
}


class GlintLanguageServiceQueue: JSLanguageServiceQueue(){
    override fun dispose() {
        TODO("Not yet implemented")
    }

    override fun getStartErrorMessage(): String {
        TODO("Not yet implemented")
    }

    override fun getState(): JSLanguageServiceExecutor.State {
        TODO("Not yet implemented")
    }

    override fun isValid(): Boolean {
        TODO("Not yet implemented")
    }

    override fun resetCaches() {
        TODO("Not yet implemented")
    }

    override fun <T : Any?> executeWithCache(p0: JSLanguageServiceCacheableCommand, p1: JSLanguageServiceCacheableCommandProcessor<out T>): CompletableFuture<T>? {
        TODO("Not yet implemented")
    }

    override fun <T : Any?> execute(p0: JSLanguageServiceCommand, p1: JSLanguageServiceCommandProcessor<T>): CompletableFuture<T>? {
        TODO("Not yet implemented")
    }

    override fun executeNoBlocking(p0: JSLanguageServiceCommand, p1: Consumer<in JSLanguageServiceAnswer>?, p2: Consumer<in JSLanguageServiceObject>?): CompletableFuture<Void>? {
        TODO("Not yet implemented")
    }

    override fun executeNoBlocking(p0: JSLanguageServiceCommand, p1: Consumer<in JSLanguageServiceAnswer>?): CompletableFuture<Void>? {
        TODO("Not yet implemented")
    }

}

class GlintTypeScriptService(private val project: Project) : TypeScriptServerServiceImpl(project), Disposable {
    companion object {
        private val LOG = Logger.getInstance(GlintTypeScriptService::class.java)
        fun getInstance(project: Project): GlintTypeScriptService = project.getService(GlintTypeScriptService::class.java)
    }

    fun getServicePath(): String? {
        val workingDir = project.guessProjectDir()!!
        val glintPkg = NodeModuleManager.getInstance(project).collectVisibleNodeModules(workingDir).find { it.name == "@glint/core" }?.virtualFile
        if (glintPkg == null) {
            return null
        }
        val file = glintPkg.findFileByRelativePath("bin/glint-language-server.js")
        if (file == null) {
            return null
        }
        return glintPkg.path
    }

    override fun createLanguageServiceQueue(): JSLanguageServiceQueue? {
        return super.createLanguageServiceQueue()
    }

    fun getDescriptor(virtualFile: VirtualFile): LspServerDescriptor? {
        return if (!LspServerManager.isFileAcceptable(virtualFile)) null else getDescriptor()
    }

    override fun isAcceptableNonTsFile(project: Project, service: TypeScriptConfigService, virtualFile: VirtualFile): Boolean {
        if (virtualFile.fileType == GtsFileType.INSTANCE) {
            return true
        }
        return super.isAcceptableNonTsFile(project, service, virtualFile)
    }

    fun getDescriptor(): LspServerDescriptor? {
        return if (project.guessProjectDir()?.emberRoot != null) getGlintDescriptor(project) else null
    }

    override fun getStatusText(): String {
        return "Glint " + super.getStatusText()
    }

    override val name = "Glint TypeScript"

    override fun isDisabledByContext(context: VirtualFile): Boolean {
        val workingDir = project.guessProjectDir()!!
        val glintPkg = NodeModuleManager.getInstance(project).collectVisibleNodeModules(workingDir).find { it.name == "@glint/core" }?.virtualFile
        if (glintPkg == null) {
            return true
        }
        val file = glintPkg.findFileByRelativePath("bin/glint-language-server.js")
        if (file == null) {
            return true
        }
        return false
    }


    override fun canHighlight(file: PsiFile) = file.fileType is HbFileType ||
            file.fileType is TypeScriptFileType ||
            file.fileType is GtsFileType ||
            file.fileType is JavaScriptFileType

    override fun isAcceptable(file: VirtualFile) = file.fileType is HbFileType ||
                                                   file.fileType is TypeScriptFileType ||
                                                   file.fileType is GtsFileType ||
                                                   file.fileType is JavaScriptFileType
}

class GlintCompletionEntry(internal val item: LspCompletionItem) : TypeScriptService.CompletionEntry {
    override val name: String get() = item.label
    val detail: String? get() = item.detail

    override fun intoLookupElement() = item.intoLookupElement()
}

class GlintAnnotationError(val diagnostic: LspDiagnostic, private val path: String?) : JSAnnotationError {
    override fun getLine() = diagnostic.range.start.line
    val endLine = diagnostic.range.asEclipseLspRange().end.line
    val endColumn = diagnostic.range.asEclipseLspRange().end.character
    override fun getColumn() = diagnostic.range.start.character
    val code by lazy { diagnostic.asEclipseLspDiagnostic().source?.toString() }

    override fun getAbsoluteFilePath(): String? = path

    override fun getDescription(): String = diagnostic.message

    override fun getCategory() = when (diagnostic.severity) {
        LspSeverity.Error -> ERROR_CATEGORY
        LspSeverity.Warning -> WARNING_CATEGORY
        LspSeverity.Hint, LspSeverity.Information -> INFO_CATEGORY
    }
}
