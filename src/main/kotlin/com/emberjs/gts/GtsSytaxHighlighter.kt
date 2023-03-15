package com.emberjs.gts

import com.dmarcotte.handlebars.HbHighlighter
import com.dmarcotte.handlebars.HbLanguage
import com.dmarcotte.handlebars.parsing.HbElementType
import com.dmarcotte.handlebars.parsing.HbLexer
import com.dmarcotte.handlebars.parsing.HbTokenTypes
import com.intellij.lang.Language
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.javascript.DialectOptionHolder
import com.intellij.lang.javascript.JSElementType
import com.intellij.lang.javascript.JSTokenTypes
import com.intellij.lang.javascript.JavaScriptHighlightingLexer
import com.intellij.lang.javascript.dialects.TypeScriptLanguageDialect
import com.intellij.lang.javascript.dialects.TypeScriptParserDefinition
import com.intellij.lang.javascript.highlighting.JSHighlighter
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.lang.javascript.psi.impl.JSFileImpl
import com.intellij.lexer.Lexer
import com.intellij.lexer.LookAheadLexer
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.ex.util.LayerDescriptor
import com.intellij.openapi.editor.ex.util.LayeredLexerEditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.fileTypes.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.psi.templateLanguages.TemplateLanguage
import com.intellij.psi.tree.*
import javax.swing.Icon


class GtsLanguage : Language("Gts"), TemplateLanguage {
    companion object {
        val INSTANCE = GtsLanguage()
    }
}

class GtsFile(viewProvider: FileViewProvider?) : JSFileImpl(viewProvider!!, GtsLanguage.INSTANCE) {
    override fun getFileType(): FileType {
        return GtsFileType.INSTANCE
    }

    override fun toString(): String {
        return "GTS File"
    }
}

class GtsTokenTypes : JSTokenTypes {
    companion object {

        val HBS_BLOCK_CONTENT: IElementType = JSElementType("HBS_BLOCK_CONTENT")
        val OUTER_ELEMENT_TYPE = OuterLanguageElementType("OUTER_BLOCK", TypeScriptLanguageDialect())
    }
}

class GtsElementTypes {
    companion object {
        val HBS_BLOCK: IElementType = JSElementType("HBS_BLOCK")
        val GTS_FILE_NODE_TYPE = IStubFileElementType<PsiFileStub<PsiFile>>("GTS", GtsLanguage.INSTANCE)
    }
}

class GtsParserDefinition : TypeScriptParserDefinition() {
    override fun getFileNodeType(): IFileElementType {
        return GtsElementTypes.GTS_FILE_NODE_TYPE
    }

    override fun createLexer(project: Project?): Lexer {
        return GtsLexerAdapter()
    }

    override fun createFile(viewProvider: FileViewProvider): JSFile {
        val mdxFile = GtsFile(viewProvider)
        return mdxFile
    }

}


internal object SimpleIcons {
    val icon: Icon = IconLoader.getIcon("/icons/jar-gray.png", SimpleIcons::class.java)
}

class HbsParseableElementType() {
    companion object {
        val INSTANCE = JSElementType("HB_TOKEN")
    }
}

class HbLexerAdapter(val baseLexer: Lexer = JavaScriptHighlightingLexer(DialectOptionHolder.TSX)) : LookAheadLexer(baseLexer) {
    val hbLexer = HbLexer()
    private val outerElementType = IElementType("JS_CONTENT", HbLanguage.INSTANCE)
    override fun lookAhead(baseLexer: Lexer) {
        if (baseLexer.tokenType == JSTokenTypes.XML_START_TAG_START) {
            // Parse all sub tokens
            var counter = 1
            var isEnd = false
            val start = baseLexer.tokenStart
            while (baseLexer.tokenType != null) {
                baseLexer.advance()
                if (baseLexer.tokenType == JSTokenTypes.XML_START_TAG_START) {
                    counter++
                }
                if (baseLexer.tokenType == JSTokenTypes.XML_END_TAG_START) {
                    isEnd = true
                }
                if (baseLexer.tokenType == JSTokenTypes.XML_TAG_NAME) {
                    if (isEnd && baseLexer.tokenText == "template") {
                        break
                    }
                    isEnd = false
                }
            }
            while (baseLexer.tokenType != JSTokenTypes.XML_TAG_END) {
                baseLexer.advance()
            }
            val end = baseLexer.tokenEnd
            hbLexer.start(baseLexer.bufferSequence, start, end)
            while (hbLexer.tokenType != null) {
                addToken(hbLexer.tokenEnd, hbLexer.tokenType)
                hbLexer.advance()
            }
            baseLexer.advance()
        } else {
            addToken(outerElementType)
            baseLexer.advance()
        }
    }
}

class GtsLexerAdapter(val baseLexer: Lexer = JavaScriptHighlightingLexer(DialectOptionHolder.TSX)) : LookAheadLexer(baseLexer) {
    val hbLexer = HbLexer()
    override fun lookAhead(baseLexer: Lexer) {
        if (baseLexer.tokenType == JSTokenTypes.XML_START_TAG_START) {
            // Parse all sub tokens
            var counter = 1
            var isEnd = false
            val start = baseLexer.tokenStart
            while (baseLexer.tokenType != null) {
                baseLexer.advance()
                if (baseLexer.tokenType == JSTokenTypes.XML_START_TAG_START) {
                    counter++
                }
                if (baseLexer.tokenType == JSTokenTypes.XML_END_TAG_START) {
                    isEnd = true
                }
                if (baseLexer.tokenType == JSTokenTypes.XML_TAG_NAME) {
                    if (isEnd && baseLexer.tokenText == "template") {
                        break
                    }
                    isEnd = false
                }
            }
            while (baseLexer.tokenType != JSTokenTypes.XML_TAG_END) {
                baseLexer.advance()
            }
            val end = baseLexer.tokenEnd
//            hbLexer.start(baseLexer.bufferSequence, start, end)
//            while (hbLexer.tokenType != null) {
//                addToken(hbLexer.tokenEnd, hbLexer.tokenType)
//                hbLexer.advance()
//            }
            baseLexer.advance()
            addToken(HbsParseableElementType.INSTANCE)
//            addToken(HbsParseableElementType.INSTANCE)
        } else {
            addToken(baseLexer.tokenType)
            baseLexer.advance()
        }
    }
}

class GtsFileType : LanguageFileType(GtsLanguage.INSTANCE) {

    companion object {
        val INSTANCE = GtsFileType()
    }

    override fun getName(): String {
        return "Gts File"
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

class GtsFileViewProvider(manager: PsiManager, virtualFile: VirtualFile, eventSystemEnabled: Boolean) : MultiplePsiFilesPerDocumentFileViewProvider(manager, virtualFile, eventSystemEnabled) {
    override fun getBaseLanguage(): Language {
        return GtsLanguage.INSTANCE
    }

    override fun getLanguages(): MutableSet<Language> {
        return mutableSetOf(GtsLanguage.INSTANCE, HbLanguage.INSTANCE, HTMLLanguage.INSTANCE)
    }

    override fun cloneInner(fileCopy: VirtualFile): MultiplePsiFilesPerDocumentFileViewProvider {
        return GtsFileViewProvider(this.manager, virtualFile, false);
    }

}


class GtsHighlighterProvider: EditorHighlighterProvider {
    override fun getEditorHighlighter(project: Project?, fileType: FileType, virtualFile: VirtualFile?, colors: EditorColorsScheme): EditorHighlighter {
        return GtsHighlighter(project, virtualFile, colors)
    }

}

class GtsHighlighter(val project: Project?, val virtualFile: VirtualFile?, val colors: EditorColorsScheme)
    : LayeredLexerEditorHighlighter(GtsSyntaxHighlighter(), colors) {

        init {
            val htmlLang = Language.findLanguageByID("HTML")!!
            val htmlSyntax = SyntaxHighlighterFactory.getSyntaxHighlighter(htmlLang, project, virtualFile)
            this.registerLayer(HbTokenTypes.STATEMENTS, LayerDescriptor(HbHighlighter(), ""))
            this.registerLayer(HbTokenTypes.CONTENT, LayerDescriptor(htmlSyntax, ""))
        }
}


class GtsSyntaxHighlighter: JSHighlighter(DialectOptionHolder.TS, false) {
    override fun getHighlightingLexer(): Lexer {
        return GtsLexerAdapter()
    }
}


class GtsSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter {
        return GtsSyntaxHighlighter()
    }
}