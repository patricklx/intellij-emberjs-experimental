package com.emberjs.gts

import com.dmarcotte.handlebars.parsing.HbTokenTypes
import com.dmarcotte.handlebars.psi.HbPsiElement
import com.emberjs.hbs.ResolvedReference
import com.emberjs.psi.EmberNamedElement
import com.emberjs.utils.ifTrue
import com.intellij.lang.ecmascript6.psi.ES6ImportSpecifier
import com.intellij.lang.javascript.JavaScriptSupportLoader
import com.intellij.lang.javascript.psi.JSPsiNamedElementBase
import com.intellij.lang.javascript.psi.JSVariable
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.RequestResultProcessor
import com.intellij.psi.search.SearchRequestCollector
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.elementType
import com.intellij.psi.xml.XmlToken
import com.intellij.util.Processor


class GtsReferenceSearcher : QueryExecutorBase<PsiReference?, ReferencesSearch.SearchParameters>(true) {
    override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference?>) {
        var element = queryParameters.elementToSearch
        if (element is JSPsiNamedElementBase) {
            val name = element.name
            if (name != null) {
                if (element.containingFile is GtsFile){
                   if (element is JSVariable) {
                       val psi = element.containingFile.viewProvider.getPsi(JavaScriptSupportLoader.TYPESCRIPT) ?: element.containingFile.viewProvider.getPsi(JavaScriptSupportLoader.ECMA_SCRIPT_6)
                       element = psi.findElementAt(element.textOffset)?.parent ?: element
                   }
                }
                val effectiveScope = if (element is JSVariable && (queryParameters.effectiveSearchScope as? LocalSearchScope)?.scope?.size == 1) {
                    LocalSearchScope(element.containingFile)
                } else {
                    queryParameters.effectiveSearchScope
                }
                val collector = queryParameters.optimizer
                collector.searchWord(name, effectiveScope, 1.toShort(), true, element, MyProcessor(queryParameters, element))
            }
        }
    }

    private class MyProcessor(queryParameters: ReferencesSearch.SearchParameters, elementToSearch: PsiElement) : RequestResultProcessor() {
        private val myQueryParameters: ReferencesSearch.SearchParameters
        private val myOwnCollector: SearchRequestCollector
        private val myElementToSearch: PsiElement

        init {
            myQueryParameters = queryParameters
            myElementToSearch = elementToSearch
            myOwnCollector = SearchRequestCollector(queryParameters.optimizer.searchSession)
        }

        override fun processTextOccurrence(element: PsiElement, offsetInElement: Int, consumer: Processor<in PsiReference>): Boolean {
            if (myElementToSearch.containingFile is GtsFile) {
                consumer.process(ResolvedReference(myElementToSearch, myElementToSearch))
                return false
            }
            return if ((element is HbPsiElement && element.elementType == HbTokenTypes.ID) || (element is XmlToken && element.parent is HtmlTag)) {
                val elem = (element is XmlToken && element.parent is HtmlTag).ifTrue { element.parent } ?: element
                var foundRef = (elem.reference?.isReferenceTo(myElementToSearch) == true).ifTrue { elem.reference } ?: elem.references.find {
                    it.isReferenceTo(myElementToSearch)
                }

                if (foundRef != null) {
                    consumer.process(foundRef)
                    return false
                }

                var resolved = elem.reference?.resolve()
                if (resolved is EmberNamedElement) {
                    resolved = resolved.target
                }
                if (resolved == myElementToSearch) {
                    foundRef = elem.reference
                    consumer.process(foundRef)
                    return false
                }
                foundRef = (resolved as? ES6ImportSpecifier)?.let {
                    val results = it.multiResolve(false)
                    results.find { it.element == myElementToSearch }?.let { ResolvedReference(element, myElementToSearch) }
                }
                if (foundRef != null) {
                    consumer.process(foundRef)
                    return false
                }
                val ref = elem.references.find { it.resolve() is ES6ImportSpecifier || it.resolve() is EmberNamedElement }
                resolved = ref?.resolve()
                if (resolved is EmberNamedElement) {
                    resolved = resolved.target
                }
                if (resolved == myElementToSearch) {
                    consumer.process(ref)
                    return false
                }
                val found = (resolved as? ES6ImportSpecifier)?.let {
                   val results = it.multiResolve(false)
                   results.any { it.element == myElementToSearch }
               } ?: false
                if (found) {
                    consumer.process(ref)
                }
                return !found
            } else {
                true
            }
        }
    }
}

