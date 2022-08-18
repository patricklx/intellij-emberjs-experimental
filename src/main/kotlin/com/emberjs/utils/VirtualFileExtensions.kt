package com.emberjs.utils

import com.google.gson.stream.JsonReader
import com.intellij.javascript.nodejs.PackageJsonData
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.text.CharSequenceReader
import java.io.IOException
import com.dmarcotte.handlebars.parsing.HbParseDefinition
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
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
val emberCache = HashMap<String, Boolean>()

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
                if (key == "ember-addon") {
                    addonCache[this.path] = true
                    return true
                }
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

val VirtualFile.isInRepoAddon: Boolean
    get() = findFileByRelativePath("package.json") != null &&
            parent?.name == "lib" &&
            parent?.parent?.isEmberFolder == true

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

fun findMainPackageJsonFile(file: VirtualFile) = file.parents.asSequence()
        .filter { it.isEmberFolder }
        .map { it.findChild("package.json") }
        .firstOrNull { it != null }

fun findMainPackageJson(file: VirtualFile) = findMainPackageJsonFile(file)?.let { PackageJsonData.parse(it, null) }

fun findMainProjectName(file: VirtualFile):String? {
    val json: PackageJsonData? = findMainPackageJson(file)
    return json?.name
}

fun HbParseDefinition.createElement(node: AstNode): PsiElement? {
    return null
}
