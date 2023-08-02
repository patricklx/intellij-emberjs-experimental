package com.emberjs.utils

import com.dmarcotte.handlebars.parsing.HbParseDefinition
import com.google.gson.stream.JsonReader
import com.intellij.javascript.nodejs.PackageJsonData
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.util.text.CharSequenceReader
import org.mozilla.javascript.ast.AstNode

val VirtualFile.parents: Iterable<VirtualFile>
    get() = object : Iterable<VirtualFile> {
        override fun iterator(): Iterator<VirtualFile> {
            var file = this@parents

            return object : Iterator<VirtualFile> {
                override fun hasNext() = file.parent != null
                override fun next(): VirtualFile {
                    file = file.parent
                    return file
                }
            }
        }
    }


val addonCache = HashMap<String, Boolean>()
val inRepoCache = HashMap<String, List<String>>()
val emberCache = HashMap<String, Boolean>()

val VirtualFile.inRepoAddonPaths: List<String>
    get() {
        val out = mutableListOf<String>()
        if (inRepoCache.contains(this.path)) return inRepoCache.getOrDefault(this.path, emptyList())
        val packageJsonFile = findFileByRelativePath("package.json") ?: return out
        val text: String
        try {
            text = String(packageJsonFile.contentsToByteArray())
            val reader = JsonReader(CharSequenceReader(text))
            reader.isLenient = true
            reader.beginObject()
            while (reader.hasNext()) {
                val key = reader.nextName()
                if (key == "ember-addon") {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        val k = reader.nextName()
                        if (k == "paths") {
                            reader.beginArray()
                            while (reader.hasNext()) {
                                out.add(reader.nextString())
                            }
                            reader.endArray()
                        }
                        reader.skipValue()
                    }
                    reader.endObject()
                }
                reader.skipValue()
            }
            inRepoCache[this.path] = out
            return out
        } catch (var3: Exception) {
            return out
        }
    }

val VirtualFile.isEmberAddonFolder: Boolean
    get() {
        if (addonCache.contains(this.path)) return addonCache.getOrDefault(this.path, false)
        val packageJsonFile = findFileByRelativePath("package.json") ?: return false
        val text: String
        try {
            text = String(packageJsonFile.contentsToByteArray())
            val reader = JsonReader(CharSequenceReader(text))
            reader.isLenient = true
            reader.beginObject()
            while (reader.hasNext()) {
                val key = reader.nextName()
                if (key == "keywords") {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        if (reader.nextString() == "ember-addon") {
                            addonCache[this.path] = true
                            return true
                        }
                    }
                    reader.endArray()
                    continue
                }
                reader.skipValue()
            }
            addonCache[this.path] = false
            return false
        } catch (var3: Exception) {
            return false
        }
    }


val VirtualFile.isEmberFolder: Boolean
    get() {
        if (this.isEmberAddonFolder) return false
        if (emberCache.contains(this.path)) return emberCache.getOrDefault(this.path, false)
        val packageJsonFile = findFileByRelativePath("package.json") ?: return false
        val text: String
        try {
            text = String(packageJsonFile.contentsToByteArray())
            val reader = JsonReader(CharSequenceReader(text))
            reader.isLenient = true
            reader.beginObject()
            while (reader.hasNext()) {
                val key = reader.nextName()
                if (key == "ember") {
                    emberCache[this.path] = true
                    return true
                }
                if (key == "keywords") {
                    reader.beginArray()
                    while (reader.hasNext()) {
                        if (reader.nextString() == "ember") {
                            emberCache[this.path] = true
                            return true
                        }
                    }
                    reader.endArray()
                    continue
                }
                if (key.lowercase().contains("dependencies")) {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        if (reader.nextName() == "ember-cli") {
                            emberCache[this.path] = true
                            return true
                        }
                        reader.skipValue()
                    }
                    reader.endObject()
                    continue
                }
                reader.skipValue()
            }
            emberCache[this.path] = false
            return false
        } catch (var3: Exception) {
            return false
        }
    }


fun getByPath(directory: VirtualFile, path: String): VirtualFile? {
    val parts = path.split("/")
    var current = directory as VirtualFile?
    parts.forEach { p ->
        current = current?.children?.find { it.name == p }
    }
    return current
}

val VirtualFile.inRepoAddonDirs: List<VirtualFile>
    get() = this.isEmber.ifFalse {
       emptyList()
    } ?: inRepoAddonPaths.mapNotNull { getByPath(this, it) }

val VirtualFile.isInRepoAddon: Boolean
    get() = findFileByRelativePath("package.json") != null &&
            parentEmberModule?.inRepoAddonDirs?.any { it == this } == true


/**
 * Searches all parent paths until it finds a path containing a `package.json` file.
 */
val VirtualFile.parentModule: VirtualFile?
    get() = this.parents.find { it.findChild("package.json") != null }

/**
 * Searches all parent paths until it finds a path containing a `package.json` file and
 * then checks if the package is an Ember CLI project.
 */
val VirtualFile.parentEmberModule: VirtualFile?
    get() = this.parentModule?.let { if (it.isEmberFolder || it.isInRepoAddon || it.isEmberAddonFolder) it else null }


val VirtualFile.isEmber: Boolean
    get() = this.isEmberFolder || this.isEmberAddonFolder

val VirtualFile.emberRoot: VirtualFile?
    get() {
        return this.parents.reversed().find { it.isEmber } ?: (if (this.isEmber) this else null)
    }

fun findMainPackageJsonFile(file: VirtualFile) = file.emberRoot?.let { it.findChild("package.json") }

fun findMainPackageJson(file: VirtualFile) = findMainPackageJsonFile(file)?.let { PackageJsonData.getOrCreate(it) }

fun findMainProjectName(file: VirtualFile):String? {
    val json: PackageJsonData? = findMainPackageJson(file)
    return json?.name
}

fun HbParseDefinition.createElement(node: AstNode): PsiElement? {
    return null
}
