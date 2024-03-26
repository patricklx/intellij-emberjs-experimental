package com.emberjs.vfs

import ai.grazie.utils.WeakHashMap
import com.emberjs.utils.parents
import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileAttributes
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.io.URLUtil
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.*
import java.util.stream.Collectors
import kotlin.concurrent.thread


class WslVirtualFileSystem: LocalFileSystemImpl() {

    private lateinit var process: Process
    private lateinit var processReader: BufferedReader
    private lateinit var processWriter: BufferedWriter
    private val mutex = Mutex()

    init {
        val bash = """
            IFS=";"
            while read line
            do
              read -ra command <<< "${'$'}line"
              type=${'$'}{command[0]}
              value=${'$'}{command[1]}
              if [ "${'$'}type" == "is-symlink" ]; then
                (test -L "${'$'}value") && echo "true" || echo "false"
              fi
              if [ "${'$'}type" == "read-symlink" ]; then
                readlink -f "${'$'}value"
              fi
            done
        """.trimIndent()
        val bashSingleLine = "IFS=\";\";while read line;do  read -ra command <<< \"${'$'}line\";  type=${'$'}{command[0]};  value=${'$'}{command[1]};  if [ \"${'$'}type\" == \"is-symlink\" ]; then    (test -L \"${'$'}value\") && echo \"true\" ||echo \"false\";  fi;  if [ \"${'$'}type\" == \"read-symlink\" ]; then    readlink -f \"${'$'}value\"; fi done"
        val builder = ProcessBuilder("wsl.exe",  "-e", "bash", "//home/patrick/files.sh")
        thread {
            val process = builder.start()
            this.process = process
            val stdin: OutputStream = process.outputStream // <- Eh?
            val stdout: InputStream = process.inputStream

            val reader = BufferedReader(InputStreamReader(stdout))
            val writer = BufferedWriter(OutputStreamWriter(stdin))
            this.processReader = reader;
            this.processWriter = writer;
        }

    }


    override fun getProtocol(): String {
        return "file"
    }

    fun isWslSymlink(file: VirtualFile): Boolean {
        if (file.isSymlink == true) {
            return true
        }
        if (file.isFromWSL() && file.parent != null) {
            try {
                val path: String = file.path.replace("^//wsl\\$/[^/]+".toRegex(), "").replace("""^//wsl.localhost/[^/]+""".toRegex(), "")
                while (!mutex.tryLock()) run {
                    Thread.sleep(5)
                }
                processWriter.write("is-symlink;${path}\n")
                processWriter.flush()
                val isSymLink = processReader.readLine()
                mutex.unlock()
                file.isSymlink = isSymLink.equals("true")
                return isSymLink.equals("true")
            } catch (e: Exception) {
                return false
            }
        }
        return false
    }

    fun getRealVirtualFile(file: VirtualFile): VirtualFile {
        val symlkinkWsl = file.parents.find { it.isFromWSL() && isWslSymlink(it) }
        val relative = symlkinkWsl?.path?.let { file.path.replace(it, "") }
        val resolved = symlkinkWsl?.let { virtualFile -> this.resolveSymLink(virtualFile)?.let { this.findFileByPath(it) } }
        return relative?.let { resolved?.findFileByRelativePath(it) } ?: file
    }

    override fun getAttributes(vfile: VirtualFile): FileAttributes? {
        val file = getRealVirtualFile(vfile)
        var attributes = super.getAttributes(file)
        if (attributes != null && attributes.type == null && isWslSymlink(file)) {
            val resolved = this.resolveSymLink(file)?.let { this.findFileByPath(it) }
            if (resolved != null) {
                val resolvedAttrs = super.getAttributes(resolved)
                attributes = FileAttributes(resolvedAttrs?.isDirectory ?: false, false, true, attributes.isHidden, attributes.length, attributes.lastModified, attributes.isWritable, FileAttributes.CaseSensitivity.SENSITIVE)
            }

        }
        return attributes
    }

    fun getWSLCanonicalPath(file: VirtualFile): String? {
        if (!file.isFromWSL()) {
            return null
        }

        if (file.cachedWSLCanonicalPath == null) {
            try {
                val distro = file.getWSLDistribution()
                val wslPath = file.getWSLPath()
                while (!mutex.tryLock()) run {
                    Thread.sleep(5)
                }
                processWriter.write("read-symlink;${wslPath}\n")
                processWriter.flush()
                val link = processReader.readLine()
                mutex.unlock()
                file.cachedWSLCanonicalPath = file.path.split("/").subList(0, 4).joinToString("/") + link
            } catch (e: IOException) {
                //
            }
        }

        return file.cachedWSLCanonicalPath
    }

    override fun resolveSymLink(file: VirtualFile): String? {
        if (file.isFromWSL()) {
            return getWSLCanonicalPath(file);
        }
        return super.resolveSymLink(file)
    }
    companion object {
        const val PROTOCOL = "\\wsl.localhost"

        fun getInstance() = service<VirtualFileManager>().getFileSystem(PROTOCOL) as WslVirtualFileSystem
    }

    override fun list(vfile: VirtualFile): Array<String> {
        val file = getRealVirtualFile(vfile)
        if (file.isFromWSL() && this.isWslSymlink(file)) {
            val f = this.resolveSymLink(file)?.let { this.findFileByPath(it) }
            return f?.let { super.list(it) } ?: emptyArray()
        }
        return super.list(file)
    }
}

val weakMap = WeakHashMap<VirtualFile, String?>()
val weakMapSymlink = WeakHashMap<VirtualFile, Boolean?>()

private var VirtualFile.cachedWSLCanonicalPath: String?
    get() {
        return weakMap[this]
    }
    set(value) {
        weakMap[this] = value
    }

private var VirtualFile.isSymlink: Boolean?
    get() {
        return weakMapSymlink[this]
    }
    set(value) {
        weakMapSymlink[this] = value
    }


private fun VirtualFile.isFromWSL(): Boolean {
    return this.path.startsWith("//wsl$/") || path.startsWith("//wsl.localhost");
}

private fun VirtualFile.getWSLDistribution(): String? {
    if (!isFromWSL()) {
        return null;
    }

    return path.replace("""^//wsl\$/""".toRegex(), "")
            .replace("""^//wsl.localhost/""".toRegex(), "").split("/")[0]

}

private fun VirtualFile.getWSLPath(): String {
    return path.replace("""^//wsl\$/[^/]+""".toRegex(), "")
            .replace("""^//wsl.localhost/[^/]+""".toRegex(), "")
}

public val VirtualFileUrl.getVirtualFile: VirtualFile?
    get() {
        if (url.startsWith(WslVirtualFileSystem.PROTOCOL)) {
            val protocolSepIndex = url.indexOf(URLUtil.SCHEME_SEPARATOR)
            val fileSystem: VirtualFileSystem? = if (protocolSepIndex < 0) null else WslVirtualFileSystem.getInstance()
            if (fileSystem == null) return null
            val path = url.substring(protocolSepIndex + URLUtil.SCHEME_SEPARATOR.length)
            return fileSystem.findFileByPath(path)
        }
        return null
    }
