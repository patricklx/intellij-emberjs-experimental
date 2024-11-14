package com.emberjs.glint

import com.dmarcotte.handlebars.file.HbFileType
import com.dmarcotte.handlebars.psi.HbPsiFile
import com.emberjs.gts.GtsFileType
import com.emberjs.hbs.EmberReference
import com.emberjs.utils.EmberUtils
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
import com.intellij.lang.javascript.integration.JSAnnotationError.*
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.lang.javascript.psi.JSFunctionType
import com.intellij.lang.javascript.service.JSLanguageService
import com.intellij.lang.javascript.service.JSLanguageServiceProvider
import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.typescript.compiler.TypeScriptService
import com.intellij.lang.typescript.compiler.languageService.TypeScriptLanguageServiceUtil
import com.intellij.lang.typescript.compiler.languageService.codeFixes.TypeScriptSuppressByCommentFix
import com.intellij.lang.typescript.compiler.languageService.protocol.commands.response.TypeScriptQuickInfoResponse
import com.intellij.lang.typescript.lsp.BaseLspTypeScriptService
import com.intellij.lang.typescript.lsp.LspAnnotationError
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.impl.LspServerImpl
import com.intellij.platform.lsp.impl.highlighting.DiagnosticAndQuickFixes
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.xml.XmlElement
import com.intellij.ui.EditorNotifications
import com.intellij.util.containers.toMutableSmartList
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.MarkupContent
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import java.util.concurrent.Future
import java.util.stream.Stream

class GlintLanguageServiceProvider(val project: Project) : JSLanguageServiceProvider {

    override fun isHighlightingCandidate(file: VirtualFile) = file.fileType is HbFileType || file.fileType is JavaScriptFileType || file.fileType is TypeScriptFileType || file.fileType is GtsFileType

    override fun getService(file: VirtualFile) = allServices.firstOrNull()

    override val allServices: List<GlintTypeScriptService>
        get() = if (EmberUtils.isEnabledEmberProject(project)) listOf(GlintTypeScriptService.getInstance(project)) else emptyList()
}



class GlintTypeScriptService(project: Project) : BaseLspTypeScriptService(project, GlintLspSupportProvider::class.java) {
    var currentlyChecking: PsiElement? = null
    val lspServerManager = LspServerManager.getInstance(project)

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
        return if (EmberUtils.isEnabledEmberProject(project)) getGlintDescriptor(project) else null
    }

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
        if (currentlyChecking == null && element.containingFile is HbPsiFile) {
            currentlyChecking = sourceElement
            if (element is LeafPsiElement) {
                element = element.parent!!
            }
            if (element.reference is EmberReference || element.references.find { it is EmberReference } != null) {
                currentlyChecking = null
                return emptyArray()
            }
            currentlyChecking = null
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
            val vfile = (element.originalVirtualFile as VirtualFileWindow).delegate
            val f = PsiManager.getInstance(element.project).findFile(vfile)!!
            elem = f.findElementAt(document.hostRanges.first().startOffset + element.textOffset)!!
            elem = DelegateElement(elem, element, document)
        }

        val links = withServer { requestExecutor.getElementDefinitions(element.originalVirtualFile!!, (elem as PsiElement).textOffset) }
        val psiManager = PsiManager.getInstance(project)
        return links?.map {
            val vFile = VfsUtil.findFileByURL(URL(it.targetUri))
            val file = vFile?.let { psiManager.findFile(it) }
            if (file == null) return@map null
            val doc = file.viewProvider.document
            val startOffset = doc.getLineStartOffset(it.targetRange.start.line)
            val offset = startOffset + it.targetRange.start.character
            return@map file.findElementAt(offset)
        }?.filterNotNull()?.toTypedArray() ?: emptyArray<PsiElement>()
    }

    override fun getNavigationFor(document: Document, elem: PsiElement): Array<PsiElement> {
        return getNavigationFor(document, elem, false)
    }


    override fun getSignatureHelp(file: PsiFile, context: CreateParameterInfoContext): Future<Stream<JSFunctionType>?>? = null

    override fun isDisabledByContext(context: VirtualFile): Boolean {
        return getDescriptor()?.isAvailable(context)?.not() ?: return true
    }

    override fun highlight(file: PsiFile): CompletableFuture<List<JSAnnotationError>>? {
        val server = getServer() ?: return completedFuture(emptyList())
        val virtualFile = file.virtualFile

        EditorNotifications.getInstance(project).updateNotifications(virtualFile)

        return completedFuture(server.getDiagnosticsAndQuickFixes(virtualFile).map {
            GlintAnnotationError(it, virtualFile.canonicalPath)
        })
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

    override fun intoLookupElement() = LookupElementBuilder.create(item.label)
            .withTypeText(item.detail, true)
            .withInsertHandler(JSInsertHandler.DEFAULT)
}

class GlintAnnotationError(val diagFixes: DiagnosticAndQuickFixes, private val path: String?) : JSAnnotationError {
    val diagnostic = diagFixes.diagnostic
    public val fixes = diagFixes.quickFixes
    override fun getLine() = diagnostic.range.start.line
    val endLine = diagnostic.range.end.line
    val endColumn = diagnostic.range.end.character
    val diagCode = diagnostic.code?.get()
    override fun getColumn() = diagnostic.range.start.character
    val code by lazy { diagnostic.source?.toString() }

    override fun getAbsoluteFilePath(): String? = path

    override fun getDescription(): String = diagnostic.message + " (${diagnostic.source}${diagCode?.let { ":${it}" } ?: ""})"

    override fun getCategory() = when (diagnostic.severity) {
        DiagnosticSeverity.Error -> ERROR_CATEGORY
        DiagnosticSeverity.Warning -> WARNING_CATEGORY
        DiagnosticSeverity.Hint, DiagnosticSeverity.Information -> INFO_CATEGORY
    }
}
