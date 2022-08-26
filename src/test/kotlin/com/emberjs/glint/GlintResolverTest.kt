package com.emberjs.glint

import com.emberjs.Ember
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.project.guessProjectDir
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.text.findTextRange
import org.junit.Test
import java.nio.file.Paths

class GlintResolverTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String? {
        val resource = ClassLoader.getSystemResource("com/emberjs/fixtures")
        return Paths.get(resource.toURI()).toAbsolutePath().toString()
    }

    @Test
    fun testGlint() {
        // Load fixture files into the project
        myFixture.copyDirectoryToProject("example", "/")

        val file = myFixture.project.guessProjectDir()!!.findFileByRelativePath("app/application/template.hbs")
        myFixture.openFileInEditor(file!!)
        val range = myFixture.file.text.findTextRange("item.")!!
        myFixture.editor.caretModel.moveToOffset(range.endOffset)
        myFixture.complete(CompletionType.BASIC)
        myFixture.lookupElementStrings
    }
}
