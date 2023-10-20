package com.emberjs.translations

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.Test
import java.nio.file.Paths

class EmberIntlIndexTest : BasePlatformTestCase() {
    @Test fun testSimple() = doTest("foo", mapOf("en" to "bar baz", "de" to "Bar Baz"))
    @Test fun testPlaceholder() = doTest("long-string", mapOf("en" to "Something veeeeery long with a {placeholder}"))
    @Test fun testNested() = doTest("parent.child", mapOf("en" to "this is nested"))
    @Test fun testQuotes1() = doTest("quote-test1", mapOf("en" to "Foo'bar"))
    @Test fun testQuotes2() = doTest("quote-test2", mapOf("en" to "Foo'bar"))
    @Test fun testQuotes3() = doTest("quote-test3", mapOf("en" to "Foo\"bar"))
    @Test fun testQuotes4() = doTest("quote-test4", mapOf("en" to "Foo\"bar"))
    @Test fun testJson() = doTest("foo", mapOf("en" to "bar baz"), "ember-intl-json")
    @Test fun testJsonFr() = doTest("foo", mapOf("fr" to "bar baz"), "ember-intl-json-fr")
    @Test fun testFallback() = doTest("foo", mapOf("de" to "Bar Baz", "en" to "bar baz"), "ember-intl-with-fallbackLocale")
    @Test fun testIncludeLocales() = doTest("foo", mapOf("de" to "Bar Baz", "en" to "bar baz"), "ember-intl-with-fallbackLocale")
    @Test fun testWithoutDependency() = doTest("foo", emptyMap(), "no-dependencies")

    @Test fun testAllKeys() {
        loadFixture("ember-intl")

        val keys = EmberIntlIndex.getTranslationKeys(myFixture.project)
        assertThat(keys).containsOnly("foo", "long-string", "parent.child", "nested.key.with-child",
                "quote-test1", "quote-test2", "quote-test3", "quote-test4")
    }

    override fun getTestDataPath(): String? {
        val resource = ClassLoader.getSystemResource("com/emberjs/translations/fixtures")
        return Paths.get(resource.toURI()).toAbsolutePath().toString()
    }

    private fun loadFixture(fixtureName: String) {
        // Load fixture files into the project
        myFixture.copyDirectoryToProject(fixtureName, "/")
    }

    private fun doTest(key: String, expected: Map<String, String>, fixtureName: String = "ember-intl") {
        loadFixture(fixtureName)

        val translations = EmberIntlIndex.getTranslations(key, myFixture.project)
        if (expected.isEmpty()) {
            assertThat(translations).isEmpty()
        } else {
            val _expected = expected.entries.map { entry(it.key, it.value) }.toTypedArray()
            assertThat(translations).containsOnly(*_expected)
        }
    }
}
