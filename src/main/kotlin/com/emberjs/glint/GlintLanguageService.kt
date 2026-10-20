package com.emberjs.glint

import com.dmarcotte.handlebars.file.HbFileType
import com.dmarcotte.handlebars.psi.HbPsiFile
import com.emberjs.gts.GtsFileType
import com.emberjs.hbs.EmberReference
import com.emberjs.utils.originalVirtualFile
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.injected.editor.DocumentWindow
import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.lang.javascript.JavaScriptFileType
import com.intellij.lang.javascript.TypeScriptFileType
import com.intellij.lang.javascript.completion.JSInsertHandler
import com.intellij.lang.javascript.inspections.JSInspectionSuppressor
import com.intellij.lang.javascript.integration.JSAnnotationError
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.lang.javascript.psi.JSFunctionType
import com.intellij.lang.javascript.service.JSLanguageServiceProvider
import com.intellij.lang.typescript.compiler.TypeScriptService
import com.intellij.lang.typescript.compiler.languageService.protocol.commands.response.TypeScriptInlayHintsResult
import com.intellij.lang.typescript.compiler.languageService.protocol.commands.response.TypeScriptQuickInfoResponse
import com.intellij.lang.typescript.compiler.languageService.TypeScriptLanguageServiceUtil
import com.intellij.lang.typescript.compiler.languageService.codeFixes.TypeScriptSuppressByCommentFix
import com.intellij.lang.typescript.lsp.BaseLspTypeScriptService
import com.intellij.lang.typescript.lsp.LspAnnotationError
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.xml.XmlElement
import com.intellij.ui.EditorNotifications
import com.intellij.util.containers.toMutableSmartList
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.Position
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import kotlinx.coroutines.future.await

class GlintLanguageServiceProvider(val project: Project) : JSLanguageServiceProvider {
    val descriptor = getGlintDescriptor(project)

    override fun isHighlightingCandidate(file: VirtualFile) = file.fileType is HbFileType || file.fileType is JavaScriptFileType || file.fileType is TypeScriptFileType || file.fileType is GtsFileType

    override fun getService(file: VirtualFile) = if (descriptor.isAvailable(file)) GlintTypeScriptService.getInstance(project) else null

    override val allServices: List<GlintTypeScriptService>
        get() = if (descriptor.isAvailable(null)) listOf(GlintTypeScriptService.getInstance(project)) else emptyList()
}



class GlintTypeScriptService(project: Project) : BaseLspTypeScriptService(project, GlintLspSupportProvider::class.java, getGlintDescriptor(project)) {
    // Re-entrancy guard for getNavigationFor (resolving references below can call back into it). This is a
    // project service used from many threads at once, so the guard must be per thread.
    private val currentlyChecking = ThreadLocal<PsiElement?>()

    companion object {
        private val LOG = Logger.getInstance(GlintTypeScriptService::class.java)
        fun getInstance(project: Project): GlintTypeScriptService = project.getService(GlintTypeScriptService::class.java)
    }

    fun getDescriptor(virtualFile: VirtualFile): GlintLspServerDescriptor? {
        return if (getDescriptor()?.isSupportedFile(virtualFile) == true)
            getDescriptor()
        else
            null
    }

    fun getDescriptor(): GlintLspServerDescriptor? {
        return getGlintDescriptor(project)
    }

    override val name = "Glint"

    override fun getCompletionMergeStrategy(parameters: CompletionParameters, file: PsiFile, context: PsiElement): TypeScriptService.CompletionMergeStrategy {
        return TypeScriptLanguageServiceUtil.getCompletionMergeStrategy(parameters, file, context)
    }


    fun getSuppressActions(element: PsiElement?): List<BaseIntentionAction>? {
        if (element == null) return null
        if (element.containingFile !is JSFile) {
            return listOf(GlintHBSupressErrorFix("ignore"), GlintHBSupressErrorFix("expect"))
        }
        val aClass = JSInspectionSuppressor.getHolderClass(element)
        return listOf(TypeScriptSuppressByCommentFix(aClass), TypeScriptSupressByExpectErrorFix(aClass))
    }

    override fun getServiceFixes(file: PsiFile, element: PsiElement?, result: JSAnnotationError): List<IntentionAction> {
        val list = ((result as? LspAnnotationError)?.quickFixes ?: emptyList()).toMutableSmartList()
        getSuppressActions(element)?.apply(list::addAll)
        return list
    }

    fun getNavigationFor(document: Document, elem: PsiElement, includeOther: Boolean): Array<PsiElement> {
        var sourceElement: PsiElement = elem
        if (!includeOther && (sourceElement is XmlElement || sourceElement.containingFile is HbPsiFile)) {
            return emptyArray()
        }
        var element = sourceElement.containingFile.originalFile.findElementAt(sourceElement.textOffset) ?: sourceElement
        if (currentlyChecking.get() == null && element.containingFile is HbPsiFile) {
            currentlyChecking.set(sourceElement)
            try {
                if (element is LeafPsiElement) {
                    element = element.parent ?: return emptyArray()
                }
                if (element.reference is EmberReference || element.references.find { it is EmberReference } != null) {
                    return emptyArray()
                }
            } finally {
                currentlyChecking.remove()
            }
        }
        if (sourceElement.containingFile.fileType == GtsFileType.INSTANCE) {
            element = sourceElement
        }
        class DelegateElement(val element: PsiElement, val origElement: PsiElement, val documentWindow: DocumentWindow) : PsiElement by element {
            override fun getTextRange(): TextRange {
                val range = origElement.textRange
                val hostRange = documentWindow.hostRanges.first()
                return TextRange(hostRange.startOffset + range.startOffset, hostRange.startOffset + range.endOffset)
            }
        }

        var elem: Any = element
        if (document is DocumentWindow) {
            val vfile = (element.originalVirtualFile as? VirtualFileWindow)?.delegate ?: return emptyArray()
            val f = PsiManager.getInstance(element.project).findFile(vfile) ?: return emptyArray()
            elem = f.findElementAt(document.hostRanges.first().startOffset + element.textOffset) ?: return emptyArray()
            elem = DelegateElement(elem, element, document)
        }

        val virtualFile = element.originalVirtualFile ?: return emptyArray()
        val client = LspClientManager.getInstance(project).getClients(GlintLspSupportProvider::class.java).firstOrNull() ?: return emptyArray()
        val identifier = client.getDocumentIdentifier(virtualFile)
        val textOffset = (elem as PsiElement).textOffset
        val fileDoc = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(virtualFile)
            ?: return emptyArray()
        val line = fileDoc.getLineNumber(textOffset)
        val col = textOffset - fileDoc.getLineStartOffset(line)
        val links = client.sendRequestSync(LspClient.DEFAULT_REQUEST_TIMEOUT_MS) { server ->
            server.textDocumentService.definition(DefinitionParams(identifier, Position(line, col)))
        }?.let { either ->
            either.left?.map { loc ->
                org.eclipse.lsp4j.LocationLink().also {
                    it.targetUri = loc.uri
                    it.targetRange = loc.range
                    it.targetSelectionRange = loc.range
                }
            } ?: either.right
        }
            ?: return emptyArray()
        val psiManager = PsiManager.getInstance(project)
        return links.mapNotNull {
            val vFile = VfsUtil.findFileByURL(URL(it.targetUri))
            val file = vFile?.let { psiManager.findFile(it) } ?: return@mapNotNull null
            val d = file.viewProvider.document ?: return@mapNotNull null
            val startOffset = d.getLineStartOffset(it.targetRange.start.line) + it.targetRange.start.character
            file.findElementAt(startOffset)
        }.toTypedArray()
    }

    override fun getNavigationFor(document: Document, elem: PsiElement, offsetInSourceElement: Int): Array<PsiElement> {
        return getNavigationFor(document, elem, false)
    }

    // Since 2026.2, BaseLspTypeScriptService sends inlay hints, signature help, quick info, completion,
    // diagnostics and type evaluation through TypeScriptLspClientCommandExecutor. That executor casts the
    // client descriptor to TypeScriptLspClientDescriptor and speaks tsserver-specific commands, neither of
    // which applies to Glint (a plain LSP server), so every such call failed with a ClassCastException.
    // The overrides below talk to the LSP client directly or opt out of the tsserver-only features.

    override fun supportsInlayHints(file: PsiFile): Boolean = false

    override suspend fun getInlayHints(file: PsiFile, range: TextRange): TypeScriptInlayHintsResult? = null

    override fun supportsTypeEvaluation(virtualFile: VirtualFile, element: PsiElement): Boolean = false

    override suspend fun getSignatureHelp(file: PsiFile, offset: Int): Sequence<JSFunctionType>? = null

    override fun getQuickInfoAt(usageElement: PsiElement, originalFile: VirtualFile): CompletableFuture<TypeScriptQuickInfoResponse?> =
        completedFuture(null)

    override suspend fun getCompletionItemsSuspending(virtualFile: VirtualFile, document: Document, offset: Int, parameters: CompletionParameters): List<TypeScriptService.CompletionEntry> {
        val client = LspClientManager.getInstance(project).getClients(GlintLspSupportProvider::class.java).firstOrNull() ?: return emptyList()
        val identifier = client.getDocumentIdentifier(virtualFile)
        val line = document.getLineNumber(offset)
        val col = offset - document.getLineStartOffset(line)
        val completionList: org.eclipse.lsp4j.CompletionList = client.sendRequest { server ->
            server.textDocumentService.completion(CompletionParams(identifier, Position(line, col)))
        }?.let { either ->
            if (either.isLeft) org.eclipse.lsp4j.CompletionList(false, either.left)
            else either.right
        } ?: return emptyList()
        return completionList.items.map { GlintCompletionEntry(it) }
    }

    override fun isDisabledByContext(context: VirtualFile): Boolean {
        if (getDescriptor()?.isAvailable(context) != true) return true
        EditorNotifications.getInstance(project).updateNotifications(context)
        return false
    }

    override fun postProcessErrors(file: PsiFile, errors: List<LspAnnotationError>): List<LspAnnotationError> {
        return errors.map { e ->
            val code = e.errorCode.takeIf { it != 0 }?.let { ":$it" } ?: ""
            val suffix = " (glint$code)"
            e.copyWith(e.description + suffix, e.tooltipText?.plus(suffix) ?: (e.description + suffix))
        }
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

class GlintCompletionEntry(internal val item: CompletionItem) : TypeScriptService.CompletionEntry {
    override val name: String get() = item.label
    val detail: String? get() = item.detail

    override fun createLookupElement() = LookupElementBuilder.create(item.label)
            .withTypeText(item.detail, true)
            .withInsertHandler(JSInsertHandler.DEFAULT)
}
