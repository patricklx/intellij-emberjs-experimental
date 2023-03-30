package com.emberjs.gts

import com.dmarcotte.handlebars.HbHighlighter
import com.dmarcotte.handlebars.HbLanguage
import com.dmarcotte.handlebars.parsing.HbLexer
import com.dmarcotte.handlebars.parsing.HbParseDefinition
import com.dmarcotte.handlebars.parsing.HbTokenTypes
import com.intellij.embedding.EmbeddingElementType
import com.intellij.lang.*
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.html.HTMLParserDefinition
import com.intellij.lang.javascript.*
import com.intellij.lang.javascript.dialects.TypeScriptParserDefinition
import com.intellij.lang.javascript.highlighting.JSHighlighter
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.lang.javascript.psi.impl.JSFileImpl
import com.intellij.lang.javascript.types.JEEmbeddedBlockElementType
import com.intellij.lang.javascript.types.TypeScriptEmbeddedContentElementType
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
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.templateLanguages.OuterLanguageElementImpl
import com.intellij.psi.templateLanguages.TemplateDataElementType
import com.intellij.psi.templateLanguages.TemplateDataModifications
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider
import com.intellij.psi.tree.*
import com.intellij.psi.xml.XmlTokenType
import javax.swing.Icon

val TS: JSLanguageDialect = JavaScriptSupportLoader.TYPESCRIPT

class GtsLanguage : Language(TS,"Gts") {

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

class TSTemplate(val ts: TypeScriptEmbeddedContentElementType = TypeScriptEmbeddedContentElementType(TS, "GTS_")):
        EmbeddingElementType by ts,
        ICustomParsingType by ts,
        ILazyParseableElementTypeBase by ts,
        ILightLazyParseableElementType by ts,
        JEEmbeddedBlockElementType by ts,
        TemplateDataElementType.TemplateAwareElementType by ts,
        TemplateDataElementType("GTS_TS", TS, GtsElementTypes.JS_TOKEN, GtsElementTypes.GTS_OUTER_ELEMENT_TYPE) {

            override fun parseContents(node: ASTNode): ASTNode? {
                return super.parseContents(node)
            }
            override fun getTemplateFileLanguage(viewProvider: TemplateLanguageFileViewProvider?): Language {
                return TS
            }
            override fun appendCurrentTemplateToken(tokenEndOffset: Int, tokenText: CharSequence): TemplateDataModifications {
                val r = Regex("=\\s*$")
                return if (r.containsMatchIn(tokenText)) {
                    TemplateDataModifications.fromRangeToRemove(tokenEndOffset, "\"\"")
                } else {
                    super.appendCurrentTemplateToken(tokenEndOffset, tokenText)
                }
            }
}

class GtsElementTypes {
    companion object {
        val HB_TOKEN = JSElementType("HB_TOKEN")
        val JS_TOKEN = JSElementType("JS_TOKEN")
        val HTML_TOKEN = JSElementType("HTML_TOKEN")
        val GTS_OUTER_ELEMENT_TYPE = IElementType("GTS_EMBEDDED_CONTENT", GtsLanguage.INSTANCE)
        val HBS_BLOCK: IElementType = JSElementType("HBS_BLOCK")
        val TS_CONTENT_ELEMENT_TYPE = TSTemplate()
//        val TS_CONTENT_ELEMENT_TYPE = object: TemplateDataElementType("GTS_TS", TS, JS_TOKEN, GTS_OUTER_ELEMENT_TYPE) {
//            override fun getTemplateFileLanguage(viewProvider: TemplateLanguageFileViewProvider?): Language {
//                return TS
//            }
//            override fun appendCurrentTemplateToken(tokenEndOffset: Int, tokenText: CharSequence): TemplateDataModifications {
//                val r = Regex("=\\s*$")
//                return if (r.containsMatchIn(tokenText)) {
//                    TemplateDataModifications.fromRangeToRemove(tokenEndOffset, "\"\"")
//                } else {
//                    super.appendCurrentTemplateToken(tokenEndOffset, tokenText)
//                }
//            }
//        }
        val HTML_CONTENT_ELEMENT_TYPE = object: TemplateDataElementType("GTS_HTML", GtsLanguage.INSTANCE, HTML_TOKEN, GTS_OUTER_ELEMENT_TYPE) {

            override fun createBaseLexer(viewProvider: TemplateLanguageFileViewProvider?): Lexer {
                return GtsLexerAdapter()
            }
            override fun getTemplateFileLanguage(viewProvider: TemplateLanguageFileViewProvider?): Language {
                return HTMLLanguage.INSTANCE
            }
            override fun appendCurrentTemplateToken(tokenEndOffset: Int, tokenText: CharSequence): TemplateDataModifications {
                return if (StringUtil.endsWithChar(tokenText, '=')) {
                    TemplateDataModifications.fromRangeToRemove(tokenEndOffset, "\"\"")
                } else {
                    super.appendCurrentTemplateToken(tokenEndOffset, tokenText)
                }
            }
        }
        val HB_CONTENT_ELEMENT_TYPE = object: TemplateDataElementType("GTS_HB", GtsLanguage.INSTANCE, HB_TOKEN, GTS_OUTER_ELEMENT_TYPE) {

            override fun createBaseLexer(viewProvider: TemplateLanguageFileViewProvider?): Lexer {
                return GtsLexerAdapter()
            }
            override fun getTemplateFileLanguage(viewProvider: TemplateLanguageFileViewProvider?): Language {
                return HbLanguage.INSTANCE
            }
            override fun appendCurrentTemplateToken(tokenEndOffset: Int, tokenText: CharSequence): TemplateDataModifications {
                return if (StringUtil.endsWithChar(tokenText, '=')) {
                    TemplateDataModifications.fromRangeToRemove(tokenEndOffset, "\"\"")
                } else {
                    super.appendCurrentTemplateToken(tokenEndOffset, tokenText)
                }
            }
        }
        val GTS_FILE_NODE_TYPE = object : IFileElementType("GTS", GtsLanguage.INSTANCE) {
            override fun equals(other: Any?): Boolean {
                if (other == TypeScriptFileType.INSTANCE) {
                    return true
                }
                return super.equals(other)
            }

            override fun hashCode(): Int {
                return TypeScriptFileType.INSTANCE.hashCode()
            }

            override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode {
                val project = psi.project;
                val languageForParser = getLanguageForParser(psi)
                val builder = PsiBuilderFactory.getInstance().createBuilder(project, chameleon, null, languageForParser, chameleon.chars)
                val parser = GtsParserDefinition().createParser(project)
                val node = parser.parse(this, builder)
                return node.firstChildNode
            }
        }
    }
}

class GtsParserDefinition : TypeScriptParserDefinition() {

    override fun getFileNodeType(): IFileElementType {
        return GtsElementTypes.GTS_FILE_NODE_TYPE
    }

    override fun createLexer(project: Project?): Lexer {
        return GtsLexerAdapter(hideMode = true)
    }

    override fun createParser(project: Project?): PsiParser {
        return object : PsiParser {
            override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
                val rootMarker = builder.mark()
                while (builder.tokenType != null) {
                    val type = builder.tokenType!!
                    val marker = builder.mark()
                    builder.advanceLexer()
                    marker.done(type)
                }
                rootMarker.done(root)
                return builder.treeBuilt
            }
        }
    }

    override fun createFile(viewProvider: FileViewProvider): JSFile {
        return GtsFile(viewProvider)
    }

}


internal object GtsIcons {
    val icon: Icon = IconLoader.getIcon("/com/emberjs/icons/glimmer.svg", GtsIcons::class.java)
}

class GtsLexerAdapter(val baseLexer: Lexer = HtmlLexer(), val hideMode: Boolean =false) : LookAheadLexer(baseLexer) {
    val hbLexer = HbLexer()
    override fun lookAhead(baseLexer: Lexer) {
        if (baseLexer.tokenType == XmlTokenType.XML_START_TAG_START && baseLexer.bufferSequence.substring(baseLexer.currentPosition.offset, baseLexer.bufferEnd).startsWith("<template")) {
            baseLexer.advance()
            // Parse all sub tokens
            var isEnd = false
            val start = baseLexer.tokenStart
            while (baseLexer.tokenType != null) {
                baseLexer.advance()
                if (baseLexer.tokenType == XmlTokenType.XML_END_TAG_START) {
                    isEnd = true
                }
                if (baseLexer.tokenType == XmlTokenType.XML_NAME) {
                    if (isEnd && baseLexer.tokenText == "template") {
                        break
                    }
                    isEnd = false
                }
            }
            while (baseLexer.tokenType != XmlTokenType.XML_TAG_END && baseLexer.tokenType != null) {
                baseLexer.advance()
            }
            val end = baseLexer.tokenEnd
            if (hideMode) {
                addToken(end, JSElementTypes.OUTER_LANGUAGE_ELEMENT_EXPRESSION)
                baseLexer.advance()
                return
            }
            var lastHbContent = 0
            hbLexer.start(baseLexer.bufferSequence, start, end)
            while (hbLexer.tokenType != null) {
                if (hbLexer.tokenType != HbTokenTypes.CONTENT) {
                    lastHbContent = hbLexer.tokenEnd
                } else {
                    if (lastHbContent > 0) {
                        addToken(lastHbContent, GtsElementTypes.HB_TOKEN)
                        lastHbContent = 0
                    }
                    addToken(hbLexer.tokenEnd, GtsElementTypes.HTML_TOKEN)
                }
                hbLexer.advance()
            }
            baseLexer.advance()
        } else {
            if (baseLexer.tokenType == null) {
                addToken(null)
                return
            }
            var end = baseLexer.tokenEnd
            while ((baseLexer.tokenType != XmlTokenType.XML_START_TAG_START || !baseLexer.bufferSequence.substring(baseLexer.currentPosition.offset, baseLexer.bufferEnd).startsWith("<template")) && baseLexer.tokenType != null) {
                end = baseLexer.tokenEnd
                baseLexer.advance()
            }
            addToken(end, GtsElementTypes.JS_TOKEN)
        }
    }
}

class GtsFileType : LanguageFileType(GtsLanguage.INSTANCE) {

    override fun hashCode(): Int {
        return super.hashCode()
        return TypeScriptFileType.INSTANCE.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other == TypeScriptFileType.INSTANCE) {
            return true
        }
        return super.equals(other)
    }

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
        return GtsIcons.icon
    }
}

class GtsFileViewProviderFactory: FileViewProviderFactory {
    override fun createFileViewProvider(file: VirtualFile, language: Language?, manager: PsiManager, eventSystemEnabled: Boolean): FileViewProvider {
        return GtsFileViewProvider(manager, file, eventSystemEnabled)
    }

}

class GtsFileViewProvider(manager: PsiManager, virtualFile: VirtualFile, eventSystemEnabled: Boolean) : MultiplePsiFilesPerDocumentFileViewProvider(manager, virtualFile, eventSystemEnabled), TemplateLanguageFileViewProvider {

    override fun getBaseLanguage(): Language {
        return GtsLanguage.INSTANCE
    }

    override fun getLanguages(): MutableSet<Language> {
        return mutableSetOf(GtsLanguage.INSTANCE, TS, HbLanguage.INSTANCE, HTMLLanguage.INSTANCE)
    }

    override fun getTemplateDataLanguage(): Language {
        return HTMLLanguage.INSTANCE
    }

    override fun cloneInner(virtualFile: VirtualFile): MultiplePsiFilesPerDocumentFileViewProvider {
        return GtsFileViewProvider(this.manager, virtualFile, false);
    }

    override fun createFile(lang: Language): PsiFile? {
        if (lang.id == GtsLanguage.INSTANCE.id) {
            return GtsParserDefinition().createFile(this)
        }
        if (lang.id == TS.id) {
            val f = TypeScriptParserDefinition().createFile(this)
            (f as PsiFileImpl).contentElementType = GtsElementTypes.TS_CONTENT_ELEMENT_TYPE
            return f
        }
        if (lang.id == HTMLLanguage.INSTANCE.id) {
            val f = HTMLParserDefinition().createFile(this)
            (f as PsiFileImpl).contentElementType = GtsElementTypes.HTML_CONTENT_ELEMENT_TYPE
            return f
        }
        if (lang.id == HbLanguage.INSTANCE.id) {
            val f = HbParseDefinition().createFile(this)
            (f as PsiFileImpl).contentElementType = GtsElementTypes.HB_CONTENT_ELEMENT_TYPE
            return f
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
            val tsSyntax = SyntaxHighlighterFactory.getSyntaxHighlighter(TS, project, virtualFile)
            this.registerLayer(GtsElementTypes.HB_TOKEN, LayerDescriptor(HbHighlighter(), ""))
            this.registerLayer(GtsElementTypes.HTML_TOKEN, LayerDescriptor(htmlSyntax, ""))
            this.registerLayer(GtsElementTypes.JS_TOKEN, LayerDescriptor(tsSyntax, ""))
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


class GtsAstFactory : ASTFactory() {
    override fun createLeaf(type: IElementType, text: CharSequence): LeafElement? {

        return (if (type === GtsElementTypes.GTS_OUTER_ELEMENT_TYPE) OuterLanguageElementImpl(type, text) else super.createLeaf(type, text))
    }
}
