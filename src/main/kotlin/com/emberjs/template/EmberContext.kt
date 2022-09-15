package com.emberjs.template

import com.dmarcotte.handlebars.file.HbFileType
import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType


class EmberContext : TemplateContextType("EMBER", "Ember") {

    override fun isInContext(context: TemplateActionContext): Boolean {
        return context.getFile().fileType is HbFileType
    }
}