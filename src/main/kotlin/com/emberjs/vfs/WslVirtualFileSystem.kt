package com.emberjs.vfs

import ai.grazie.utils.WeakHashMap
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileAttributes
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl
import com.intellij.openapi.vfs.newvfs.impl.FakeVirtualFile
import com.intellij.openapi.vfs.newvfs.impl.StubVirtualFile
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.io.URLUtil
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.stream.Collectors


class WslVirtualFileSystem: LocalFileSystemImpl() {


    override fun getProtocol(): String {
        return "file"
    }

    fun isWslSymlink(file: VirtualFile): Boolean {
        if (file.isFromWSL() && file.parent != null) {
            try {
                val parentPath: String = file.parent.path.replace("^//wsl\\$/[^/]+".toRegex(), "").replace("""^//wsl.localhost/[^/]+""".toRegex(), "")
                val process = Runtime.getRuntime().exec(
                        "wsl --cd \"/$parentPath\" -- test -L ${file.name} && echo \"true\" || echo \"false\"")
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val isSymLink = reader.lines().collect(Collectors.joining("\n"))
                return isSymLink.equals("true")
            } catch (e: IOException) {
                return false
            }
        }
        return false
    }
    override fun getAttributes(file: VirtualFile): FileAttributes? {
        var attributes = super.getAttributes(file)
        if (attributes != null && attributes.type == null && isWslSymlink(file)) {
            attributes = FileAttributes(false, false, true, attributes.isHidden, attributes.length, attributes.lastModified, attributes.isWritable, FileAttributes.CaseSensitivity.SENSITIVE)
        }
        return attributes
    }

    override fun resolveSymLink(file: VirtualFile): String? {
        if (file.isFromWSL()) {
            return file.getWSLCanonicalPath();
        }
        return super.resolveSymLink(file)
    }
    companion object {
        const val PROTOCOL = "\\wsl.localhost"

        fun getInstance() = service<VirtualFileManager>().getFileSystem(PROTOCOL) as WslVirtualFileSystem
    }
}

val weakMap = WeakHashMap<VirtualFile, String?>()

private var VirtualFile.cachedWSLCanonicalPath: String?
    get() {
        return weakMap[this]
    }
    set(value) {
        weakMap[this] = value
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

private fun VirtualFile.getWSLCanonicalPath(): String? {
    if (!isFromWSL()) {
        return null
    }

    if (this.cachedWSLCanonicalPath == null) {
        try {
            val distro = this.getWSLDistribution()
            val wslPath = this.getWSLPath()
            val process = Runtime.getRuntime().exec("wsl -d $distro -- readlink -f '/${wslPath}'")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val link = reader.lines().collect(Collectors.joining("\n"))
            this.cachedWSLCanonicalPath = this.path.split("/").subList(0, 4).joinToString("/") + link
        } catch (e: IOException) {
            //
        }
    }

    return this.cachedWSLCanonicalPath
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
