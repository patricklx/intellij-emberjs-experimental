package com.emberjs.hbs

import com.emberjs.gts.GjsFileType
import com.emberjs.gts.GtsFileType
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.javascript.inspections.ES6UnusedImportsInspection
import com.intellij.lang.javascript.inspections.JSUnusedGlobalSymbolsInspection
import com.intellij.lang.javascript.inspections.JSUnusedLocalSymbolsInspection
import com.intellij.lang.javascript.psi.impl.JSFileImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
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
            const grault = {};
            
            function corge() {};
            function corge2() {};
            
            export const grault2 = {}; 
            export const Grault = {}; 
            
            export default <template>
                <Foo />
                <Baz />
                <Grault />
                {{x}}
                {{y}}
                {{corge}}
                {{bar}}
                {{grault2}}
            </template>
        """.trimIndent()
        myFixture.configureByText(GjsFileType.INSTANCE, gts)
        myFixture.enableInspections(ES6UnusedImportsInspection(), JSUnusedLocalSymbolsInspection(), JSUnusedGlobalSymbolsInspection())
        CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project)
        val highlighting = myFixture.doHighlighting()
        val unusedConstants = highlighting.filter { it.description?.startsWith("Unused constant") == true }
        TestCase.assertEquals(unusedConstants.toString(), 2, unusedConstants.size)
        val highlightInfos: List<HighlightInfo> = highlighting.filter { it.inspectionToolId == "ES6UnusedImports" || it.inspectionToolId == "JSUnusedLocalSymbols" }
        TestCase.assertEquals(highlighting.toString(), 5, highlightInfos.size)
        TestCase.assertTrue(highlightInfos[0].description, highlightInfos[0].description.contains("quux"))
        TestCase.assertTrue(highlightInfos[1].description, highlightInfos[1].description.contains("qux"))
        TestCase.assertTrue(highlightInfos[2].description, highlightInfos[2].description.contains("grault"))
        TestCase.assertTrue(highlightInfos[3].description, highlightInfos[3].description.contains("grault"))
        TestCase.assertTrue(highlightInfos[4].description, highlightInfos[4].description.contains("corge2"))
    }

    @Test
    fun testGtsImportUsed() {
        val otherGts = """
            export const OtherComponent = 2;
            export const other = 2;
        """.trimIndent()
        val gts = """
            import { OtherComponent, other } from './other-component';
            import x from "a";
            import { y, quux } from "a";
            import qux from "a";
            import { Foo } from "a";
            
            const bar = () => null;
            
            const Baz = {};
            
            export const grault = {}; 
            export const Grault = {}; 
            
            export default <template>
                <OtherComponent />
                <Foo />
                <Baz />
                <Grault />
                {{x}}
                {{y}}
                {{bar}}
                {{grault}}
                {{other}}
            </template>
        """.trimIndent()
        myFixture.addFileToProject("other-component.js", otherGts)
        myFixture.addFileToProject("main.gts", gts)
        myFixture.configureByFile("main.gts")
        myFixture.enableInspections(ES6UnusedImportsInspection(), JSUnusedLocalSymbolsInspection(), JSUnusedGlobalSymbolsInspection())
        CodeInsightTestFixtureImpl.ensureIndexesUpToDate(project)
        val highlighting = myFixture.doHighlighting()
        System.out.println(highlighting)
        val unusedConstants = highlighting.filter { it.description?.startsWith("Unused constant") == true }
        TestCase.assertEquals(unusedConstants.toString(), 0, unusedConstants.size)
        val highlightInfos: List<HighlightInfo> = highlighting.filter { it.inspectionToolId == "ES6UnusedImports" }
        TestCase.assertEquals(2, highlightInfos.size)
        TestCase.assertTrue(highlightInfos.first().description.contains("quux"))
        TestCase.assertTrue(highlightInfos.last().description.contains("qux"))
    }
}
