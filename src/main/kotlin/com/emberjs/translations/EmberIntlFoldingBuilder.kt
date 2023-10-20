package com.emberjs.translations

import com.dmarcotte.handlebars.psi.HbHash
import com.dmarcotte.handlebars.psi.HbParam
import com.dmarcotte.handlebars.psi.HbPsiFile
import com.emberjs.hbs.HbsPatterns
import com.emberjs.utils.*
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilder
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Key

class EmberIntlFoldingBuilder : FoldingBuilder {
    override fun buildFoldRegions(node: ASTNode, document: Document): Array<FoldingDescriptor> {
        val file = node.psi as? HbPsiFile ?: return emptyArray()

        if (findMainPackageJson(file.virtualFile)?.isDependencyOfAnyType("ember-intl") != true) return emptyArray()

        val simpleMustaches = file.collect(HbsPatterns.TRANSLATION_KEY.toFilter())
        val subexpressions = file.collect(HbsPatterns.TRANSLATION_KEY_IN_SEXPR.toFilter())

        val baseLocale = EmberIntl.findBaseLocale(file)

        return (simpleMustaches + subexpressions).map { buildFoldingDescriptor(it, baseLocale) }.toTypedArray()
    }

    private fun buildFoldingDescriptor(element: HbParam, baseLocale: String?): FoldingDescriptor {
        // read translation key from HbParam element
        val key = element.text.unquote()

        // query translation index for translation key
        val translations = EmberIntlIndex.getTranslations(key, element.project)

        // store translations and base locale on ASTNode user data
        element.node.putUserData(TRANSLATIONS_KEY, translations)
        element.node.putUserData(BASE_LOCALE_KEY, baseLocale)

        return FoldingDescriptor(element, element.parent.textRange)
    }

    override fun getPlaceholderText(node: ASTNode): String? = when {
        node.psi.parent is HbParam -> "\"${getRawPlaceholderText(node)}\""
        else -> getRawPlaceholderText(node)
    }

    private fun getRawPlaceholderText(node: ASTNode): String {
        val translations = node.getUserData(TRANSLATIONS_KEY)
        val baseLocale = node.getUserData(BASE_LOCALE_KEY)

        val translation = translations?.get(baseLocale) ?:
                translations?.get("en-us") ?:
                translations?.get("en") ?:
                translations?.values?.firstOrNull()


        return translation?.fillPlaceholders(node) ?: "Missing translation: ${node.text.unquote()}"
    }

    override fun isCollapsedByDefault(node: ASTNode): Boolean {
        return node.getUserData(TRANSLATIONS_KEY).let { it != null && it.isNotEmpty() }
    }

    private fun String.fillPlaceholders(node: ASTNode): String {
        return fillPlaceholders(node.psi as HbParam)
    }

    private fun String.fillPlaceholders(param: HbParam): String {
        val hashParams = param.parent.children
                .filterIsInstance(HbHash::class.java)
                .map { hash -> hash.hashName?.let { it to hash.valueElement } }
                .filterNotNull()
                .toMap()

        if (hashParams.isEmpty()) return this

        return PLACEHOLDER_RE.replace(this) {
            val (key) = it.destructured
            hashParams[key]?.let { extractPlaceholderReplacement(it.text) } ?: it.value
        }
    }

    private val HbHash.valueElement: HbParam?
        get() = children.filterIsInstance(HbParam::class.java).firstOrNull()

    private fun extractPlaceholderReplacement(text: String) = when {
        text.startsWith("\"") -> text.removeSurrounding("\"")
        text.startsWith("'") -> text.removeSurrounding("'")
        text.startsWith("(") -> text.removeSurrounding("(", ")").prepend("{{").append("}}")
        else -> text.prepend("{{").append("}}")
    }

    companion object {
        private val TRANSLATIONS_KEY = Key<Map<String, String>>("ember-intl.translations")
        private val BASE_LOCALE_KEY = Key<String?>("ember-intl.baseLocale")
        private val PLACEHOLDER_RE = """\{([\w-]+)\}""".toRegex()
    }
}
