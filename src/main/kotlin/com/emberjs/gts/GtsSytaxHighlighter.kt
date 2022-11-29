package com.emberjs.gts

import com.intellij.lang.ASTNode
import com.intellij.lang.Language
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.FlexAdapter
import com.intellij.lexer.Lexer
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import javax.swing.Icon


object GtsLanguage : Language() {
    val INSTANCE: GtsLanguage = GtsLanguage()
}

internal object SimpleIcons {
    val icon: Icon = IconLoader.getIcon("/icons/jar-gray.png", SimpleIcons::class.java)
        @Nullable get() {
            return SimpleIcons.field
        }
}

class GtsLexerAdapter : FlexAdapter(GtsLexer(null)) {

}

class GtsFileType : LanguageFileType(GtsLanguage.INSTANCE) {

    companion object {
        val INSTANCE = GtsFileType()
    }

    override fun getName(): String {
        return "Gts"
    }

    override fun getDescription(): String {
        return "Gts file type"
    }

    override fun getDefaultExtension(): String {
        return "gts"
    }

    override fun getIcon(): Icon? {
        return null
    }
}


class GtsParserDefinition: ParserDefinition {
    override fun createLexer(project: Project?): Lexer {
        return GtsLexerAdapter()
    }

    override fun createParser(project: Project?): PsiParser {
        TODO("Not yet implemented")
    }

    override fun getFileNodeType(): IFileElementType {
        TODO("Not yet implemented")
    }

    override fun getCommentTokens(): TokenSet {
        return TokenSet.create()
    }

    override fun getStringLiteralElements(): TokenSet {
        return TokenSet.create()
    }

    override fun createElement(node: ASTNode?): PsiElement {
        TODO("Not yet implemented")
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile {
        TODO("Not yet implemented")
    }

}

class GtsSytaxHighlighter : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter {
        TODO("Not yet implemented")
    }
}