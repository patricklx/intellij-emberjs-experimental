package com.emberjs.glint

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lsp.LspServer
import com.intellij.lsp.methods.CommandMethod
import com.intellij.lsp.methods.CommandProcessor
import com.intellij.lsp.methods.LspServerMethodWithPsiElement
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.IncorrectOperationException
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageServer
import java.util.concurrent.CompletableFuture


class GlintCodeActionMethod private constructor(codeActionParams: CodeActionParams, commandProcessor: CommandProcessor) : LspServerMethodWithPsiElement<List<Either<Command?, CodeAction?>?>?, List<IntentionAction?>?>() {
    private val myCommandProcessor: CommandProcessor
    private val myCodeActionParams: CodeActionParams

    init {
        myCodeActionParams = codeActionParams
        myCommandProcessor = commandProcessor
    }

    override fun invokeLanguageServerMethod(languageServer: LanguageServer, lspServer: LspServer): CompletableFuture<List<Either<Command?, CodeAction?>?>?>? {
        return languageServer.textDocumentService.codeAction(myCodeActionParams)
    }

    override fun mapLanguageServerResponse(actions: List<Either<Command?, CodeAction?>?>?, lspServer: LspServer): List<IntentionAction> {
        return if (actions != null && actions.isNotEmpty()) {
            val objects: ArrayList<IntentionAction> = ArrayList()
            val var4: Iterator<*> = actions.iterator()
            while (var4.hasNext()) {
                val action: Either<*, *> = var4.next() as Either<*, *>
                val codeAction = action.right as CodeAction
                var command: Command? = codeAction.command
                if (command != null) {
                    objects.add(LspCommandIntentionAction(command, codeAction.title, lspServer, myCommandProcessor))
                }
            }
            objects
        } else {
            return emptyList()
        }
    }

    internal class LspCommandIntentionAction(command: Command, title: @NlsSafe String, lspServer: LspServer, commandProcessor: CommandProcessor) : IntentionAction {
        private val myLspServer: LspServer
        private val myCommandProcessor: CommandProcessor
        private val myCommand: Command
        private val myTitle: @NlsSafe String

        init {
            myCommand = command
            myTitle = title
            myLspServer = lspServer
            myCommandProcessor = commandProcessor
        }

        override fun getText(): String {
            val var10000 = myTitle
            return var10000
        }

        override fun getFamilyName(): String {
            val var10000 = myTitle
            return var10000
        }

        override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
            return true
        }

        @Throws(IncorrectOperationException::class)
        override fun invoke(project: Project, editor: Editor, file: PsiFile) {
            BackgroundTaskUtil.executeOnPooledThread(project) {
                if (!myCommandProcessor.processCommand(myCommand.command, myCommand.arguments)) {
                    myLspServer.invoke(CommandMethod(myCommand.command, myCommand.arguments) {
                        ReadAction.run<RuntimeException> {
                            if (!project.isDisposed) {
                                DaemonCodeAnalyzer.getInstance(project).restart()
                            }
                        }
                    })
                }
            }
        }

        override fun startInWriteAction(): Boolean {
            return false
        }
    }

    companion object {
        fun create(lspServer: LspServer, psiFile: PsiFile, diagnostic: Diagnostic, commandProcessor: CommandProcessor): GlintCodeActionMethod {
            return ReadAction.compute(ThrowableComputable<GlintCodeActionMethod, RuntimeException> {
                if (!psiFile.isValid) {
                    return@ThrowableComputable null
                } else {
                    val document = PsiDocumentManager.getInstance(psiFile.project).getDocument(psiFile)
                    if (document == null) {
                        return@ThrowableComputable null
                    } else {
                        val virtualFile = PsiUtilCore.getVirtualFile(psiFile)
                        if (virtualFile != null && lspServer.isFileOpened(virtualFile)) {
                            val context = CodeActionContext()
                            context.only = listOf("quickfix")
                            context.diagnostics = listOf(diagnostic)
                            context.triggerKind = CodeActionTriggerKind.Invoked
                            val codeActionParams = CodeActionParams(createDocumentIdentifier(lspServer, virtualFile), diagnostic.range, context)
                            return@ThrowableComputable GlintCodeActionMethod(codeActionParams, commandProcessor)
                        } else {
                            return@ThrowableComputable null
                        }
                    }
                }
            }) as GlintCodeActionMethod
        }
    }
}
