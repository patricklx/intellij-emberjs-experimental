package com.emberjs.gts

import com.dmarcotte.handlebars.HbLanguage
import com.dmarcotte.handlebars.editor.actions.HbTypedHandler
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile


class FakeViewProviderFile(val psiFile: PsiFile): PsiFile by psiFile {
    override fun getViewProvider(): FileViewProvider {
        return object : FileViewProvider by psiFile.viewProvider {
            override fun getBaseLanguage(): Language {
                return HbLanguage.INSTANCE
            }
        }
    }
}

class GtsTypeHandler: HbTypedHandler() {

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        return super.charTyped(c, project, editor, FakeViewProviderFile(file))
    }
}