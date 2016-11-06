package com.emberjs.intl

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase
import com.intellij.util.indexing.FileBasedIndex
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import java.nio.file.Paths

class EmberTranslationIndexTest : LightPlatformCodeInsightFixtureTestCase() {

    fun testSimple() = doTest("foo", mapOf("en" to "bar baz", "de" to "Bar Baz"))
    fun testPlaceholder() = doTest("long-string", mapOf("en" to "Something veeeeery long with a {placeholder}"))
    fun testNested() = doTest("parent.child", mapOf("en" to "this is nested"))
    fun testQuotes1() = doTest("quote-test1", mapOf("en" to "Foo'bar"))
    fun testQuotes2() = doTest("quote-test2", mapOf("en" to "Foo'bar"))
    fun testQuotes3() = doTest("quote-test3", mapOf("en" to "Foo\"bar"))
    fun testQuotes4() = doTest("quote-test4", mapOf("en" to "Foo\"bar"))

    fun testAllKeys() {
        val keys = EmberTranslationIndex.getTranslationKeys(myFixture.project)
        assertThat(keys).containsOnly("foo", "long-string", "parent.child",
                "quote-test1", "quote-test2", "quote-test3", "quote-test4")
    }

    override fun getTestDataPath(): String? {
        val resource = ClassLoader.getSystemResource("com/emberjs/intl/fixtures")
        return Paths.get(resource.toURI()).toAbsolutePath().toString()
    }

    override fun setUp() {
        super.setUp()

        // Load fixture files into the project
        myFixture.copyDirectoryToProject("fixture1", "/")

        // Rebuild index now that the `package.json` file is copied over
        FileBasedIndex.getInstance().requestRebuild(EmberTranslationIndex.NAME)
    }

    private fun doTest(key: String, expected: Map<String, String>) {
        val translations = EmberTranslationIndex.getTranslations(key, myFixture.project)
        val _expected = expected.entries.map { entry(it.key, it.value) }.toTypedArray()
        assertThat(translations).containsOnly(*_expected)
    }
}