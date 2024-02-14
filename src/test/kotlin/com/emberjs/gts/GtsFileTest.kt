package com.emberjs.hbs

import com.emberjs.gts.GjsFileType
import com.emberjs.gts.GtsFileType
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.javascript.inspections.ES6UnusedImportsInspection
import com.intellij.lang.javascript.psi.impl.JSFileImpl
import com.intellij.lang.javascript.validation.UnusedImportsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase
import org.junit.Test

class GtsFileTest : BasePlatformTestCase() {
    @Test
    fun testGtsStub() {
        val gts = """
            import x from "a";
            export const a = 2;
        """.trimIndent()
        myFixture.configureByText(GtsFileType.INSTANCE, gts)
        (myFixture.file as JSFileImpl).calcStubTree()
        assertNotNull("should have stub definition", (myFixture.file as JSFileImpl).greenStub)
    }

    @Test
    fun testGjsImportUsed() {
        val gts = """
            import x from "a";
            import { y, quux } from "a";
            import qux from "a";
            import { Foo } from "a";
            
            const bar = () => null;
            
            const Baz = {};
            
            export default <template>
                <Foo />
                <Baz />
                {{x}}
                {{y}}
                {{bar}}
            </template>
        """.trimIndent()
        myFixture.configureByText(GjsFileType.INSTANCE, gts)
        myFixture.enableInspections(ES6UnusedImportsInspection())
        val highlightInfos: List<HighlightInfo> = myFixture.doHighlighting().filter { it.inspectionToolId == "ES6UnusedImports" }
        TestCase.assertEquals(2, highlightInfos.size);
        TestCase.assertTrue(highlightInfos.first().description.contains("quux"));
        TestCase.assertTrue(highlightInfos.last().description.contains("qux"));
    }

    @Test
    fun testGtsImportUsed() {
        val gts = """
            import x from "a";
            import { y, quux } from "a";
            import qux from "a";
            import { Foo } from "a";
            
            const bar = () => null;
            
            const Baz = {};
            
            export default <template>
                <Foo />
                <Baz />
                {{x}}
                {{y}}
                {{bar}}
            </template>
        """.trimIndent()
        myFixture.configureByText(GtsFileType.INSTANCE, gts)
        myFixture.enableInspections(ES6UnusedImportsInspection())
        val highlightInfos: List<HighlightInfo> = myFixture.doHighlighting().filter { it.inspectionToolId == "ES6UnusedImports" }
        TestCase.assertEquals(highlightInfos.size, 2);
        TestCase.assertTrue(highlightInfos.first().description.contains("quux"));
        TestCase.assertTrue(highlightInfos.last().description.contains("qux"));
    }
}
