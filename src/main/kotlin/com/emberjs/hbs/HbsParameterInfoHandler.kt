package com.emberjs.hbs

import com.dmarcotte.handlebars.parsing.HbTokenTypes
import com.dmarcotte.handlebars.psi.HbParam
import com.dmarcotte.handlebars.psi.HbPsiElement
import com.emberjs.utils.EmberUtils
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.lang.javascript.psi.JSParameterListElement
import com.intellij.lang.javascript.psi.JSRecordType
import com.intellij.lang.javascript.psi.JSType
import com.intellij.lang.javascript.psi.ecma6.TypeScriptCallSignature
import com.intellij.lang.javascript.psi.ecma6.TypeScriptVariable
import com.intellij.lang.javascript.psi.types.JSArrayType
import com.intellij.lang.javascript.psi.types.JSTupleType
import com.intellij.lang.parameterInfo.*
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType


class HbsParameterInfoHandler : ParameterInfoHandler<PsiElement, Any?> {

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): PsiElement? {
        val psiElement = context.file.findElementAt(context.offset)
        val block = PsiTreeUtil.collectElements(EmberUtils.findFirstHbsParamFromParam(psiElement)) {  it is HbPsiElement && it.elementType == HbTokenTypes.ID }.firstOrNull()
        val ref = EmberUtils.followReferences(block)
        if (ref == null || ref !is JSFunction || ref !is TypeScriptCallSignature) {
            return null
        }

        if (ref is TypeScriptVariable) {
            val signatures = ref.jsType?.asRecordType()?.properties?.firstOrNull()?.jsType?.asRecordType()?.typeMembers;
            signatures?.mapNotNull { it as? TypeScriptCallSignature }?.firstOrNull()?.let {
                val namedParams = it.parameters[0].jsType?.asRecordType()
                val positional = it.parameters.slice(IntRange(1, it.parameters.lastIndex))
                val args = emptyList<Any>().toMutableList()
                args.add(positional)
                namedParams?.let { args.add(it) }
                context.itemsToShow = args.toTypedArray()
            }
            return null
        }

        val func: JSFunction = ref
        val args = emptyList<Any>().toMutableList()
        val argType = func.parameters.last().jsType
        if (argType is JSRecordType) {
            val positional = argType.findPropertySignature("positional")
            if (positional != null) {
                args.add(positional)
            }
            val named = argType.findPropertySignature("named")
            if (named != null) {
                args.add(named)
            }
            if (args.size > 0) {
                context.itemsToShow = args.toTypedArray()
            } else {
                context.itemsToShow = func.parameters
            }
        }

        return psiElement
    }

    override fun showParameterInfo(element: PsiElement, context: CreateParameterInfoContext) {
        context.showHint(element, element.textOffset, this)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): PsiElement? {
        return context.file.findElementAt(context.offset)
    }

    override fun updateParameterInfo(parameterOwner: PsiElement, context: UpdateParameterInfoContext) {
        val psiElement = context.file.findElementAt(context.offset)
        if (psiElement == null) {
            return
        }
        val currentParam = psiElement.parent.children.filter { it is HbParam }.indexOf(psiElement) - 1
        context.setCurrentParameter(currentParam)
    }

    override fun updateUI(p: Any?, context: ParameterInfoUIContext) {
        var text = ""
        if (p == null) {
            return
        }
        var type: JSType? = null
        var arrayName: String? = null
        if (p is JSParameterListElement) {
            arrayName = p.name
            type = p.inferredType
        }
        if (p is JSRecordType.PropertySignature) {
            arrayName = p.memberName
            type = p.jsType
        }
        if (type is JSArrayType || type is JSTupleType) {
            if (type is JSTupleType) {
                val names = type.sourceElement?.children?.map { it.text } ?: emptyList<String>()
                text += names.mapIndexed { index, s -> "$s:${type.getTypeByIndex(index) ?: "unknown"}" }.joinToString(",")
            } else {
                val arrayType = type as JSArrayType
                text += arrayName + ":" + (arrayType.type?.resolvedTypeText ?: "*")
            }
        }

        if (type is JSRecordType) {
            text += type.properties.map { it.memberName + ":" + it.jsType?.resolvedTypeText + "=" }.joinToString(",")
        }


        if (text == "") {
            return
        }
        context.setupUIComponentPresentation(text, 0, 0, false, false, false, context.defaultParameterColor)
    }

}
