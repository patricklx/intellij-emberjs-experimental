package com.emberjs.gts

import com.dmarcotte.handlebars.HbLanguage
import com.dmarcotte.handlebars.editor.actions.HbTypedHandler
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
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

    override fun beforeCharTyped(c: Char, project: Project, editor: Editor, file: PsiFile, fileType: FileType): Result {
        if (file.viewProvider !is GtsFileViewProvider) {
            return Result.CONTINUE
        }
        return super.beforeCharTyped(c, project, editor, file, fileType)
    }

    override fun beforeSelectionRemoved(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file.viewProvider !is GtsFileViewProvider) {
            return Result.CONTINUE
        }
        return super.beforeSelectionRemoved(c, project, editor, file)
    }

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result {
        if (file.viewProvider !is GtsFileViewProvider) {
            return Result.CONTINUE
        }
        return super.charTyped(c, project, editor, FakeViewProviderFile(file))
    }
}