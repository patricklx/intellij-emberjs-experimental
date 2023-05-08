package com.emberjs.gts

import com.intellij.lang.javascript.JavaScriptSupportLoader
import com.intellij.lang.javascript.linter.JSLinterInput
import com.intellij.lang.javascript.linter.eslint.EslintExternalAnnotator
import com.intellij.lang.javascript.linter.eslint.EslintState
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile


class FakeVirtualVile(val virtualFile: VirtualFile): VirtualFile() {

    override fun isInLocalFileSystem() = true
    override fun getExtension() = "ts"
    override fun getName() = virtualFile.name
    override fun getFileSystem() = virtualFile.fileSystem
    override fun getPath() = virtualFile.path.replace(".gts", ".ts")
    override fun isWritable() = virtualFile.isWritable
    override fun isDirectory() = virtualFile.isDirectory
    override fun isValid() = virtualFile.isValid
    override fun getParent() = virtualFile.parent
    override fun getChildren() = virtualFile.children
    override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long) = virtualFile.getOutputStream(requestor, newModificationStamp, newTimeStamp)
    override fun contentsToByteArray() = virtualFile.contentsToByteArray()
    override fun getTimeStamp() = virtualFile.timeStamp
    override fun getLength() = virtualFile.length
    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) = virtualFile.refresh(asynchronous, recursive, postRunnable)
    override fun getInputStream() = virtualFile.inputStream
    override fun getFileType() = virtualFile.fileType
    override fun getModificationStamp() = 0.0.toLong()
}

class FakeFile(val psiFile: PsiFile): PsiFile by psiFile {
    override fun getVirtualFile() = FakeVirtualVile(psiFile.virtualFile)

}

class GtsEslintExternalAnnotator: EslintExternalAnnotator() {

    override fun createInfo(psiFile: PsiFile, state: EslintState, colorsScheme: EditorColorsScheme?): JSLinterInput<EslintState> {
        return super.createInfo(FakeFile(psiFile.viewProvider.getPsi(JavaScriptSupportLoader.TYPESCRIPT)), state, colorsScheme)
    }
    override fun acceptPsiFile(file: PsiFile): Boolean {
        return file is GtsFile
    }
}