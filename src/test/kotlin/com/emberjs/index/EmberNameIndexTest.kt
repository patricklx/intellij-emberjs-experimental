package com.emberjs.index

import com.emberjs.project.ProjectService
import com.emberjs.resolver.EmberName
import com.emberjs.utils.clearVirtualCache
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Paths

class EmberNameIndexTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        project.getService(ProjectService::class.java)
    }
    override fun getTestDataPath(): String? {
        val resource = ClassLoader.getSystemResource("com/emberjs/index/fixtures")
        return Paths.get(resource.toURI()).toAbsolutePath().toString()
    }

    private fun doTest(vararg modules: String) {
        // Load fixture files into the project
        myFixture.copyDirectoryToProject(getTestName(true), "/")
        clearVirtualCache()
        assertThat(EmberNameIndex.getAllKeys(myFixture.project).map { it.fullName })
                .containsOnly(*modules.map { EmberName.from(it)?.fullName }.toTypedArray())
    }

    @Test fun testExample() = doTest(
            "controller:application:~/controllers/application",
            "controller:user/index:~/controllers/user/index",
            "controller:user/new:~/controllers/user/new",
            "route:index:~/routes/index",
            "helper-test:format-number",
            "acceptance-test:user-page")

    @Test fun testNoEmberCli() = doTest()
}
