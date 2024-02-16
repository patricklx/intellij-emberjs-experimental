package com.emberjs.utils

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile

val AnActionEvent.module: Module?
    get() = getData(LangDataKeys.MODULE)

val AnActionEvent.emberRoot: VirtualFile?
    get() = module?.emberRoot

val AnActionEvent.hasEmberRoot: Boolean
    get() = emberRoot != null

fun AnActionEvent.getIdeView() =
        getData(LangDataKeys.IDE_VIEW)
