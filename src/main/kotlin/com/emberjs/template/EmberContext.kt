package com.emberjs.template

import com.dmarcotte.handlebars.file.HbFileViewProvider
import com.emberjs.gts.GtsFileViewProvider
import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType


class EmberContext : TemplateContextType("Ember") {

    override fun isInContext(context: TemplateActionContext): Boolean {
        return context.file.viewProvider is HbFileViewProvider || context.file.viewProvider is GtsFileViewProvider
    }
}