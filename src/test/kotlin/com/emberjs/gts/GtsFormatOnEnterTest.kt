
import com.dmarcotte.handlebars.config.HbConfig
import com.emberjs.gts.GtsActionHandlerTest
import org.junit.Ignore
import org.junit.Test


class GtsFormatOnEnterTest() : GtsActionHandlerTest() {
    private var myPrevFormatSetting = false


    override fun setUp() {
        super.setUp()

        myPrevFormatSetting = HbConfig.isFormattingEnabled()
        HbConfig.setFormattingEnabled(true)
    }

    override fun tearDown() {
        try {
            HbConfig.setFormattingEnabled(myPrevFormatSetting)
        } catch (e: Throwable) {
            addSuppressedException(e)
        } finally {
            super.tearDown()
        }
    }

    /**
     * This sanity check should be enough to ensure that we don't format on Enter
     * when the formatter is disabled
     */
    @Test
    fun testEnterWithFormatterDisabled() {
        val previousFormatterSetting = HbConfig.isFormattingEnabled()
        HbConfig.setFormattingEnabled(false)

        doEnterTest(
            "{{#foo}}<caret>",

            "{{#foo}}\n" +
                    "<caret>"
        )

        HbConfig.setFormattingEnabled(previousFormatterSetting)
    }

    @Test
    fun testSimpleStache() {
        doEnterTest(
            "{{foo}}<caret>",

            "{{foo}}\n" +
                    "<caret>"
        )
    }

    @Test
    fun testSimpleBlock1() {
        doEnterTest(
            "{{#foo}}<caret>",

            "{{#foo}}\n" +
                    "    <caret>"
        )
    }

    @Test
    fun testSimpleBlock2() {
        doEnterTest(
            "{{#foo}}\n" +
                    "    {{bar}}<caret>htmlPadding",

            """
        {{#foo}}
            {{bar}}
            <caret>htmlPadding
            """.trimIndent()
        )
    }

    @Test
    fun testSimpleBlock3() {
        doEnterTest(
            """
        {{#foo}}
            {{bar}}<caret>
        {{/foo}}
        
        """.trimIndent(),

            """
        {{#foo}}
            {{bar}}
            <caret>
        {{/foo}}
        
        """.trimIndent()
        )
    }

    @Test
    fun testNestedBlocks1() {
        doEnterTest(
            """
        {{#foo}}
        {{#bar}}
        {{#bat}}<caret>
        {{baz}}
        {{/bat}}
        {{/bar}}
        {{/foo}}
        """.trimIndent(),

            """
        {{#foo}}
        {{#bar}}
        {{#bat}}
            <caret>
        {{baz}}
        {{/bat}}
        {{/bar}}
        {{/foo}}
        """.trimIndent()
        )
    }

    @Test
    fun testNestedBlocks2() {
        doEnterTest(
            """
        {{#foo}}
            {{#bar}}
                {{#bat}}<caret>
                    {{baz}}
                {{/bat}}
            {{/bar}}
        {{/foo}}
        """.trimIndent(),

            """
        {{#foo}}
            {{#bar}}
                {{#bat}}
                    <caret>
                    {{baz}}
                {{/bat}}
            {{/bar}}
        {{/foo}}
        """.trimIndent()
        )
    }

    @Test
    fun testNestedBlocks3() {
        doEnterTest(
            """
        {{#foo}}
            {{#bar}}
                {{#bat}}
                    {{baz}}<caret>
                {{/bat}}
            {{/bar}}
        {{/foo}}
        """.trimIndent(),

            """
        {{#foo}}
            {{#bar}}
                {{#bat}}
                    {{baz}}
                    <caret>
                {{/bat}}
            {{/bar}}
        {{/foo}}
        """.trimIndent()
        )
    }

    @Test
    fun testSimpleStacheInDiv1() {
        doEnterTest(
            """
        <div><caret>
            {{foo}}
        </div>
        """.trimIndent(),

            """
        <div>
            <caret>
            {{foo}}
        </div>
        """.trimIndent()
        )
    }

    @Test
    fun testSimpleStacheInDiv2() {
        doEnterTest(
            """
        <div>
            {{foo}}<caret>
        </div>
        """.trimIndent(),

            """
        <div>
            {{foo}}
            <caret>
        </div>
        """.trimIndent()
        )
    }

    @Test
    fun testSimpleStacheInDiv3() {
        doEnterTest(
            "<div>\n" +
                    "    {{foo}}<caret>",

            """
        <div>
            {{foo}}
            <caret>
            """.trimIndent()
        )
    }

    fun testMarkupInBlockStache1() {
        doEnterTest(
            """
        {{#foo}}
            <span></span><caret>
        {{/foo}}
        """.trimIndent(),

            """
        {{#foo}}
            <span></span>
            <caret>
        {{/foo}}
        """.trimIndent()
        )
    }

    fun testMarkupInBlockStache2() {
        doEnterTest(
            """
        {{#foo}}<caret>
            <span></span>
        {{/foo}}
        """.trimIndent(),

            """
        {{#foo}}
            <caret>
            <span></span>
        {{/foo}}
        """.trimIndent()
        )
    }

    @Ignore("not supported - broken hbs")
    fun ignore_testMarkupInBlockStache3() {
        doEnterTest(
            "{{#foo}}\n" +
                    "    <span></span><caret>",

            """
        {{#foo}}
            <span></span>
            <caret>
            """.trimIndent()
        )
    }

    fun testEmptyBlockInDiv1() {
        doEnterTest(
            """
        <div>
            {{#foo}}<caret>
            {{/foo}}
        </div>
        """.trimIndent(),

            """
        <div>
            {{#foo}}
                <caret>
            {{/foo}}
        </div>
        """.trimIndent()
        )
    }

    fun testEmptyBlockInDiv2() {
        doEnterTest(
            """
        <div>
            {{#foo}}<caret>{{/foo}}
        </div>
        """.trimIndent(),

            """
        <div>
            {{#foo}}
                <caret>
            {{/foo}}
        </div>
        """.trimIndent()
        )
    }

    fun testEmptyBlockInDiv3() {
        doEnterTest(
            """
        <div>
            {{#foo}}<caret>
        htmlPadding
        """.trimIndent(),

            """
        <div>
            {{#foo}}
                <caret>
        htmlPadding
        """.trimIndent()
        )
    }

    fun testEmptyBlockInDiv4() {
        doEnterTest(
            "<div>\n" +
                    "{{#foo}}<caret>{{/foo}}",

            """
        <div>
        {{#foo}}
            <caret>
        {{/foo}}
        """.trimIndent()
        )
    }

    fun testSimpleBlockInDiv1() {
        doEnterTest(
            """
        <div>
        {{#foo}}
        {{bar}}<caret>
        {{/foo}}
        </div>
        """.trimIndent(),

            """
        <div>
        {{#foo}}
        {{bar}}
            <caret>
        {{/foo}}
        </div>
        """.trimIndent()
        )
    }

    fun testSimpleBlockInDiv2() {
        doEnterTest(
            """
        <div>
            {{#foo}}
                {{bar}}<caret>
            {{/foo}}
        </div>
        """.trimIndent(),

            """
        <div>
            {{#foo}}
                {{bar}}
                <caret>
            {{/foo}}
        </div>
        """.trimIndent()
        )
    }

    fun testSimpleBlockInDiv3() {
        doEnterTest(
            """
        <div>
            {{#foo}}
                {{bar}}
            {{/foo}}<caret>
        </div>
        """.trimIndent(),

            """
        <div>
            {{#foo}}
                {{bar}}
            {{/foo}}
            <caret>
        </div>
        """.trimIndent()
        )
    }

    @Ignore("not supported - broken hbs")
    fun ignore_testSimpleBlockInDiv4() {
        doEnterTest(
            """
        <div>
        {{#foo}}
        {{bar}}<caret>
        """.trimIndent(),

            """
        <div>
        {{#foo}}
        {{bar}}
            <caret>
            """.trimIndent()
        )
    }

    @Ignore("not supported - broken hbs")
    fun ignore_testSimpleBlockInDiv5() {
        doEnterTest(
            """
        <div>
            {{#foo}}
                {{bar}}<caret>
        htmlPadding
        """.trimIndent(),

            """
        <div>
            {{#foo}}
                {{bar}}
                <caret>
        htmlPadding
        """.trimIndent()
        )
    }

    fun testSimpleBlockInDiv6() {
        doEnterTest(
            """
        <div>
            {{#foo}}
                {{bar}}
            {{/foo}}<caret>
            """.trimIndent(),

            """
        <div>
            {{#foo}}
                {{bar}}
            {{/foo}}
            <caret>
            """.trimIndent()
        )
    }

    @Ignore("not supported - broken hbs")
    fun ignore_testSimpleBlockInDiv7() {
        doEnterTest(
            """
        <div>
            {{#foo}}<caret>
                {{bar}}
            {{/foo}}
            """.trimIndent(),

            "<div>\n" +
                    "    {{#foo}}\n" +
                    "    <caret>\n" +  // NOTE: this is not ideal, but it's tough to get the formatting right when there's unclosed html elements
                    "        {{bar}}\n" +
                    "    {{/foo}}"
        )
    }

    fun testSimpleBlockInDiv8() {
        doEnterTest(
            """
        <div>
            {{#foo}}<caret>
                {{bar}}
            {{/foo}}
        </div>
        """.trimIndent(),

            """
        <div>
            {{#foo}}
                <caret>
                {{bar}}
            {{/foo}}
        </div>
        """.trimIndent()
        )
    }

    fun testAttributeStaches1() {
        doEnterTest(
            """
        <div {{foo}}><caret>
            <div class="{{bar}}">
                sweeet
            </div>
        </div>
        """.trimIndent(),

            """
        <div {{foo}}>
            <caret>
            <div class="{{bar}}">
                sweeet
            </div>
        </div>
        """.trimIndent()
        )
    }

    fun testAttributeStaches2() {
        doEnterTest(
            """
        <div {{foo}}>
            <div class="{{bar}}"><caret>
                sweeet
            </div>
        </div>
        """.trimIndent(),

            """
        <div {{foo}}>
            <div class="{{bar}}">
                <caret>
                sweeet
            </div>
        </div>
        """.trimIndent()
        )
    }

    fun testAttributeStaches3() {
        doEnterTest(
            """
        <div {{foo}}>
            <div class="{{bar}}">
                sweeet<caret>
            </div>
        </div>
        """.trimIndent(),

            """
        <div {{foo}}>
            <div class="{{bar}}">
                sweeet
                <caret>
            </div>
        </div>
        """.trimIndent()
        )
    }

    fun testAttributeStaches4() {
        doEnterTest(
            "<div {{foo}}><caret>",

            "<div {{foo}}>\n" +
                    "    <caret>"
        )
    }

    fun testAttributeStaches5() {
        doEnterTest(
            "<div {{foo}}>\n" +
                    "    <div class=\"{{bar}}\"><caret>",

            """
        <div {{foo}}>
            <div class="{{bar}}">
                <caret>
                """.trimIndent()
        )
    }

    fun testAttributeStaches6() {
        doEnterTest(
            """
        <div {{foo}}>
            <div class="{{bar}}">
                sweeet<caret>
                """.trimIndent(),

            """
        <div {{foo}}>
            <div class="{{bar}}">
                sweeet
                <caret>
                """.trimIndent()
        )
    }

    fun testMixedContentInDiv1() {
        doEnterTest(
            """
        <div>
            {{#foo}}
                <span class="{{bat}}">{{bar}}</span><caret>
            {{/foo}}
        </div>
        """.trimIndent(),

            """
        <div>
            {{#foo}}
                <span class="{{bat}}">{{bar}}</span>
                <caret>
            {{/foo}}
        </div>
        """.trimIndent()
        )
    }

    fun testMixedContentInDiv2() {
        doEnterTest(
            """
        <div>
            {{#foo}}<caret>
                <span class="{{bat}}">{{bar}}</span>
            {{/foo}}
        </div>
        """.trimIndent(),

            """
        <div>
            {{#foo}}
                <caret>
                <span class="{{bat}}">{{bar}}</span>
            {{/foo}}
        </div>
        """.trimIndent()
        )
    }

    fun testMixedContentInDiv3() {
        doEnterTest(
            """
        <div>
            {{#foo}}
                <span class="{{bat}}">{{bar}}</span><caret>
            {{/foo}}
        </div>
        """.trimIndent(),

            """
        <div>
            {{#foo}}
                <span class="{{bat}}">{{bar}}</span>
                <caret>
            {{/foo}}
        </div>
        """.trimIndent()
        )
    }

    fun testMixedContentInDiv4() {
        doEnterTest(
            """
        <div>
            {{#foo}}
                <span class="{{bat}}">{{bar}}</span>
            {{/foo}}<caret>
        </div>
        """.trimIndent(),

            """
        <div>
            {{#foo}}
                <span class="{{bat}}">{{bar}}</span>
            {{/foo}}
            <caret>
        </div>
        """.trimIndent()
        )
    }

    @Ignore("not supported - broken hbs")
    @Test
    fun ignore_testMixedContentInDiv5() {
        doEnterTest(
            """
        <div><caret>
            {{#foo}}
                <span class="{{bat}}">{{bar}}</span>
                """.trimIndent(),

            """
        <div>
            <caret>
            {{#foo}}
                <span class="{{bat}}">{{bar}}</span>
                """.trimIndent()
        )
    }

    fun testMixedContentInDiv6() {
        doEnterTest(
            """
        <div>
            {{#foo}}<caret>
                <span class="{{bat}}">{{bar}}</span>
                """.trimIndent(),

            """
        <div>
            {{#foo}}
                <caret>
                <span class="{{bat}}">{{bar}}</span>
                """.trimIndent()
        )
    }

    @Ignore("not supported - broken hbs")
    fun ignore_testMixedContentInDiv7() {
        doEnterTest(
            """
        <div>
            {{#foo}}
                <span class="{{bat}}">{{bar}}</span><caret>
                """.trimIndent(),

            """
        <div>
            {{#foo}}
                <span class="{{bat}}">{{bar}}</span>
                <caret>
                """.trimIndent()
        )
    }

    fun testMixedContentInDiv8() {
        doEnterTest(
            """
        <div>
            {{#foo}}
                <span class="{{bat}}">{{bar}}</span>
            {{/foo}}<caret>
            """.trimIndent(),

            """
        <div>
            {{#foo}}
                <span class="{{bat}}">{{bar}}</span>
            {{/foo}}
            <caret>
            """.trimIndent()
        )
    }

    @Ignore("not supported - broken hbs")
    fun ignore_testEmptyLinesAfterOpenBlock1() {
        doEnterTest(
            """
        {{#foo}}
            
            
            
            <caret>
            
        
            
        """.trimIndent(),

            """
        {{#foo}}
            
            
            
            
            <caret>
            
        
            
        """.trimIndent()
        )
    }

    @Ignore("not supported - broken hbs")
    fun ignore_testEmptyLinesAfterOpenBlock2() {
        doEnterTest(
            """
        {{#if}}
            
            
        {{else}}
            
            
            <caret>
            
            
        
            
            
        """.trimIndent(),

            """
        {{#if}}
            
            
        {{else}}
            
            
            
            <caret>
            
            
        
            
            
        """.trimIndent()
        )
    }

    fun testSimpleStacheInNestedDiv1() {
        doEnterTest(
            """
        {{#foo}}
            <div><caret>
                {{bar}}
            </div>
        {{/foo}}
        """.trimIndent(),

            """
        {{#foo}}
            <div>
                <caret>
                {{bar}}
            </div>
        {{/foo}}
        """.trimIndent()
        )
    }

    fun testSimpleStacheInNestedDiv2() {
        doEnterTest(
            """
        {{#foo}}
            <div>
                {{bar}}<caret>
            </div>
        {{/foo}}
        """.trimIndent(),

            """
        {{#foo}}
            <div>
                {{bar}}
                <caret>
            </div>
        {{/foo}}
        """.trimIndent()
        )
    }

    fun testBlockStacheInNestedDiv1() {
        doEnterTest(
            """
        {{#foo}}
            <div><caret>
                {{#bar}}
                    stuff
                {{/bar}}
            </div>
        {{/foo}}
        """.trimIndent(),

            """
        {{#foo}}
            <div>
                <caret>
                {{#bar}}
                    stuff
                {{/bar}}
            </div>
        {{/foo}}
        """.trimIndent()
        )
    }

    fun testBlockStacheInNestedDiv2() {
        doEnterTest(
            """
        {{#foo}}
            <div>
                {{#bar}}<caret>
                    stuff
                {{/bar}}
            </div>
        {{/foo}}
        """.trimIndent(),

            """
        {{#foo}}
            <div>
                {{#bar}}
                    <caret>
                    stuff
                {{/bar}}
            </div>
        {{/foo}}
        """.trimIndent()
        )
    }

    fun testBlockStacheInNestedDiv3() {
        doEnterTest(
            """
        {{#foo}}
            <div>
                {{#bar}}
                    stuff<caret>
                {{/bar}}
            </div>
        {{/foo}}
        """.trimIndent(),

            """
        {{#foo}}
            <div>
                {{#bar}}
                    stuff
                    <caret>
                {{/bar}}
            </div>
        {{/foo}}
        """.trimIndent()
        )
    }

    fun testBlockStacheInNestedDiv4() {
        doEnterTest(
            """
        {{#foo}}
            <div>
                {{#bar}}
                    stuff
                {{/bar}}<caret>
            </div>
        {{/foo}}
        """.trimIndent(),

            """
        {{#foo}}
            <div>
                {{#bar}}
                    stuff
                {{/bar}}
                <caret>
            </div>
        {{/foo}}
        """.trimIndent()
        )
    }
}