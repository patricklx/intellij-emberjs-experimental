package com.emberjs.glint

import com.dmarcotte.handlebars.file.HbFileType
import com.emberjs.utils.emberRoot
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.lang.ecmascript6.resolve.JSFileReferencesUtil
import com.intellij.lang.javascript.JSStringUtil
import com.intellij.lang.javascript.JSTokenTypes
import com.intellij.lang.javascript.completion.JSInsertHandler
import com.intellij.lang.javascript.frameworks.modules.JSUrlImportsUtil
import com.intellij.lang.javascript.integration.JSAnnotationError
import com.intellij.lang.javascript.integration.JSAnnotationError.*
import com.intellij.lang.javascript.psi.JSFunctionType
import com.intellij.lang.javascript.service.JSLanguageService
import com.intellij.lang.javascript.service.JSLanguageServiceProvider
import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.typescript.compiler.TypeScriptService
import com.intellij.lang.typescript.compiler.languageService.TypeScriptLanguageServiceUtil
import com.intellij.lang.typescript.compiler.languageService.TypeScriptMessageBus
import com.intellij.lsp.LspServer
import com.intellij.lsp.LspServerDescriptor
import com.intellij.lsp.LspServerManager
import com.intellij.lsp.data.LspCompletionItem
import com.intellij.lsp.data.LspDiagnostic
import com.intellij.lsp.data.LspSeverity
import com.intellij.lsp.methods.HoverMethod
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.Future
import java.util.stream.Stream

class GlintLanguageServiceProvider(val project: Project) : JSLanguageServiceProvider {

    override fun isHighlightingCandidate(file: VirtualFile) = file.fileType is HbFileType

    override fun getService(file: VirtualFile) = allServices.firstOrNull()

    override fun getAllServices() =
            if (project.guessProjectDir()?.emberRoot != null) listOf(GlintTypeScriptService.getInstance(project)) else emptyList()
}

class GlintTypeScriptService(private val project: Project) : TypeScriptService, Disposable {
    companion object {
        private val LOG = Logger.getInstance(GlintTypeScriptService::class.java)
        fun getInstance(project: Project): GlintTypeScriptService = project.getService(GlintTypeScriptService::class.java)
    }

    fun getDescriptor(virtualFile: VirtualFile): LspServerDescriptor? {
        return if (!LspServerManager.isFileAcceptable(virtualFile)) null else getDescriptor()
    }

    fun getDescriptor(): LspServerDescriptor? {
        return if (project.guessProjectDir()?.emberRoot != null) getGlintDescriptor(project) else null
    }

    private fun <T> withServer(action: LspServer.() -> T): T? = getDescriptor()?.server?.action()

    override val name = "Glint LSP"

    override fun isDisabledByContext(context: VirtualFile) = false

    override fun isServiceCreated() = withServer { isRunning || isMalfunctioned } ?: false

    override fun showStatusBar() = withServer { totalFilesOpened != 0 } ?: false

    override fun getStatusText() = withServer {
        when {
            isRunning -> "Glint LSP"
            isMalfunctioned -> "Glint LSP ⚠"
            else -> "..."
        }
    }

    override fun openEditor(file: VirtualFile) {
        getDescriptor(file)?.server?.fileOpened(file)
    }
    override fun closeLastEditor(file: VirtualFile) {
        getDescriptor(file)?.server?.fileClosed(file)
    }

    override fun getCompletionMergeStrategy(parameters: CompletionParameters, file: PsiFile, context: PsiElement): TypeScriptService.CompletionMergeStrategy {
        return TypeScriptService.CompletionMergeStrategy.MERGE
    }

    override fun updateAndGetCompletionItems(virtualFile: VirtualFile, parameters: CompletionParameters): Future<List<TypeScriptService.CompletionEntry>?>? {
        val descriptor = getDescriptor(virtualFile) ?: return null
        return completedFuture(descriptor.getCompletionItems(parameters).map(::GlintCompletionEntry))
    }

    override fun getServiceFixes(file: PsiFile, element: PsiElement?, result: JSAnnotationError): Collection<IntentionAction> {
        return emptyList()
    }

    override fun getDetailedCompletionItems(virtualFile: VirtualFile,
                                            items: List<TypeScriptService.CompletionEntry>,
                                            document: Document,
                                            positionInFileOffset: Int): Future<List<TypeScriptService.CompletionEntry>?>? {
        val descriptor = getDescriptor(virtualFile) ?: return null
        return completedFuture(items.map { GlintCompletionEntry(descriptor.getResolvedCompletionItem((it as GlintCompletionEntry).item)) })
    }

    override fun getNavigationFor(document: Document, sourceElement: PsiElement): Array<PsiElement> =
            getDescriptor()?.getElementDefinitions(sourceElement)?.toTypedArray() ?: emptyArray()

    override fun getSignatureHelp(file: PsiFile, context: CreateParameterInfoContext): Future<Stream<JSFunctionType>?>? = null

    fun quickInfo(element: PsiElement): String? {
        val server = getDescriptor()?.server
        val raw = server?.invokeSynchronously(HoverMethod.create(server, element)) ?: return null
        LOG.info("Quick info for $element : $raw")
        return raw.substring("<html><body><pre>".length, raw.length - "</pre></body></html>".length)
    }

    override fun getQuickInfoAt(element: PsiElement, originalElement: PsiElement, originalFile: VirtualFile): CompletableFuture<String?> =
            completedFuture(quickInfo(element))

    override fun restart(recreateToolWindow: Boolean) {
        val descriptor = getDescriptor()
        if (!project.isDisposed && descriptor != null) {
            descriptor.restart()
            TypeScriptMessageBus.get(project).changed()
        }
    }

    override fun highlight(file: PsiFile): CompletableFuture<List<JSAnnotationError>>? {
        val server = getDescriptor()?.server ?: return completedFuture(emptyList())
        val virtualFile = file.virtualFile
        val changedUnsaved = collectChangedUnsavedFiles()
        if (changedUnsaved.isNotEmpty()) {
            JSLanguageService.saveChangedFilesAndRestartHighlighting(file, changedUnsaved)
            return null
        }

        return completedFuture(server.getDiagnostics(virtualFile)?.map {
            GlintAnnotationError(it, virtualFile.canonicalPath)
        })
    }

    private fun collectChangedUnsavedFiles(): Collection<VirtualFile> {
        val manager = FileDocumentManager.getInstance()
        val openFiles = setOf(*FileEditorManager.getInstance(project).openFiles)
        val unsavedDocuments = manager.unsavedDocuments
        if (unsavedDocuments.isEmpty()) return emptyList()

        return unsavedDocuments
                .mapNotNull { manager.getFile(it) }
                .filter { vFile -> !openFiles.contains(vFile) && isAcceptable(vFile) }
    }

    override fun canHighlight(file: PsiFile) = file.fileType is HbFileType

    override fun isAcceptable(file: VirtualFile) = file.fileType is HbFileType

    override fun dispose() {}
}

class GlintCompletionEntry(internal val item: LspCompletionItem) : TypeScriptService.CompletionEntry {
    override val name: String get() = item.label

    override fun intoLookupElement() = item.intoLookupElement()
}

class GlintAnnotationError(val diagnostic: LspDiagnostic, private val path: String?) : JSAnnotationError {
    override fun getLine() = diagnostic.range.start.line

    override fun getColumn() = diagnostic.range.start.character
    val code by lazy { diagnostic.asEclipseLspDiagnostic().code?.toString() }

    override fun getAbsoluteFilePath(): String? = path

    override fun getDescription(): String = diagnostic.message

    override fun getCategory() = when (diagnostic.severity) {
        LspSeverity.Error -> ERROR_CATEGORY
        LspSeverity.Warning -> WARNING_CATEGORY
        LspSeverity.Hint, LspSeverity.Information -> INFO_CATEGORY
    }
}