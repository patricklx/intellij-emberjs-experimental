package com.emberjs.glint

import com.dmarcotte.handlebars.file.HbFileType
import com.emberjs.gts.GtsFileType
import com.emberjs.utils.parentModule
import com.emberjs.utils.slice
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.OSProcessUtil
import com.intellij.javascript.nodejs.interpreter.NodeCommandLineConfigurator
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.javascript.nodejs.reference.NodeModuleManager
import com.intellij.lang.javascript.JavaScriptFileType
import com.intellij.lang.javascript.TypeScriptFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.platform.lsp.api.LspServerDescriptor
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.psi.PsiManager
import com.intellij.util.FileContentUtil
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*
import kotlin.concurrent.schedule

class GlintLspSupportProvider : LspServerSupportProvider {
    var willStart = false
    override fun fileOpened(project: Project, file: VirtualFile, serverStarter: LspServerSupportProvider.LspServerStarter) {
        if (!getGlintDescriptor(project).isAvailable(file)) return
        serverStarter.ensureServerStarted(getGlintDescriptor(project))
    }
}


fun getGlintDescriptor(project: Project): GlintLspServerDescriptor {
    return project.getService(GlintLspServerDescriptor::class.java)
}


@Service
class GlintLspServerDescriptor(private val myProject: Project) : LspServerDescriptor(myProject, "Glint"), Disposable {
    val psiManager = PsiManager.getInstance(myProject)
    val lspServerManager = LspServerManager.getInstance(project)
    var isWsl = false
    var wslDistro = ""
    var lastDir = project.guessProjectDir()

    public val server
        get() =
           lspServerManager.getServersForProvider(GlintLspSupportProvider::class.java).firstOrNull()

    fun isAvailableFromDir(file: VirtualFile): Boolean {
        val workingDir = file
        var isWsl = false
        if (workingDir.path.contains("wsl.localhost") || workingDir.path.contains("wsl\$")) {
            isWsl = true
        }
        if (isWsl) {
            val wslWorkdir = "/" + workingDir.path.split("/").slice(4).joinToString("/")
            val path = "$wslWorkdir/node_modules/@glint/core/bin/glint-language-server.js"
            val builder = ProcessBuilder()
                .command("wsl", "--", "test", "-f", "\"$path\"", "&&", "echo", "\"true\"")
            val p = builder.start()
            p.waitFor()
            val out = p.inputStream.reader().readText().trim()
            if (out == "true") {
                lastDir = workingDir
                return true
            }
        }
        val glintPkg = NodeModuleManager.getInstance(project).collectVisibleNodeModules(workingDir).find { it.name == "@glint/core" }?.virtualFile
        if (glintPkg == null) {
            return false
        }
        glintPkg.findFileByRelativePath("bin/glint-language-server.js") ?: return false
        lastDir = workingDir
        return true
    }

    fun isAvailable(vfile: VirtualFile): Boolean {
        if (vfile.parentModule != null && isAvailableFromDir(vfile.parentModule!!)) {
            return true
        }
        if (project.guessProjectDir() != null && isAvailableFromDir(project.guessProjectDir()!!)) {
            return true
        }
        return false
    }

    fun ensureStarted(vfile: VirtualFile) {
        if (!isAvailable(vfile)) return
        lspServerManager.startServersIfNeeded(GlintLspSupportProvider::class.java)
    }

    override fun createCommandLine(): GeneralCommandLine {
        val workingDir = lastDir!!
        val workDirectory = VfsUtilCore.virtualToIoFile(workingDir)
        var path = workDirectory.path
        path = path.replace("\\", "/")
        this.isWsl = false
        if (path.startsWith("//wsl.localhost") || path.startsWith("//wsl\$")) {
            this.wslDistro = Regex("//wsl.localhost/([^/]+)").find(path)?.groups?.get(0)?.value ?: Regex("//wsl\\$/([^/]+)").find(path)!!.groups.get(0)!!.value
            path = path.replace("//wsl.localhost/[^/]+".toRegex(), "")
            path = path.replace("//wsl\\$/[^/]+".toRegex(), "")
            this.isWsl = true
        }
        val commandLine = GeneralCommandLine()
                .withCharset(StandardCharsets.UTF_8)
                .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
                .withWorkDirectory(workDirectory)

        ApplicationManager.getApplication().runReadAction {
//            val glintPkg = NodeModuleManager.getInstance(project).collectVisibleNodeModules(workingDir).find { it.name == "@glint/core" }?.virtualFile
//                    ?: throw RuntimeException("glint is not installed")
//            val file = glintPkg.findFileByRelativePath("bin/glint-language-server.js")
//                    ?: throw RuntimeException("glint lsp was not found")
            //commandLine.addParameter("--inspect-brk")
            commandLine.addParameter("$path/node_modules/@glint/core/bin/glint-language-server.js")
            commandLine.addParameter("--stdio")
            if (!this.isWsl) {
                commandLine.addParameter("--clientProcessId=" + OSProcessUtil.getCurrentProcessId().toString())
            }
        }


        NodeCommandLineConfigurator
                .find(NodeJsInterpreterRef.createProjectRef().resolve(project)!!)
                .configure(commandLine)
        return commandLine
    }

    override fun startServerProcess(): OSProcessHandler {
        val r = super.startServerProcess()
        Timer().schedule(5000) {
            ApplicationManager.getApplication().invokeLater {
                DaemonCodeAnalyzer.getInstance(project).restart()
                ApplicationManager.getApplication().runWriteAction {
                    FileContentUtil.reparseOpenedFiles()
                }
            }
        }
        return r;
    }

    override fun findFileByUri(fileUri: String): VirtualFile? {
        if (this.isWsl) {
            val uri = fileUri.replace("file://", "file://${this.wslDistro}")
            return super.findFileByUri(uri)
        }
        return super.findFileByUri(fileUri)
    }

    override fun getFilePath(file: VirtualFile): String {
        var path = super.getFilePath(file)
        if (!path.startsWith("/")) {
            path = "/$path"
        }
        if (path.startsWith("//wsl.localhost") || path.startsWith("//wsl\$")) {
            path = path.replace("//wsl.localhost/[^/]+".toRegex(), "")
            path = path.replace("//wsl\\$/[^/]+".toRegex(), "")
        }
        return URLEncoder.encode(path, "utf-8")
                .replace("%2F", "/")
                .replace("%253A", ":")
                .replace("%3A", ":")
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

    override val lspDiagnosticsSupport = null
    override val lspGoToDefinitionSupport = false
    override val lspCompletionSupport = null

    override fun dispose() {}
}

