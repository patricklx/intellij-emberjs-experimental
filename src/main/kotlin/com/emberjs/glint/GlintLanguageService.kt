package com.emberjs.glint

import com.dmarcotte.handlebars.file.HbFileType
import com.dmarcotte.handlebars.psi.HbPsiFile
import com.emberjs.gts.GtsFileType
import com.emberjs.hbs.HbReference
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
import com.intellij.lang.javascript.psi.JSReferenceExpression
import com.intellij.lang.javascript.psi.ecma6.impl.TypeScriptSingleTypeImpl
import com.intellij.lang.javascript.service.*
import com.intellij.lang.javascript.service.protocol.JSLanguageServiceProtocol
import com.intellij.lang.javascript.service.ui.JSLanguageServiceToolWindowManager
import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.typescript.compiler.TypeScriptCompilerService
import com.intellij.lang.typescript.compiler.TypeScriptService
import com.intellij.lang.typescript.compiler.TypeScriptService.CompletionMergeStrategy
import com.intellij.lang.typescript.compiler.languageService.TypeScriptLanguageServiceUtil
import com.intellij.lang.typescript.compiler.languageService.TypeScriptMessageBus
import com.intellij.lang.typescript.compiler.languageService.TypeScriptServerServiceCompletionEntry
import com.intellij.lang.typescript.compiler.languageService.TypeScriptServerServiceImpl
import com.intellij.lang.typescript.compiler.languageService.protocol.commands.TypeScriptServiceCommandClean
import com.intellij.lang.typescript.compiler.languageService.protocol.commands.response.TypeScriptCompletionResponse.CompletionEntryDetail
import com.intellij.lang.typescript.compiler.languageService.protocol.commands.response.TypeScriptSymbolDisplayPart
import com.intellij.lang.typescript.compiler.ui.TypeScriptServerServiceSettings
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
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.Consumer
import icons.JavaScriptLanguageIcons.Typescript
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.Future
import java.util.stream.Stream
import javax.swing.text.AbstractDocument.LeafElement

class GlintLanguageServiceProvider(val project: Project) : JSLanguageServiceProvider {

    override fun isHighlightingCandidate(file: VirtualFile) = file.fileType is HbFileType || file.fileType is JavaScriptFileType || file.fileType is TypeScriptFileType || file.fileType is GtsFileType

    override fun getService(file: VirtualFile) = allServices.firstOrNull()

    override fun getAllServices() =
            if (project.guessProjectDir()?.emberRoot != null) listOf(GlintTypeScriptService.getInstance(project)) else emptyList()
}

class GlintTypeScriptService(private val project: Project) : TypeScriptService, Disposable {
    var currentlyChecking: PsiElement? =null
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

    override val name = "Glint TypeScript LSP"
    override fun isServiceCreated() = withServer { isRunning || isMalfunctioned } ?: false

    override fun showStatusBar() = withServer { isServiceCreated() } ?: false

    override fun getStatusText() = withServer {
        when {
            isRunning -> "Glint TypeScript LSP"
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
        return CompletionMergeStrategy.MERGE
    }

    override fun updateAndGetCompletionItems(virtualFile: VirtualFile, parameters: CompletionParameters): Future<List<TypeScriptService.CompletionEntry>?>? {
        val descriptor = getDescriptor(virtualFile) ?: return null
        return completedFuture(descriptor.getCompletionItems(parameters).map { descriptor.getResolvedCompletionItem(it) }.map { GlintCompletionEntry(it) })
    }

    override fun getServiceFixes(file: PsiFile, element: PsiElement?, result: JSAnnotationError): Collection<IntentionAction> {
        if (result as? GlintAnnotationError == null) {
            return emptyList()
        }
        val descriptor = getDescriptor(file.virtualFile) ?: return emptyList()
        return descriptor.getCodeActions(file, result.diagnostic) { command, _ ->
            if (command == "x") {
                return@getCodeActions true
            }
            return@getCodeActions false
        }
    }

    override fun getDetailedCompletionItems(virtualFile: VirtualFile,
                                            items: List<TypeScriptService.CompletionEntry>,
                                            document: Document,
                                            positionInFileOffset: Int): Future<List<TypeScriptService.CompletionEntry>?>? {
        val descriptor = getDescriptor(virtualFile) ?: return null
        return completedFuture(items.map { GlintCompletionEntry(descriptor.getResolvedCompletionItem((it as GlintCompletionEntry).item)) })
    }

    override fun getNavigationFor(document: Document, sourceElement: PsiElement): Array<PsiElement>? {
        var element = sourceElement.getContainingFile().getOriginalFile().findElementAt(sourceElement.textOffset+1)!!
        if (currentlyChecking == null && element.containingFile is HbPsiFile) {
            currentlyChecking = sourceElement
            if (element is LeafPsiElement) {
                element = element.parent!!
            }
            if (element.reference is HbReference || element.references.find { it is HbReference } != null) {
                currentlyChecking = null
                return null
            }
            currentlyChecking = null
        }
        if (sourceElement.getContainingFile().fileType == GtsFileType.INSTANCE) {
            element = sourceElement
        }
        class DelegateElement(val element: PsiElement, val origElement: PsiElement, val documentWindow: DocumentWindow): PsiElement by element {
            override fun getTextRange(): TextRange {
                val range = origElement.textRange
                val hostRange = documentWindow.hostRanges.first()
                return TextRange(hostRange.startOffset + range.startOffset, hostRange.startOffset + range.endOffset)
            }
        }
        var elem: Any = element
        if (document is DocumentWindow) {
            val vfile = (element.originalVirtualFile as VirtualFileWindow).delegate
            val f = PsiManager.getInstance(element.project).findFile(vfile)!!
            elem = f.findElementAt(document.hostRanges.first().startOffset + element.textOffset)!!
            elem = DelegateElement(elem, element, document)
        }

        return getDescriptor()?.getElementDefinitions(elem as PsiElement)?.toTypedArray()
    }


    override fun getSignatureHelp(file: PsiFile, context: CreateParameterInfoContext): Future<Stream<JSFunctionType>?>? = null

    fun quickInfo(element: PsiElement): String? {
        val server = getDescriptor()?.server
        val raw = server?.invokeSynchronously(HoverMethod.create(server, element)) ?: return null
        LOG.info("Quick info for $element : $raw")
        return raw.substring("<html><body><pre>".length, raw.length - "</pre></body></html>".length)
    }

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

    override fun getQuickInfoAt(element: PsiElement, originalElement: PsiElement, originalFile: VirtualFile): CompletableFuture<String?> =
            completedFuture(quickInfo(element))

    override fun restart(recreateToolWindow: Boolean) {
        val descriptor = getDescriptor()
        if (!project.isDisposed && descriptor != null) {
            descriptor.restart()
        }
    }

    override fun highlight(file: PsiFile): CompletableFuture<List<JSAnnotationError>>? {
        return null
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

    override fun canHighlight(file: PsiFile) = file.fileType is HbFileType ||
            file.fileType is TypeScriptFileType ||
            file.fileType is GtsFileType ||
            file.fileType is JavaScriptFileType

    override fun isAcceptable(file: VirtualFile) = file.fileType is HbFileType ||
                                                   file.fileType is TypeScriptFileType ||
                                                   file.fileType is GtsFileType ||
                                                   file.fileType is JavaScriptFileType

    override fun dispose() {
        return
    }
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

    override fun getDescription(): String = diagnostic.asEclipseLspDiagnostic().source + " " + diagnostic.message

    override fun getCategory() = when (diagnostic.severity) {
        LspSeverity.Error -> ERROR_CATEGORY
        LspSeverity.Warning -> WARNING_CATEGORY
        LspSeverity.Hint, LspSeverity.Information -> INFO_CATEGORY
    }
}
