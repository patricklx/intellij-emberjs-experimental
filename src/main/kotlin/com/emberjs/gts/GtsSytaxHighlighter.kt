package com.emberjs.gts

import com.dmarcotte.handlebars.HbHighlighter
import com.dmarcotte.handlebars.HbLanguage
import com.dmarcotte.handlebars.parsing.HbLexer
import com.dmarcotte.handlebars.parsing.HbParseDefinition
import com.dmarcotte.handlebars.parsing.HbTokenTypes
import com.intellij.lang.Language
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.html.HTMLParserDefinition
import com.intellij.lang.javascript.DialectOptionHolder
import com.intellij.lang.javascript.JSElementType
import com.intellij.lang.javascript.JSElementTypes
import com.intellij.lang.javascript.JSTokenTypes
import com.intellij.lang.javascript.JavaScriptHighlightingLexer
import com.intellij.lang.javascript.dialects.TypeScriptLanguageDialect
import com.intellij.lang.javascript.dialects.TypeScriptParserDefinition
import com.intellij.lang.javascript.highlighting.JSHighlighter
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.lang.javascript.psi.impl.JSFileImpl
import com.intellij.lexer.HtmlLexer
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
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.psi.templateLanguages.TemplateLanguage
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.IStubFileElementType
import com.intellij.psi.tree.OuterLanguageElementType
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
        return GtsLexerAdapter(hideMode = true)
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
        val HB_TOKEN = JSElementType("HB_TOKEN")
        val HTML_TOKEN = JSElementType("HTML_TOKEN")
    }
}

class HtmlLexerAdapter(val baseLexer: Lexer = JavaScriptHighlightingLexer(DialectOptionHolder.TSX)) : LookAheadLexer(baseLexer) {
    val hbLexer = HbLexer()
    val htmlLexer = HtmlLexer()
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
            baseLexer.advance()
            val end = baseLexer.tokenEnd
            hbLexer.start(baseLexer.bufferSequence, start, end)
            htmlLexer.start(baseLexer.bufferSequence, start, end)
            while (hbLexer.tokenType != null) {
                if (hbLexer.tokenType != HbTokenTypes.CONTENT) {
                    addToken(hbLexer.tokenEnd, HbTokenTypes.OUTER_ELEMENT_TYPE)
                } else {
                    while (htmlLexer.tokenEnd < hbLexer.tokenEnd) {
                        if (htmlLexer.tokenStart >= hbLexer.tokenStart) {
                            addToken(htmlLexer.tokenEnd, htmlLexer.tokenType)
                        }
                        htmlLexer.advance()
                    }
                }
                hbLexer.advance()
            }
        } else {
            addToken(TokenType.WHITE_SPACE)
            baseLexer.advance()
        }
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
            baseLexer.advance()
            val end = baseLexer.tokenEnd
            hbLexer.start(baseLexer.bufferSequence, start, end)
            while (hbLexer.tokenType != null) {
                addToken(hbLexer.tokenEnd, hbLexer.tokenType)
                hbLexer.advance()
            }
        } else {
            addToken(outerElementType)
            baseLexer.advance()
        }
    }
}

class GtsLexerAdapter(val baseLexer: Lexer = JavaScriptHighlightingLexer(DialectOptionHolder.TSX), val hideMode: Boolean =false) : LookAheadLexer(baseLexer) {
    val hbLexer = HbLexer()
    override fun lookAhead(baseLexer: Lexer) {
        if (baseLexer.tokenType == JSTokenTypes.XML_START_TAG_START) {
            // Parse all sub tokens
            var isEnd = false
            val start = baseLexer.tokenStart
            while (baseLexer.tokenType != null) {
                baseLexer.advance()
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
            while (baseLexer.tokenType != JSTokenTypes.XML_TAG_END && baseLexer.tokenType != null) {
                baseLexer.advance()
            }
            val end = baseLexer.tokenEnd
            if (hideMode) {
                addToken(end, JSElementTypes.OUTER_LANGUAGE_ELEMENT_EXPRESSION)
                return
            }
            var lastHbContent = 0
            hbLexer.start(baseLexer.bufferSequence, start, end)
            while (hbLexer.tokenType != null) {
                if (hbLexer.tokenType != HbTokenTypes.CONTENT) {
                    lastHbContent = hbLexer.tokenEnd
                } else {
                    if (lastHbContent > 0) {
                        addToken(lastHbContent, HbsParseableElementType.HB_TOKEN)
                        lastHbContent = 0
                    }
                    addToken(hbLexer.tokenEnd, HbsParseableElementType.HTML_TOKEN)
                }
                hbLexer.advance()
            }
        } else {
            addToken(baseLexer.tokenType)
            baseLexer.advance()
        }
    }

    override fun getTokenType(): IElementType? {
        if (baseLexer.tokenType == null) {
            return null
        }
        return super.getTokenType()
    }
}


class GtsHtmlParserDefinition: HTMLParserDefinition() {
    override fun createLexer(project: Project?): Lexer {
        return HtmlLexerAdapter()
    }
}

class GtsHbParserDefinition: HbParseDefinition() {
    override fun createLexer(project: Project?): Lexer {
        return HbLexerAdapter()
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

class GtsFileViewProviderFactory: FileViewProviderFactory {
    override fun createFileViewProvider(file: VirtualFile, language: Language?, manager: PsiManager, eventSystemEnabled: Boolean): FileViewProvider {
        return GtsFileViewProvider(manager, file, eventSystemEnabled)
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

    override fun createFile(lang: Language): PsiFile? {
        if (lang.id == GtsLanguage.INSTANCE.id) {
            return GtsParserDefinition().createFile(this)
        }
        if (lang.id == HTMLLanguage.INSTANCE.id) {
            return GtsHtmlParserDefinition().createFile(this)
        }
        if (lang.id == HbLanguage.INSTANCE.id) {
            return GtsHbParserDefinition().createFile(this)
        }
        return null
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
            this.registerLayer(HbsParseableElementType.HB_TOKEN, LayerDescriptor(HbHighlighter(), ""))
            this.registerLayer(HbsParseableElementType.HTML_TOKEN, LayerDescriptor(htmlSyntax, ""))
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