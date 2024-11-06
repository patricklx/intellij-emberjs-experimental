package com.emberjs.gts

import com.dmarcotte.handlebars.HbHighlighter
import com.dmarcotte.handlebars.HbLanguage
import com.dmarcotte.handlebars.parsing.HbLexer
import com.dmarcotte.handlebars.parsing.HbParseDefinition
import com.dmarcotte.handlebars.parsing.HbTokenTypes
import com.emberjs.hbs.EmberReference
import com.emberjs.icons.EmberIcons
import com.emberjs.index.EmberNameIndex
import com.emberjs.resolver.EmberName
import com.emberjs.utils.EmberUtils
import com.emberjs.utils.ifTrue
import com.intellij.formatting.*
import com.intellij.formatting.templateLanguages.DataLanguageBlockWrapper
import com.intellij.lang.*
import com.intellij.lang.ecmascript6.psi.ES6ImportExportDeclaration
import com.intellij.lang.ecmascript6.psi.impl.ES6CreateImportUtil
import com.intellij.lang.ecmascript6.psi.impl.ES6ImportPsiUtil
import com.intellij.lang.html.HTMLLanguage
import com.intellij.lang.html.HTMLParserDefinition
import com.intellij.lang.javascript.*
import com.intellij.lang.javascript.config.JSImportResolveContext
import com.intellij.lang.javascript.dialects.ECMA6ParserDefinition
import com.intellij.lang.javascript.dialects.TypeScriptParserDefinition
import com.intellij.lang.javascript.formatter.JavascriptFormattingModelBuilder
import com.intellij.lang.javascript.highlighting.JSHighlighter
import com.intellij.lang.javascript.index.IndexedFileTypeProvider
import com.intellij.lang.javascript.modules.JSImportCandidateDescriptor
import com.intellij.lang.javascript.modules.JSImportPlaceInfo
import com.intellij.lang.javascript.modules.imports.JSImportCandidatesBase
import com.intellij.lang.javascript.modules.imports.JSImportDescriptor
import com.intellij.lang.javascript.modules.imports.JSModuleDescriptor
import com.intellij.lang.javascript.modules.imports.JSSimpleImportCandidate
import com.intellij.lang.javascript.modules.imports.providers.JSCandidatesProcessor
import com.intellij.lang.javascript.modules.imports.providers.JSImportCandidatesProvider
import com.intellij.lang.javascript.psi.*
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.impl.JSFileImpl
import com.intellij.lang.javascript.types.JSFileElementType
import com.intellij.lang.typescript.tsconfig.*
import com.intellij.lang.xml.XMLLanguage
import com.intellij.lang.xml.XmlFormattingModel
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
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.DocumentBasedFormattingModel
import com.intellij.psi.formatter.FormattingDocumentModelImpl
import com.intellij.psi.formatter.common.AbstractBlock
import com.intellij.psi.formatter.xml.AnotherLanguageBlockWrapper
import com.intellij.psi.formatter.xml.HtmlPolicy
import com.intellij.psi.formatter.xml.XmlFormattingPolicy
import com.intellij.psi.formatter.xml.XmlTagBlock
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.impl.source.html.HtmlDocumentImpl
import com.intellij.psi.impl.source.html.HtmlFileImpl
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.impl.source.tree.PsiWhiteSpaceImpl
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.templateLanguages.*
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlTokenType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.Processor
import com.intellij.xml.template.formatter.AbstractXmlTemplateFormattingModelBuilder
import com.intellij.xml.template.formatter.TemplateFormatUtil
import java.util.function.Predicate
import javax.swing.Icon

val TS: JSLanguageDialect = JavaScriptSupportLoader.TYPESCRIPT
val JS: JSLanguageDialect = JavaScriptSupportLoader.ECMA_SCRIPT_6



open class GtsLanguage(val lang: JSLanguageDialect = TS, id: String ="Gts") : Language(lang, id) {
    public var fileElementType: JSFileElementType? = null
    override fun <T : Any?> getUserData(key: Key<T>): T? {
        if (key.toString() == "js.file.element.type") {
            return fileElementType as T?
        }
        return super.getUserData(key)
    }

    override fun <T : Any?> putUserDataIfAbsent(key: Key<T>, value: T & Any): T & Any {
        if (key.toString() == "js.file.element.type") {
            return value
        }
        return super.putUserDataIfAbsent(key, value)
    }

    companion object {
        val INSTANCE = GtsLanguage()
    }
}

class GjsLanguage(): GtsLanguage(JS, "Gjs") {
    companion object {
        val INSTANCE = GjsLanguage()
    }
}


class GtsFile(viewProvider: FileViewProvider?, val isJS: Boolean =false)
    : JSFileImpl(viewProvider!!, isJS.ifTrue { GjsLanguage.INSTANCE } ?: GtsLanguage.INSTANCE) {
    override fun getFileType(): FileType {
        return isJS.ifTrue { GjsFileType.INSTANCE } ?: GtsFileType.INSTANCE
    }

    override fun toString(): String {
        return "GTS File"
    }


}


class GtsFileElementType(language: Language?) : JSFileElementType(language) {

    init {
        (language as GtsLanguage).fileElementType = this
    }

    override fun parseContents(chameleon: ASTNode): ASTNode? {
        return GtsElementTypes.TS_CONTENT_ELEMENT_TYPE.parseContents(chameleon)
    }

    override fun getExternalId(): String {
        return GtsLanguage.INSTANCE.toString() + ":" + this
    }

    companion object {
        val INSTANCE = GtsFileElementType(GtsLanguage.INSTANCE)
    }
}

class GjsFileElementType(language: Language?) : JSFileElementType(language) {

    init {
        (language as GjsLanguage).fileElementType = this
    }

    override fun parseContents(chameleon: ASTNode): ASTNode? {
        return GtsElementTypes.TS_CONTENT_ELEMENT_TYPE.parseContents(chameleon)
    }

    override fun getExternalId(): String {
        return GjsLanguage.INSTANCE.toString() + ":" + this
    }

    companion object {
        val INSTANCE = GjsFileElementType(GjsLanguage.INSTANCE)
    }
}

class GtsElementTypes {
    companion object {
        val HB_TOKEN = JSElementType("HB_TOKEN")
        val JS_TOKEN = JSElementType("JS_TOKEN")
        val HTML_TOKEN = JSElementType("HTML_TOKEN")
        val GTS_OUTER_ELEMENT_TYPE = IElementType("GTS_EMBEDDED_CONTENT", GtsLanguage.INSTANCE)
        val HBS_BLOCK: IElementType = JSElementType("HBS_BLOCK")
        //val TS_CONTENT_ELEMENT_TYPE = TSTemplate()
        val TS_CONTENT_ELEMENT_TYPE = object: TemplateDataElementType("GTS_TS", TS, JS_TOKEN, GTS_OUTER_ELEMENT_TYPE) {

            override fun equals(other: Any?): Boolean {
                if (other is JSFileElementType) {
                    return true
                }
                return super.equals(other)
            }
            override fun getTemplateFileLanguage(viewProvider: TemplateLanguageFileViewProvider?): Language {
                return TS
            }


            override fun appendCurrentTemplateToken(tokenEndOffset: Int, tokenText: CharSequence): TemplateDataModifications {
                val r = Regex("(=|:)\\s*$")
                return if (r.containsMatchIn(tokenText)) {
                    TemplateDataModifications.fromRangeToRemove(tokenEndOffset, "''")
                } else {
                    super.appendCurrentTemplateToken(tokenEndOffset, tokenText)
                }
            }
        }
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
                return GtsLexerAdapter(hbMode = true)
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
                val project = psi.project
                val languageForParser = getLanguageForParser(psi)
                val builder = PsiBuilderFactory.getInstance().createBuilder(project, chameleon, null, languageForParser, chameleon.chars)
                val parser = GtsParserDefinition().createParser(project)
                val node = parser.parse(this, builder)
                return node.firstChildNode ?: PsiWhiteSpaceImpl("")
            }
        }
        val GJS_FILE_NODE_TYPE = object : IFileElementType("GJS", GjsLanguage.INSTANCE) {
            override fun equals(other: Any?): Boolean {
                if (other == JavaScriptFileType.INSTANCE) {
                    return true
                }
                return super.equals(other)
            }

            override fun hashCode(): Int {
                return JavaScriptFileType.INSTANCE.hashCode()
            }

            override fun doParseContents(chameleon: ASTNode, psi: PsiElement): ASTNode {
                val project = psi.project
                val languageForParser = getLanguageForParser(psi)
                val builder = PsiBuilderFactory.getInstance().createBuilder(project, chameleon, null, languageForParser, chameleon.chars)
                val parser = GjsParserDefinition().createParser(project)
                val node = parser.parse(this, builder)
                return node.firstChildNode ?: PsiWhiteSpaceImpl("")
            }
        }
    }
}

open class GtsParserDefinition(val isJS: Boolean = false) : TypeScriptParserDefinition() {

    override fun getFileNodeType(): JSFileElementType {
        if (isJS) {
            return GjsFileElementType.INSTANCE
        }
        return GtsFileElementType.INSTANCE
    }

    override fun createLexer(project: Project?): Lexer {
        return GtsLexerAdapter(hideMode = true)
    }

    override fun createParser(project: Project?): PsiParser {
        return PsiParser { root, builder ->
            val rootMarker = builder.mark()
            while (builder.tokenType != null) {
                val type = builder.tokenType!!
                val marker = builder.mark()
                builder.advanceLexer()
                marker.done(type)
            }
            rootMarker.done(root)
            builder.treeBuilt
        }
    }

    override fun createFile(viewProvider: FileViewProvider): JSFile {
        if (viewProvider.baseLanguage.id == GjsLanguage.INSTANCE.id) {
            return GtsFile(viewProvider, true)
        }
        return GtsFile(viewProvider, isJS)
    }
}

class GjsParserDefinition: GtsParserDefinition(true)


internal object GtsIcons {
    val icon: Icon = IconLoader.getIcon("/com/emberjs/icons/glimmer.svg", GtsIcons::class.java)
}

class GtsLexerAdapter(baseLexer: Lexer = HtmlLexer(), val hideMode: Boolean =false, val hbMode: Boolean =false) : LookAheadLexer(baseLexer) {
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
            if (hbMode) {
                addToken(end, GtsElementTypes.HB_TOKEN)
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

    override fun getIcon(): Icon {
        return GtsIcons.icon
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}

class GjsFileType : LanguageFileType(GjsLanguage.INSTANCE) {

    override fun equals(other: Any?): Boolean {
        if (other == JavaScriptFileType.INSTANCE) {
            return true
        }
        return super.equals(other)
    }

    companion object {
        val INSTANCE = GjsFileType()
    }

    override fun getName(): String {
        return "Gjs File"
    }

    override fun getDescription(): String {
        return "Gjs file type"
    }

    override fun getDefaultExtension(): String {
        return "gjs"
    }

    override fun getIcon(): Icon {
        return GtsIcons.icon
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}

class GtsFileViewProviderFactory: FileViewProviderFactory {
    override fun createFileViewProvider(file: VirtualFile, language: Language?, manager: PsiManager, eventSystemEnabled: Boolean): FileViewProvider {
        return (language as? GtsLanguage)
                ?.let { GtsFileViewProvider(manager, file, eventSystemEnabled, it) }
                ?: GtsFileViewProvider(manager, file, eventSystemEnabled)
    }

}

class GtsFileViewProvider(manager: PsiManager, virtualFile: VirtualFile, eventSystemEnabled: Boolean, public val baseLang: GtsLanguage = GtsLanguage.INSTANCE) : MultiplePsiFilesPerDocumentFileViewProvider(manager, virtualFile, eventSystemEnabled), TemplateLanguageFileViewProvider {

    override fun findElementAt(offset: Int, language: Language): PsiElement? {
        var element: PsiElement?
        if (language == baseLang) {
            this.languages.forEach {
                element = super.findElementAt(offset, it)
                if (element !is OuterLanguageElement) {
                    return element
                }
            }
        }
        element = super.findElementAt(offset, language)
        return element
    }

    override fun findElementAt(offset: Int): PsiElement? {
        this.languages.forEach {
            val element = super.findElementAt(offset, it)
            if (element !is OuterLanguageElement) {
                return element
            }
        }
        return null
    }

    override fun findReferenceAt(offset: Int): PsiReference? {
        val ref = super.findReferenceAt(offset)
        if (ref is PsiMultiReference) {
            val r = ref.references.find { it is EmberReference && it.resolve() != null }
            if (r !== null) return r
        }
        return ref
    }

    override fun getBaseLanguage(): Language {
        return baseLang
    }

    override fun getLanguages(): MutableSet<Language> {
        if (baseLang == GjsLanguage.INSTANCE) {
            return mutableSetOf(HTMLLanguage.INSTANCE, HbLanguage.INSTANCE, JS, GjsLanguage.INSTANCE)
        }
        return mutableSetOf(HTMLLanguage.INSTANCE, HbLanguage.INSTANCE, TS, GtsLanguage.INSTANCE)
    }

    override fun getTemplateDataLanguage(): Language {
        return HTMLLanguage.INSTANCE
    }

    override fun cloneInner(virtualFile: VirtualFile): MultiplePsiFilesPerDocumentFileViewProvider {
        return GtsFileViewProvider(this.manager, virtualFile, false, baseLang)
    }

    override fun createFile(lang: Language): PsiFile? {
        if (lang.id == GtsLanguage.INSTANCE.id) {
            return GtsParserDefinition().createFile(this)
        }
        if (lang.id == GjsLanguage.INSTANCE.id) {
            return GjsParserDefinition().createFile(this)
        }
        if (lang.id == TS.id) {
            val f = TypeScriptParserDefinition().createFile(this)
            (f as PsiFileImpl).contentElementType = GtsFileElementType.INSTANCE
            return f
        }
        if (lang.id == JS.id) {
            val f = ECMA6ParserDefinition().createFile(this)
            (f as PsiFileImpl).contentElementType = GjsFileElementType.INSTANCE
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

class GtsHighlighter(val project: Project?, val virtualFile: VirtualFile?, colors: EditorColorsScheme)
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

        return (if (type == GtsElementTypes.GTS_OUTER_ELEMENT_TYPE) OuterLanguageElementImpl(type, text) else super.createLeaf(type, text))
    }
}


val GTS_DEFAULT_EXTENSIONS_WITH_DOT = arrayOf(".gts", ".gjs")

class GtsImportResolver(project: Project,
                        resolveContext: JSImportResolveContext,
                        private val contextFile: VirtualFile): TypeScriptFileImportsResolverImpl(project, resolveContext, GTS_DEFAULT_EXTENSIONS_WITH_DOT, listOf(GtsFileType.INSTANCE)) {

    override fun processAllFilesInScope(includeScope: GlobalSearchScope, processor: Processor<in VirtualFile>) {
        if (includeScope == GlobalSearchScope.EMPTY_SCOPE) return

        //accept all, even without lang="ts"
        super.processAllFilesInScope(includeScope, processor)

        processGtsPackage(processor)
    }

    override fun getExtensionsWithDot(): Array<String> {
        return super.getExtensionsWithDot() + arrayOf(".gts", ".gjs")
    }

    private fun processGtsPackage(processor: Processor<in VirtualFile>) {
        TypeScriptImportsResolverProvider.getDefaultProvider(project, resolveContext)
                .resolveFileModule("gts", contextFile)
                ?.let { processor.process(it) }
    }

    override fun getPriority(): Int = TypeScriptFileImportsResolver.JS_DEFAULT_PRIORITY
}

class GtsTypeScriptImportsResolverProvider : TypeScriptImportsResolverProvider {
    override fun isImplicitTypeScriptFile(project: Project, file: VirtualFile): Boolean {
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return false
        return psiFile.viewProvider is GtsFileViewProvider
    }

    override fun getExtensions(): Array<String> = GTS_DEFAULT_EXTENSIONS_WITH_DOT

    override fun contributeResolver(project: Project, config: TypeScriptConfig): TypeScriptFileImportsResolver {
        return GtsImportResolver(project, config.resolveContext, config.configFile)
    }

    override fun contributeResolver(project: Project,
                                    context: TypeScriptImportResolveContext,
                                    contextFile: VirtualFile): TypeScriptFileImportsResolver? {
        val detectedEmber = EmberUtils.isEmber(project)
        if (detectedEmber) {
            return GtsImportResolver(project, context, contextFile)
        }
        return null
    }
}

class GtsComponentCandidatesProvider(val placeInfo: JSImportPlaceInfo) : JSImportCandidatesBase(placeInfo) {

    class Info(val type: String, val name: String, val icon: Icon, val virtualFile: VirtualFile)

    private val candidates: Map<String, List<Info>> by lazy {
        val list = FilenameIndex.getAllFilesByExt(project, "gts",
                createProjectImportsScope(placeInfo, getStructureModuleRoot(placeInfo)))
                .map { getExports(it) }
                .flatten()
                .toMutableList()

        val scopes = EmberUtils.getScopesForFile(myPlaceInfo.file)

        val scope = ProjectScope.getAllScope(project)
        val emberNames = mutableListOf<EmberName>()

        // Collect all components from the index
        EmberNameIndex.getFilteredProjectKeys(scope) { it.type == "component" }
                .toCollection(emberNames)

        // Collect all component templates from the index
        EmberNameIndex.getFilteredProjectKeys(scope) { it.isComponentTemplate }
                .filter { !emberNames.contains(it) }
                .toCollection(emberNames)

        EmberNameIndex.getFilteredProjectKeys(scope) { it.type == "helper" }
                .toCollection(emberNames)

        EmberNameIndex.getFilteredProjectKeys(scope) { it.type == "modifier" }
                .toCollection(emberNames)

        emberNames.removeIf { it.virtualFile?.let { !EmberUtils.isInScope(it, scopes) } ?: false }

        emberNames.mapNotNull { getComponentTemplateInfo(it) }.toCollection(list)

        return@lazy list.groupBy { it.name }
    }

    fun getComponentTemplateInfo(name: EmberName): Info? {
        val file = name.virtualFile ?: return null
        return Info("default", name.camelCaseName, EmberIcons.COMPONENT_16, file)
    }


    fun getExports(virtualFile: VirtualFile): List<Info> {
        val exports = mutableListOf<Info>()
        var file = PsiManager.getInstance(placeInfo.project).findFile(virtualFile)
        if (file == null) {
            return exports
        }
        file = file.viewProvider.getPsi(JavaScriptSupportLoader.TYPESCRIPT)
        val defaultExport = PsiTreeUtil.collectElements(file) { (it as? JSElementBase)?.isExportedWithDefault == true }.firstOrNull()
        if (defaultExport != null) {
            exports.add(Info("default", (defaultExport as? JSNamedElement)?.name ?: getComponentName(virtualFile), GtsIcons.icon, virtualFile))
            exports.add(Info("default", getComponentName(virtualFile), GtsIcons.icon, virtualFile))
        }

        val namedExports = PsiTreeUtil.collectElements(file) { (it as? JSElementBase)?.isExported == true}.map { it as JSElementBase }
        namedExports.forEach {
            exports.add(Info("named", it.name!!, GtsIcons.icon, virtualFile))
        }
        return exports
    }

    override fun processCandidates(ref: String,
                                   processor: JSCandidatesProcessor) {
        val place = myPlaceInfo.place
        val candidates = candidates[ref]
        candidates?.forEach { processor.processCandidate(GtsImportCandidate(ref, place, it)) }
    }

    override fun getNames(keyFilter: Predicate<in String>): Set<String> {
        return candidates.keys.filter(keyFilter::test).toSet()
    }

    private fun getComponentName(virtualFile: VirtualFile): String {
        return if (virtualFile.name == "index.gts") {
            virtualFile.parent.name
        }
        else {
            virtualFile.nameWithoutExtension
        }
    }

    class Factory : JSImportCandidatesProvider.CandidatesFactory {
        override fun createProvider(placeInfo: JSImportPlaceInfo): JSImportCandidatesProvider {
            return GtsComponentCandidatesProvider(placeInfo)
        }
    }
}

class GtsJSModuleDescriptor(val descriptor: JSModuleDescriptor): JSModuleDescriptor by descriptor {
    override fun getModuleName(): String {
        val name = descriptor.moduleName
        val toRemove = arrayOf("app", "addon")
        val parts = name.split("/").toMutableList()
        if (name.startsWith("@")) {
            if (toRemove.contains(parts.getOrNull(2))) {
                parts.removeAt(2)
            }
            return parts.joinToString("/")
        }
        if (toRemove.contains(parts.getOrNull(1))) {
            parts.removeAt(1)
        }
        return parts.joinToString("/").removeSuffix("/index")
    }
}

class GtsImportCandidate(name: String, place: PsiElement, val info: GtsComponentCandidatesProvider.Info)
    : JSSimpleImportCandidate(name, null, place) {
    override fun createDescriptor(): JSImportDescriptor? {
        val place = place?.containingFile?.viewProvider?.getPsi(TS) ?: return null
        val type = info.type.equals("named").ifTrue { ES6ImportPsiUtil.ImportExportType.SPECIFIER } ?: ES6ImportPsiUtil.ImportExportType.DEFAULT
        val desc = ES6CreateImportUtil.getImportDescriptor(name, null, info.virtualFile, place, true)
        val info = ES6ImportPsiUtil.CreateImportExportInfo(info.name, info.name, type, ES6ImportExportDeclaration.ImportExportPrefixKind.IMPORT)
        if (desc?.moduleDescriptor == null) {
            return null
        }
        return JSImportCandidateDescriptor(GtsJSModuleDescriptor(desc.moduleDescriptor), info.importedName, info.exportedName, info.importExportPrefixKind, info.importType)
    }

    override fun getContainerText(): String {
        return descriptor?.moduleName ?: ""
    }

    override fun getIcon(flags: Int): Icon {
        return info.icon
    }

    override fun equals(other: Any?): Boolean {
        return super.equals(other) && this.descriptor?.moduleName == (other as? GtsImportCandidate)?.descriptor?.moduleName
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + info.hashCode()
        return result
    }
}

class GtsIndexedFileTypeProvider : IndexedFileTypeProvider {
    override fun getFileTypesToIndex(): Array<FileType> = arrayOf(GtsFileType.INSTANCE)
}

val NoWrap = Wrap.createWrap(WrapType.NONE, false).apply { ignoreParentWraps() }


// wrapper to patch JsBlocks to include outer language block into JSAssignmentExpression and JSVarStatement
open class JsBlockWrapper(val block: Block, val parent: JsBlockWrapper?, var hbsBlock: Block? = null): Block by block {

    private var cachedBlocks: MutableList<JsBlockWrapper>? = null
    val astnode =(block as? ASTBlock)?.node

    init {
        this.subBlocks
    }

    override fun getWrap(): Wrap? {
        if (parent?.subBlocks?.lastOrNull()?.block is RootBlockWrapper) {
            return NoWrap
        }
        if (subBlocks.lastOrNull()?.block is RootBlockWrapper) {
            return NoWrap
        }
        if (parent?.block is RootBlockWrapper.SynteticBlockWrapper) {
            return NoWrap
        }
        return block.wrap
    }

    override fun getDebugName(): String {
        var b = block
        if (b is DataLanguageBlockWrapper) {
            b = b.original
        }
        return b.debugName ?: b.javaClass.simpleName
    }

    override fun getTextRange(): TextRange {
        val subBlocks = this.subBlocks
        if (subBlocks.isEmpty()) {
            return block.textRange
        }
        return TextRange(subBlocks.first().textRange.startOffset, subBlocks.last().textRange.endOffset)
    }

    private fun nexOuterLanguageBlock(block: Block): RootBlockWrapper? {
        val i = this.block.subBlocks.indexOf(block)
        if (this.block.subBlocks.getOrNull(i+1) is RootBlockWrapper) {
            return this.block.subBlocks.getOrNull(i+1) as RootBlockWrapper
        }
        return null
    }

    fun mapToWrapper(block: Block, hbsBlock: Block?): JsBlockWrapper {
        if (block is ASTBlock) {
            return JSAstBlockWrapper(block, this, hbsBlock)
        }
        return JsBlockWrapper(block, this, hbsBlock)
    }

    override fun getSubBlocks(): MutableList<JsBlockWrapper> {
        if (this.cachedBlocks != null) {
            return this.cachedBlocks!!
        }
        val blocks = block.subBlocks.map { mapToWrapper(it, hbsBlock) }.toMutableList()

        blocks.toTypedArray().forEach {
//            blocks.removeIf { it.block is RootBlockWrapper && it.block.patched }
            if (it.block is RootBlockWrapper && !it.block.patched) {
                it.block.parent = this
            }
        }

        val psiElement = this.astnode?.psi
        if (psiElement is JSVariable || (psiElement is JSExpressionStatement) && psiElement.children.find { it is JSAssignmentExpression } != null) {
            val last = PsiTreeUtil.collectElements(psiElement) { it is JSLiteralExpression}.lastOrNull()
            if (last is JSLiteralExpression && last.textLength == 0 && last.textOffset == psiElement.endOffset) {
                val outerLanguageBlock = parent?.parent?.nexOuterLanguageBlock(parent.block) ?: parent?.nexOuterLanguageBlock(this.block)
                if (outerLanguageBlock != null) {
                    outerLanguageBlock.patched = true
                    outerLanguageBlock.parent = this
                }
            }
        }
        this.cachedBlocks = blocks
        return blocks
    }
}

class JSAstBlockWrapper(block: ASTBlock, parent: JsBlockWrapper?, hbsBlock: Block?): JsBlockWrapper(block, parent, hbsBlock), ASTBlock {
    override fun getNode(): ASTNode? {
        return super.astnode
    }
}

class RootBlockWrapper(val block: DataLanguageBlockWrapper, val policy: HtmlPolicy): ASTBlock by block {

    var patched = false
    var parent: Block? = null

    override fun getDebugName(): String? {
        return block.original.javaClass.simpleName
    }

    override fun getIndent(): Indent? {
        return Indent.getNoneIndent()
    }

    override fun getWrap(): Wrap? {
        return NoWrap
    }

    class SynteticBlockWrapper(val subblock: Block, val parent: RootBlockWrapper, val index: Any, val subblocks: MutableList<Block>): Block by subblock {

        override fun getWrap(): Wrap? {
            return NoWrap
        }

        override fun getIndent(): Indent {
            val shouldIndent = subblock.javaClass.simpleName == "HandlebarsBlock" || subblock.javaClass.simpleName == "HandlebarsTagBlock"
            var indent = parent.getBaseIndent(shouldIndent)
            if (index == 0) {
                indent = parent.getBaseIndent()
            }
            if (index == subblocks.lastIndex) {
                indent = parent.getBaseIndent()
            }
            return indent!!
        }
    }

    override fun getSubBlocks(): MutableList<Block> {
        val subblocks = (block.parent == null).ifTrue { block.original.subBlocks } ?: block.subBlocks
        return subblocks.mapIndexed { index, it ->
            SynteticBlockWrapper(it, this, index, subblocks)
        }.toMutableList()
    }

    fun getBaseIndent(forChild: Boolean = false): Indent? {
        val viewProvider = this.node!!.psi.containingFile.viewProvider
        val htmlFile = viewProvider.getPsi(HTMLLanguage.INSTANCE)
        val jsFile = viewProvider.getPsi(JavaScriptSupportLoader.TYPESCRIPT) ?: viewProvider.getPsi(JavaScriptSupportLoader.ECMA_SCRIPT_6)
        val project = this.node!!.psi.project
        val document = PsiDocumentManager.getInstance(project).getDocument(htmlFile)!!
        val INDENT_SIZE = this.policy.settings.getIndentSize(htmlFile.language.associatedFileType)
        val JS_INDENT_SIZE = this.policy.settings.getIndentSize(jsFile.language.associatedFileType)
        if (this.parent != null) {
            val blockRef = this.parent as? JSAstBlockWrapper ?: ((this.parent as JsBlockWrapper).parent as JSAstBlockWrapper)

            var startOffset: Int? = null
            if (blockRef.node!!.psi is JSClass) {
                val psiRef = blockRef.node!!.psi.parent
                startOffset = psiRef.textRange?.startOffset?.let { it + JS_INDENT_SIZE }
            }

            if (blockRef.node!!.psi.parent is JSVarStatement) {
                val psiRef = blockRef.node!!.psi.parent
                startOffset = psiRef.textRange?.startOffset
                if (startOffset != null) {
                    val lineTpl = document.getLineNumber(this.textRange.startOffset)
                    val parentLine = document.getLineNumber(startOffset)
                    if (lineTpl != parentLine) {
                        startOffset += JS_INDENT_SIZE
                    }
                }
            }

            if (blockRef.node!!.psi is JSObjectLiteralExpression) {
                val psiRef = blockRef.node!!.psi.parent.parent
                startOffset = psiRef.textRange?.startOffset
                if (startOffset != null) {
                    val lineTpl = document.getLineNumber(this.textRange.startOffset)
                    val parentLine = document.getLineNumber(startOffset)
                    if (lineTpl != parentLine) {
                        startOffset += JS_INDENT_SIZE
                    }
                }
            }

            if (startOffset == null) {
                return forChild.ifTrue { Indent.getNormalIndent() } ?: Indent.getNoneIndent()
            }

            val line = document.getLineNumber(startOffset)
            val lineOffset = document.getLineStartOffset(line)
            val offset = startOffset - lineOffset


            return Indent.getSpaceIndent(offset + (forChild.ifTrue { INDENT_SIZE } ?: 0))
        }

        return Indent.getNoneIndent()
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        return ChildAttributes(getBaseIndent(true), null)
    }
}

class GtsFormattingModelBuilder : AbstractXmlTemplateFormattingModelBuilder() {
    val jsModelBuilder = JavascriptFormattingModelBuilder()

    fun findRootBlock(block: JsBlockWrapper, element: PsiElement): Block? {
        if (block.block is RootBlockWrapper && block.textRange.contains(element.textRange)) {
            return block
        }
        block.subBlocks.forEach {
            val b = findRootBlock(it, element)
            if (b != null) {
                return b
            }
        }
        return null
    }

    fun findTemplateRootBlock(block: Block, element: PsiElement): DataLanguageBlockWrapper? {
        if (block is DataLanguageBlockWrapper && block.node is HtmlTag && (block.node as HtmlTag).parent is HtmlDocumentImpl && block.textRange.contains(element.textRange)) {
            return block
        }
        if (block is XmlTagBlock && block.node is HtmlTag && (block.node as HtmlTag).parent is HtmlDocumentImpl && block.textRange.contains(element.textRange)) {
            return DataLanguageBlockWrapper.create(block, null)
        }
        block.subBlocks.forEach {
            if (it is AnotherLanguageBlockWrapper) {
                return@forEach
            }
            val b = findTemplateRootBlock(it, element)
            if (b != null) {
                return b
            }
        }
        return null
    }

    fun createAbstractBlock(node: ASTNode): AbstractBlock {
        return object : AbstractBlock(node, NoWrap, Alignment.createAlignment()) {
            override fun buildChildren(): List<Block> {
                return emptyList()
            }

            override fun getSpacing(child1: Block?, child2: Block): Spacing? {
                return Spacing.getReadOnlySpacing()
            }

            override fun isLeaf(): Boolean {
                return true
            }
        }
    }


    override fun createModel(formattingContext: FormattingContext): FormattingModel {

        if (formattingContext.node is OuterLanguageElement) {
            return DocumentBasedFormattingModel(createAbstractBlock(formattingContext.node), (formattingContext.node as OuterLanguageElement).project, formattingContext.codeStyleSettings, formattingContext.containingFile.fileType, formattingContext.containingFile)
        }

        var element = formattingContext.psiElement.containingFile.findElementAt(formattingContext.formattingRange.startOffset) ?: formattingContext.psiElement
        if (formattingContext.psiElement is PsiFile && formattingContext.formattingRange.startOffset == 0) {
            element = formattingContext.containingFile.viewProvider.getPsi(TS) ?: formattingContext.containingFile.viewProvider.getPsi(JS)
        }
        val tsFile = formattingContext.containingFile.viewProvider.getPsi(TS) ?: formattingContext.containingFile.viewProvider.getPsi(JS)
        val m = jsModelBuilder.createModel(formattingContext.withPsiElement(tsFile))
        val jsModel = JavascriptFormattingModelBuilder.createJSFormattingModel(tsFile, formattingContext.codeStyleSettings, JSAstBlockWrapper(m.rootBlock as ASTBlock, null, null))
        if (element.language is JSLanguageDialect) {
            return jsModel
        }
        if (element.language == XMLLanguage.INSTANCE || element.language == HTMLLanguage.INSTANCE || element.language == HbLanguage.INSTANCE) {
            val block = findRootBlock(jsModel.rootBlock as JsBlockWrapper, element) ?: return DocumentBasedFormattingModel(createAbstractBlock(formattingContext.node), formattingContext.node.psi.project, formattingContext.codeStyleSettings, formattingContext.containingFile.fileType, formattingContext.containingFile)
            val psiFile = element.containingFile
            val documentModel = FormattingDocumentModelImpl.createOn(psiFile)
            val model = XmlFormattingModel(
                    psiFile,
                    block,
                    documentModel)
            return DocumentBasedFormattingModel(model.rootBlock, element.project, formattingContext.codeStyleSettings, psiFile.fileType, psiFile)
        }
        return jsModel
    }

    override fun isTemplateFile(file: PsiFile?): Boolean {
        return file is GtsFile
    }

    override fun isOuterLanguageElement(element: PsiElement?): Boolean {
        return element is OuterLanguageElement
    }

    override fun isMarkupLanguageElement(element: PsiElement?): Boolean {
        return false
    }

    override fun createTemplateLanguageBlock(node: ASTNode, settings: CodeStyleSettings, xmlFormattingPolicy: XmlFormattingPolicy?, indent: Indent?, alignment: Alignment?, wrap: Wrap?): Block {
        val element = node.psi.containingFile.viewProvider.findElementAt(node.startOffset) ?: node.psi
        val file: PsiFile = element.containingFile

        if (element.language == XMLLanguage.INSTANCE || element.language == HTMLLanguage.INSTANCE || element.language == HbLanguage.INSTANCE) {
            val psiFile = element.containingFile
            val htmlElement = element.containingFile.viewProvider.findElementAt(element.startOffset, HTMLLanguage.INSTANCE)!!
            val ctxElement = element.containingFile.viewProvider.findElementAt(element.startOffset, HbLanguage.INSTANCE)!!
            val htmlModel = LanguageFormatting.INSTANCE.forLanguage(HTMLLanguage.INSTANCE).createModel(FormattingContext.create(htmlElement, settings))
            val hbsRootBlock = LanguageFormatting.INSTANCE.forLanguage(HbLanguage.INSTANCE).createModel(FormattingContext.create(ctxElement, settings)).rootBlock
            val htmlRootBlock = htmlModel.rootBlock
            val block = findTemplateRootBlock(hbsRootBlock, element) ?: findTemplateRootBlock(htmlRootBlock, element) ?: return createAbstractBlock(node)
            val documentModel = FormattingDocumentModelImpl.createOn(block.node!!.psi.containingFile)
            val rootBlock = RootBlockWrapper(block, HtmlPolicy(settings, documentModel))
            val model = XmlFormattingModel(
                    block.node!!.psi.containingFile,
                    rootBlock,
                    documentModel)
            return DocumentBasedFormattingModel(model.rootBlock, element.project, settings, file.fileType, file).rootBlock
        }
        return createModel(FormattingContext.create(node.psi, settings)).rootBlock
    }

}
