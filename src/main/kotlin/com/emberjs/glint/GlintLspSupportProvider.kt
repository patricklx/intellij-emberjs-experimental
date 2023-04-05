package com.emberjs.glint

import com.dmarcotte.handlebars.file.HbFileType
import com.emberjs.gts.GtsFileType
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.OSProcessUtil
import com.intellij.javascript.nodejs.interpreter.NodeCommandLineConfigurator
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.javascript.nodejs.reference.NodeModuleManager
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.javascript.JavaScriptFileType
import com.intellij.lang.javascript.TypeScriptFileType
import com.intellij.lsp.*
import com.intellij.lsp.api.LspServerDescriptor
import com.intellij.lsp.api.LspServerManager
import com.intellij.lsp.api.LspServerSupportProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.FileContentUtil
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.services.LanguageClient
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.function.Consumer

class GlintLspSupportProvider : LspServerSupportProvider {
    override fun fileOpened(project: Project, file: VirtualFile, serverStarter: LspServerSupportProvider.LspServerStarter) {
        getGlintDescriptor(project)?.let { serverStarter.ensureServerStarted(it) }
    }
}


class GlintLanguageServerConnectorStdio(server: LspServer, processHandler: OSProcessHandler) : LanguageServerConnectorStdio(server, processHandler) {


    override fun getFilePath(file: VirtualFile): String {
        var path = super.getFilePath(file)
        if (!path.startsWith("/")) {
            path = "/$path"
        }
        return URLEncoder.encode(path, "utf-8").replace("%2F", "/")
    }

    override fun initializeServer() {
        super.initializeServer()
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().runWriteAction {
                FileContentUtil.reparseOpenedFiles()
            }
        }
    }
}


fun getGlintDescriptor(project: Project): GlintLspServerDescriptor {
    return project.getService(GlintLspServerDescriptor::class.java)
}


@Service
class GlintLspServerDescriptor(private val myProject: Project) : LspServerDescriptor(myProject, "Glint"), Disposable {
    val psiManager = PsiManager.getInstance(myProject)
    val lspServerManager = LspServerManager.getInstance(project)

    public val server
        get() =
           lspServerManager.getServersForProvider(GlintLspSupportProvider::class.java).firstOrNull()

    override fun createCommandLine(): GeneralCommandLine {
        val workingDir = myProject.guessProjectDir()!!
        val workDirectory = VfsUtilCore.virtualToIoFile(workingDir)
        val commandLine = GeneralCommandLine()
                .withCharset(StandardCharsets.UTF_8)
                .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
                .withWorkDirectory(workDirectory)

        ApplicationManager.getApplication().runReadAction {
            val glintPkg = NodeModuleManager.getInstance(project).collectVisibleNodeModules(workingDir).find { it.name == "@glint/core" }?.virtualFile
                    ?: throw RuntimeException("glint is not installed")
            val file = glintPkg.findFileByRelativePath("bin/glint-language-server.js")
                    ?: throw RuntimeException("glint lsp was not found")
//            commandLine.addParameter("--inspect")
            commandLine.addParameter(file.path)
            commandLine.addParameter("--stdio")
            commandLine.addParameter("--clientProcessId=" + OSProcessUtil.getCurrentProcessId().toString())
        }


        NodeCommandLineConfigurator
                .find(NodeJsInterpreterRef.createProjectRef().resolve(project)!!)
                .configure(commandLine)
        return commandLine
    }

    override fun createServerConnector(lspServer: LspServer): LanguageServerConnector {
        val startingCommandLine = createCommandLine()
        LOG.debug("$this: starting server process using: $startingCommandLine")
        return GlintLanguageServerConnectorStdio(this.server!!, OSProcessHandler(startingCommandLine))
    }

    override fun createInitializationOptions(): Any {
        val result = com.google.gson.JsonParser.parseString("{}")
        return result
    }

    override fun getLanguageId(file: VirtualFile): String {
        if (file.extension?.lowercase() == "hbs") {
            return "handlebars"
        }
        return super.getLanguageId(file)
    }

    override fun isSupportedFile(file: VirtualFile): Boolean {
        return file.fileType is HbFileType ||
                file.fileType is TypeScriptFileType ||
                file.fileType is GtsFileType ||
                file.fileType is JavaScriptFileType
    }

    override val handlePublishDiagnostics = true
    override val useGenericNavigation = false

    override fun dispose() {}
}

