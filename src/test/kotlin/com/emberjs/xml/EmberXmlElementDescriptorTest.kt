package com.emberjs.xml

import com.dmarcotte.handlebars.file.HbFileType
import com.intellij.lang.Language
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

class EmberXmlElementDescriptorTest : BasePlatformTestCase() {

    private fun findDivTag(): XmlTag {
        myFixture.configureByText(HbFileType.INSTANCE, "<div></div>")
        val htmlView = myFixture.file.viewProvider.getPsi(Language.findLanguageByID("HTML")!!)
        return PsiTreeUtil.collectElements(htmlView) { it is XmlTag && it.name == "div" }.first() as XmlTag
    }

    @Test
    fun testGetAttributesDescriptorsIncludesCommonHtmlAttributesInSmartMode() {
        val tag = findDivTag()
        val descriptor = EmberXmlElementDescriptor(tag, null)

        val attributes = descriptor.getAttributesDescriptors(tag)

        assertTrue(attributes.isNotEmpty())
        assertTrue(attributes.any { it.getName() == "class" })
    }

    @Test
    fun testGetAttributesDescriptorsSkipsCommonHtmlAttributesInDumbMode() {
        val tag = findDivTag()
        val descriptor = EmberXmlElementDescriptor(tag, null)

        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            val attributes = descriptor.getAttributesDescriptors(tag)
            assertTrue(attributes.isEmpty())
        }
    }
}
