package com.emberjs.glint

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.OSProcessUtil
import com.intellij.javascript.nodejs.interpreter.NodeCommandLineConfigurator
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.javascript.nodejs.reference.NodeModuleManager
import com.intellij.lsp.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.FileContentUtil
import java.nio.charset.StandardCharsets

class GlintLspSupportProvider : LspServerSupportProvider {
    override fun getServerDescriptor(project: Project, p1: VirtualFile): LspServerDescriptor {
        val workingDir = project.guessProjectDir()!!
        val glintPkg = NodeModuleManager.getInstance(project).collectVisibleNodeModules(workingDir).find { it.name == "@glint/core" }!!.virtualFile
        if (glintPkg == null) {
            return LspServerDescriptor.emptyDescriptor()
        }
        val file = glintPkg.findFileByRelativePath("bin/glint-language-server.js")
        if (file == null) {
            return LspServerDescriptor.emptyDescriptor()
        }
        return project.getService(GlintLspServerDescriptor::class.java)
    }
}


class GlintLanguageServerConnectorStdio(serverDescriptor: LspServerDescriptor, processHandler: OSProcessHandler) : LanguageServerConnectorStdio(serverDescriptor, processHandler, ) {
    override fun getFilePath(file: VirtualFile): String {
        var path =super.getFilePath(file).replace(":", "%3A")
        if (!path.startsWith("/")) {
            path = "/$path"
        }
        return path
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
class GlintLspServerDescriptor(private val myProject: Project) : LspServerDescriptor(), Disposable {

    override fun getProject(): Project = myProject

    override fun createStdioServerStartingCommandLine(): GeneralCommandLine {
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
            //commandLine.addParameter("--inspect")
            commandLine.addParameter(file.path)
            commandLine.addParameter("--stdio")
            commandLine.addParameter("--clientProcessId=" + OSProcessUtil.getCurrentProcessId().toString())
        }


        NodeCommandLineConfigurator
                .find(NodeJsInterpreterRef.createProjectRef().resolve(project)!!)
                .configure(commandLine)
        return commandLine
    }

    override fun createServerConnector(): LanguageServerConnector {
        val socketModeDescriptor = this.socketModeDescriptor
        return if (socketModeDescriptor != null) {
            LanguageServerConnectorSocket(this, socketModeDescriptor)
        } else {
            val startingCommandLine = createStdioServerStartingCommandLine()
            LOG.debug("$this: starting server process using: $startingCommandLine")
            GlintLanguageServerConnectorStdio(this, OSProcessHandler(startingCommandLine))
        }
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

    override fun getRoot(): VirtualFile = project.guessProjectDir()!!

    override fun getSocketModeDescriptor(): SocketModeDescriptor? = null

    override fun useGenericCompletion() = true

    override fun useGenericHighlighting() = true

    override fun useGenericNavigation() = false
    override fun dispose() {}
}
