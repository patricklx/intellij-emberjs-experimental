package com.emberjs.hbs

import com.emberjs.gts.GtsFileType
import com.intellij.lang.javascript.psi.impl.JSFileImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubUpdatingIndex
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileBasedIndex
import org.junit.Test

class GtsFileTest : BasePlatformTestCase() {
    @Test
    fun testFileStub() {
        val gts = """
            import x from "a";
            export const a = 2;
        """.trimIndent()
        myFixture.configureByText(GtsFileType.INSTANCE, gts)
        (myFixture.file as JSFileImpl).calcStubTree()
        assertNotNull("should have stub definition", (myFixture.file as JSFileImpl).greenStub)
    }
}
