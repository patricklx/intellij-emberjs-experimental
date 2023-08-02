package com.emberjs.gts

import com.dmarcotte.handlebars.parsing.HbTokenTypes
import com.dmarcotte.handlebars.psi.HbPsiElement
import com.emberjs.psi.EmberNamedElement
import com.intellij.lang.ecmascript6.psi.ES6ImportSpecifier
import com.intellij.lang.javascript.psi.*
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.RequestResultProcessor
import com.intellij.psi.search.SearchRequestCollector
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.elementType
import com.intellij.util.Processor


class GtsReferenceSearcher : QueryExecutorBase<PsiReference?, ReferencesSearch.SearchParameters>(true) {
    override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference?>) {
        val element = queryParameters.elementToSearch
        if (element is JSPsiNamedElementBase) {
            val name = element.name
            if (name != null) {
                val effectiveScope = queryParameters.effectiveSearchScope
                val collector = queryParameters.optimizer
                collector.searchWord(name, effectiveScope, 1.toShort(), true, element, MyProcessor(queryParameters))
            }
        }
    }

    private class MyProcessor(queryParameters: ReferencesSearch.SearchParameters) : RequestResultProcessor() {
        private val myQueryParameters: ReferencesSearch.SearchParameters
        private val myOwnCollector: SearchRequestCollector

        init {
            myQueryParameters = queryParameters
            myOwnCollector = SearchRequestCollector(queryParameters.optimizer.searchSession)
        }

        override fun processTextOccurrence(element: PsiElement, offsetInElement: Int, consumer: Processor<in PsiReference>): Boolean {
            return if (element is HbPsiElement && element.elementType == HbTokenTypes.ID) {
                var found = element.reference?.isReferenceTo(myQueryParameters.elementToSearch) == true || element.references.any {
                    it.isReferenceTo(myQueryParameters.elementToSearch)
                }
                if (!found) {
                    var resolved = element.reference?.resolve()
                    if (resolved is EmberNamedElement) {
                        resolved = resolved.target
                    }
                    found = (resolved as? ES6ImportSpecifier)?.let {
                        val results = it.multiResolve(false)
                        results.any { it.element == myQueryParameters.elementToSearch }
                    } ?: false
                }
                if (!found) {
                    var resolved = element.references.find { it.resolve() is ES6ImportSpecifier || it.resolve() is EmberNamedElement }?.resolve()
                    if (resolved is EmberNamedElement) {
                        resolved = resolved.target
                    }
                    found = (resolved as? ES6ImportSpecifier)?.let {
                        val results = it.multiResolve(false)
                        results.any { it.element == myQueryParameters.elementToSearch }
                    } ?: false
                }
                return !found
            } else {
                true
            }
        }
    }
}

