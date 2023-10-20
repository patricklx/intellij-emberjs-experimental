package com.emberjs.translations

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Paths

class EmberIntlTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String? {
        val resource = ClassLoader.getSystemResource("com/emberjs/translations/fixtures")
        return Paths.get(resource.toURI()).toAbsolutePath().toString()
    }

    fun testFindBaseLocale() {
        // Load fixture files into the project
        myFixture.copyDirectoryToProject("ember-intl-with-base-locale", "/")

        val psiFile = myFixture.configureByFile("app/templates/base-locale-test.hbs")

        assertThat(EmberIntl.findBaseLocale(psiFile)).isEqualTo("de")
    }

    fun testFindFallbackLocale() {
        // Load fixture files into the project
        myFixture.copyDirectoryToProject("ember-intl-with-fallbackLocale", "/")

        val psiFile = myFixture.configureByFile("app/templates/base-locale-test.hbs")

        assertThat(EmberIntl.findBaseLocale(psiFile)).isEqualTo("de")
    }

    fun testFindIncludeLocales() {
        // Load fixture files into the project
        myFixture.copyDirectoryToProject("ember-intl-with-includeLocales", "/")

        val psiFile = myFixture.configureByFile("app/templates/base-locale-test.hbs")

        assertThat(EmberIntl.findBaseLocale(psiFile)).isEqualTo("de")
    }
}
