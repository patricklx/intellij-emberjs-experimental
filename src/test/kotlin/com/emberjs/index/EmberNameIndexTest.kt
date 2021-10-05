package com.emberjs.index

import com.emberjs.resolver.EmberName
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.indexing.FileBasedIndex
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Paths

class EmberNameIndexTest : BasePlatformTestCase() {
    override fun getTestDataPath(): String? {
        val resource = ClassLoader.getSystemResource("com/emberjs/fixtures")
        return Paths.get(resource.toURI()).toAbsolutePath().toString()
    }

    private fun doTest(vararg modules: String) {
        // Load fixture files into the project
        FileBasedIndex.getInstance().apply {
            invalidateCaches()
            requestRebuild(EmberNameIndex.NAME)
        }

        myFixture.copyDirectoryToProject("crates.io", "/")

// Rebuild index now that the `package.json` file is copied over

        assertThat(EmberNameIndex.getAllKeys(myFixture.project))
                .containsOnly(*modules.map { EmberName.from(it) }.toTypedArray())



    }

    fun testExample() = doTest(
            "controller:application",
            "controller:user/index",
            "controller:user/new",
            "route:index",
            "helper-test:format-number",
            "acceptance-test:user-page")

    fun testNoEmberCli() = doTest()
}
