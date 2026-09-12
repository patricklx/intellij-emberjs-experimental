package com.emberjs.glint

import com.dmarcotte.handlebars.file.HbFileType
import com.emberjs.gts.GlintConfiguration
import com.emberjs.gts.GtsFileType
import com.emberjs.utils.parentEmberModule
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.OSProcessUtil
import com.intellij.execution.wsl.WslPath
import com.intellij.javascript.nodejs.PackageJsonData
import com.intellij.javascript.nodejs.interpreter.NodeCommandLineConfigurator
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.javascript.nodejs.interpreter.wsl.WslCommandLineConfigurator
import com.intellij.lang.javascript.JavaScriptFileType
import com.intellij.lang.javascript.TypeScriptFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.isFile
import com.intellij.platform.lsp.api.LspServerDescriptor
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.psi.PsiManager
import com.intellij.util.FileContentUtil
import org.eclipse.lsp4j.ServerInfo
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.schedule
import kotlin.io.path.Path

private const val AVAILABILITY_CACHE_TTL_MS = 5000L

class GlintLspSupportProvider : LspServerSupportProvider {
    var willStart = false
    override fun fileOpened(project: Project, file: VirtualFile, serverStarter: LspServerSupportProvider.LspServerStarter) {
        if (!getGlintDescriptor(project).isAvailable(file)) return
        getGlintDescriptor(project).ensureStarted(file)
    }
}


fun getGlintDescriptor(project: Project): GlintLspServerDescriptor {
    return project.getService(GlintLspServerDescriptor::class.java)
}


@Service(Service.Level.PROJECT)
class GlintLspServerDescriptor(private val myProject: Project) : LspServerDescriptor(myProject, "Glint"), Disposable {
    val psiManager = PsiManager.getInstance(myProject)
    val lspServerManager = LspServerManager.getInstance(project)
    var isWsl = false
    var wslDistro = ""
    var glintCoreDir: VirtualFile? = null
    private data class CachedAvailability(val available: Boolean, val checkedAt: Long)
    private val availabilityCache = ConcurrentHashMap<String, CachedAvailability>()
    private val availabilityProbesInFlight = ConcurrentHashMap.newKeySet<String>()

    public val server
        get() =
           lspServerManager.getServersForProvider(GlintLspSupportProvider::class.java).firstOrNull()

    /**
     * getAttributeDescriptor/getAttributesDescriptors calls this for every attribute of every
     * tag during highlighting, and on WSL this spawns a blocking `wsl.exe` process, so results
     * are cached for a short time. A short TTL (rather than caching forever) is used so that
     * e.g. installing @glint/core after the first check is picked up again quickly.
     *
     * The actual probe runs synchronously on the calling thread (not dispatched to another
     * thread) because callers here almost always already hold the read lock (PsiReference
     * resolution, reference providers, completion contributors). Blocking the caller on a
     * future computed on a different thread that itself needs to acquire a fresh read lock can
     * deadlock against a pending write action - the caller holds its read lock while waiting on
     * `.get()`, the pool thread waits for the write action, and the write action waits for the
     * caller's read lock to be released.
     *
     * Concurrent callers for the same path are de-duplicated via `availabilityProbesInFlight`: if
     * another thread is already probing this path, this call returns the last known result (or
     * `false` if none yet) immediately instead of running its own probe or waiting for the other
     * one to finish. This deliberately never blocks one caller on another - holding any lock (an
     * explicit one, or a `ConcurrentHashMap` bucket lock via `compute`) across the blocking
     * subprocess call and `runReadAction` below would reintroduce the same deadlock shape as the
     * `CompletableFuture` approach above: a caller that already holds a read lock could block on
     * that lock while a write action is waiting for that same read lock to be released. A stale
     * or momentarily-false result here is harmless - it just means "not available for this
     * highlighting pass," which corrects itself on the next pass once the in-flight probe
     * completes and populates the cache.
     */
    fun isAvailableFromDir(file: VirtualFile): Boolean {
        val cacheKey = file.path
        val now = System.currentTimeMillis()
        val cached = availabilityCache[cacheKey]
        if (cached != null && now - cached.checkedAt < AVAILABILITY_CACHE_TTL_MS) {
            return cached.available
        }
        if (!availabilityProbesInFlight.add(cacheKey)) {
            return cached?.available ?: false
        }
        try {
            val available = computeAvailabilityFromDir(file)
            availabilityCache[cacheKey] = CachedAvailability(available, System.currentTimeMillis())
            return available
        } finally {
            availabilityProbesInFlight.remove(cacheKey)
        }
    }

    private fun computeAvailabilityFromDir(workingDir: VirtualFile): Boolean {
        if (WslPath.isWslUncPath(workingDir.path)) {
            isWsl = true
            val wsl = WslPath.parseWindowsUncPath(workingDir.path)
            wslDistro = wsl?.wslRoot ?: ""
        }
        if (isWsl) {
            val path = "./node_modules/@glint/core/bin/glint-language-server.js"
            val builder = ProcessBuilder()
                .directory(File(workingDir.path))
                .command("wsl", "--", "test", "-f", "\"$path\"", "&&", "echo", "\"true\"")
            val p = builder.start()
            p.waitFor()
            val out = p.inputStream.reader().readText().trim()
            if (out == "true") {
                return ApplicationManager.getApplication().runReadAction<Boolean> {
                    glintCoreDir = workingDir.findFileByRelativePath("node_modules/@glint/core") ?: return@runReadAction false
                    true
                }
            }
        }
        return ApplicationManager.getApplication().runReadAction<Boolean> {
            val glintPkg = workingDir.findFileByRelativePath("node_modules/@glint/core") ?: return@runReadAction false
            glintPkg.findFileByRelativePath("bin/glint-language-server.js") ?: return@runReadAction false
            glintCoreDir = glintPkg
            true
        }
    }

    fun isAvailable(vfile: VirtualFile?): Boolean {
        if (ApplicationManager.getApplication().isUnitTestMode) {
            return false
        }
        val config = GlintConfiguration.getInstance(myProject)
        val pkg = config.getPackage()
        pkg.readOrDetect()
        val path = pkg.`package`.constantPackage?.systemIndependentPath
        if (path != null) {
            val fileExists = ApplicationManager.getApplication().runReadAction<Boolean> {
                val f = VfsUtil.findFile(Path(path), true)
                val serverFile = f?.findFileByRelativePath("bin/glint-language-server.js")
                
                if (serverFile != null && serverFile.exists()) {
                    if (WslPath.isWslUncPath(serverFile.path)) {
                        isWsl = true
                        val wsl = WslPath.parseWindowsUncPath(serverFile.path)
                        wslDistro = wsl?.wslRoot ?: ""
                    }
                    true
                } else {
                    false
                }
            }
            if (fileExists) return true
        }
        var f: VirtualFile? = vfile
        while (true) {
            val (isFile, parent, pathContainsNodeModules) = ApplicationManager.getApplication().runReadAction<Triple<Boolean, VirtualFile?, Boolean>> {
                Triple(f?.isFile == true, f?.parent, f?.path?.contains("node_modules") == true)
            }
            
            if (isFile) {
                f = parent
            }
            if (pathContainsNodeModules) {
                f = f?.parentEmberModule
                continue
            }
            if (f != null && isAvailableFromDir(f)) {
                return true
            }
            f = f?.parentEmberModule
            if (f == null) return false
        }
    }

    fun ensureStarted(vfile: VirtualFile) {
        if (!isAvailable(vfile)) return
        lspServerManager.ensureServerStarted(GlintLspSupportProvider::class.java, getGlintDescriptor(project))
        server?.let {
            if (it.initializeResult?.serverInfo == null) {
                it.initializeResult?.serverInfo = ServerInfo()
            }
            it.initializeResult?.serverInfo?.name = "Glint"
            it.initializeResult?.serverInfo?.version = getGlintVersion()
        }
    }

    fun getGlintVersion(): String? {
        return ApplicationManager.getApplication().runReadAction<String?> {
            val config = GlintConfiguration.getInstance(myProject)
            val pkg = config.getPackage()
            val path = pkg.`package`.constantPackage?.systemIndependentPath ?: glintCoreDir?.path ?: return@runReadAction null
            val f = VirtualFileManager.getInstance().findFileByNioPath(Path(path).resolve("./package.json"))
            PackageJsonData.getOrCreate(f!!).version?.rawVersion
        }
    }

    override fun createCommandLine(): GeneralCommandLine {
        val config = GlintConfiguration.getInstance(myProject)
        val pkg = config.getPackage()
        var path = pkg.`package`.constantPackage?.systemIndependentPath
        val dir = glintCoreDir ?: ApplicationManager.getApplication().runReadAction<VirtualFile?> {
            VfsUtil.findFile(Path(path!!), true)
        }

        val (workDirectoryPath, dirPath) = ApplicationManager.getApplication().runReadAction<Pair<String?, String?>> {
            var workDirectory = dir
            while (workDirectory != null && workDirectory.path.contains("node_modules")) {
                workDirectory = workDirectory.parent
            }
            Pair(workDirectory?.path, dir?.path)
        }

        val commandLine = GeneralCommandLine()
                .withCharset(StandardCharsets.UTF_8)
                .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
                .withWorkDirectory(workDirectoryPath)

        ApplicationManager.getApplication().runReadAction {
//            val glintPkg = NodeModuleManager.getInstance(project).collectVisibleNodeModules(workingDir).find { it.name == "@glint/core" }?.virtualFile
//                    ?: throw RuntimeException("glint is not installed")
//            val file = glintPkg.findFileByRelativePath("bin/glint-language-server.js")
//                    ?: throw RuntimeException("glint lsp was not found")
            //commandLine.addParameter("--inspect-brk")
            commandLine.addParameter("${dirPath!!}/bin/glint-language-server.js")
            commandLine.addParameter("--stdio")
            if (!this.isWsl) {
                commandLine.addParameter("--clientProcessId=" + OSProcessUtil.getCurrentProcessId().toString())
            }
        }

        if (isWsl) {
            WslCommandLineConfigurator
                .find(NodeJsInterpreterRef.createProjectRef().resolve(project)!!)
                .configure(commandLine)
        } else {
            NodeCommandLineConfigurator
                .find(NodeJsInterpreterRef.createProjectRef().resolve(project)!!)
                .configure(commandLine)
        }
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

    override fun findLocalFileByPath(path: String): VirtualFile? {
        if (this.isWsl && !path.startsWith(this.wslDistro.replace("\\", "/"))) {
            val uri = this.wslDistro.replace("\\", "/") + path
            return super.findLocalFileByPath(uri)
        }
        return super.findLocalFileByPath(path)
    }

    override fun findFileByUri(fileUri: String): VirtualFile? {
        if (this.isWsl) {
            val uri = fileUri.replace("file://", "file://${this.wslDistro.replace("\\", "/")}")
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

