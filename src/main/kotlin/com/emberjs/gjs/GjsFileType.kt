package com.emberjs.gjs


import com.intellij.extapi.psi.PsiFileBase
import com.intellij.lang.Language
import com.intellij.lang.PsiParser
import com.intellij.lang.javascript.*
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import org.jetbrains.annotations.NotNull
import javax.swing.Icon

class GjsFile(viewProvider: @NotNull FileViewProvider) : PsiFileBase(viewProvider, GjsLanguage.INSTANCE) {
    override fun getFileType(): FileType {
        return GjsFileType.INSTANCE
    }

    override fun toString(): String {
        return "GJS File"
    }
}


class GjsLanguageParserDefinition: JavascriptParserDefinition() {

    override fun getFileNodeType(): IFileElementType {
        return FILE
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile {
        return GjsFile(viewProvider)
    }

    override fun createParser(project: Project?): PsiParser {
        return super.createParser(project)
    }

    companion object {
        val FILE = IFileElementType(GjsLanguage.INSTANCE);
    }
}


class GjsLanguage : JSLanguageDialect("GjsLanguage", DialectOptionHolder.JS_WITHOUT_JSX, null as Language?, "text/javascript", "application/javascript", "application/x-javascript", "jscript", "javascript", "javascript1.2", "text/ecmascript", "application/ecmascript", "text/paperscript") {

    companion object {
        val INSTANCE = GjsLanguage()
    }
}


class GjsFileType : LanguageFileType(GjsLanguage.INSTANCE) {
    override fun getName(): String {
        return "gjs"
    }

    override fun getDescription(): String {
        return "GJS language file"
    }

    override fun getDefaultExtension(): String {
        return "gjs"
    }

    override fun getIcon(): Icon? {
        return JavaScriptFileType.INSTANCE.icon
    }

    companion object {
        val INSTANCE = GjsFileType()
    }
}