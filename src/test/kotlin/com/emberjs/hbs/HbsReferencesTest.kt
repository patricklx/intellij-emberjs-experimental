package com.emberjs.hbs

import com.dmarcotte.handlebars.file.HbFileType
import com.dmarcotte.handlebars.parsing.HbTokenTypes
import com.dmarcotte.handlebars.psi.HbHash
import com.dmarcotte.handlebars.psi.HbParam
import com.dmarcotte.handlebars.psi.impl.HbPathImpl
import com.emberjs.psi.EmberNamedAttribute
import com.emberjs.psi.EmberNamedElement
import com.intellij.lang.Language
import com.intellij.lang.javascript.psi.JSField
import com.intellij.lang.javascript.psi.ecma6.TypeScriptPropertySignature
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.psi.PsiElement
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.rd.util.assert
import org.junit.Test

class HbsReferencesTest : BasePlatformTestCase() {
    @Test
    fun testLocalFromMustach() {
        val hbs = """
            {{#each this.items as |item index|}}
                {{item}}
                {{item.a}}
                {{index}}
                {{in-helper (fn item)}}
                <item/>
            {{/each}}
        """.trimIndent()
        myFixture.configureByText(HbFileType.INSTANCE, hbs)
        val element = PsiTreeUtil.collectElements(myFixture.file, { it is HbPathImpl })
        val resolvedItem = element.find { it.text == "item" }?.children?.get(0)?.references?.find { it is HbsLocalReference }?.resolve()
        val resolvedItemA = element.find { it.text == "item.a" }?.children?.get(0)?.references?.find { it is HbsLocalReference }?.resolve()
        val resolvedIndex = element.find { it.text == "index" }?.children?.get(0)?.references?.find { it is HbsLocalReference }?.resolve()
        val resolvedItemInFn = element.find { it.text == "item" }?.children?.get(0)?.references?.find { it is HbsLocalReference }?.resolve()
        val htmlView = myFixture.file.viewProvider.getPsi(Language.findLanguageByID("HTML")!!)
        val declaration = (PsiTreeUtil.collectElements(htmlView, { it is HtmlTag && it.name == "item" }).first() as HtmlTag).references.lastOrNull()?.resolve()
        assert(declaration.elementType == HbTokenTypes.ID && declaration?.text == "item")
        assert(resolvedItem != null)
        assert(resolvedItemA != null)
        assert(resolvedIndex != null)
        assert(resolvedItemInFn != null)
    }

    @Test
    fun testLocalFromAngleBracket() {
        val hbs = """
            <MyComponent as |item index|>
                {{item}}
                {{item.a}}
                {{index}}
                {{in-helper (fn item)}}
            </MyComponent>
        """.trimIndent()
        myFixture.addFileToProject("app/components/my-component/template.hbs", "{{yield x y}}")
        myFixture.addFileToProject("app/routes/index/template.hbs", hbs)
        myFixture.addFileToProject("package.json", "{\"keywords\": [\"ember\"]}")
        myFixture.addFileToProject(".ember-cli", "")
        myFixture.configureByFile("app/routes/index/template.hbs")
        val element = PsiTreeUtil.collectElements(myFixture.file, { it is HbPathImpl })
        val resolvedItem = element.find { it.text == "item" }?.children?.get(0)?.references?.find { it is HbsLocalReference }?.resolve()
        val resolvedItemAParent = element.find { it.text == "item.a" }?.children?.get(0)?.references?.find { it is HbsLocalReference }?.resolve()
        val resolvedIndex = element.find { it.text == "index" }?.children?.get(0)?.references?.find { it is HbsLocalReference }?.resolve()
        val resolvedItemInFn = element.find { it.text == "item" }?.children?.get(0)?.references?.find { it is HbsLocalReference }?.resolve()
        assert(resolvedItem != null)
        assert(resolvedItemAParent != null)
        assert(resolvedIndex != null)
        assert(resolvedItemInFn != null)
    }

    @Test
    fun testToTsFile() {
        val hbs = """
            {{this.a}}
            {{this.x.b}}
            {{this.y.b}}
            {{x}}
            {{in-helper (fn this.x)}}
        """.trimIndent()
        val ts = """
            export default class extends Component {
                @tracked a;
                x: { b: string };
                /**
                 * @type {{b: string}}
                 */
                y;
            }
        """.trimIndent()
        myFixture.addFileToProject("component.ts", ts)
        myFixture.configureByText("template.hbs", hbs)
        val element = PsiTreeUtil.collectElements(myFixture.file, { it.elementType == HbTokenTypes.ID })
        val resolvedA = element.find { it.parent.text == "this.a" }!!.parent.children.map { it.references }
                .filter { it.isNotEmpty() }.map { it.first().resolve() }
        val resolvedXB = element.find { it.parent.text == "this.x.b" }!!.parent.children.map { it.references }
                .filter { it.isNotEmpty() }.map { it.first().resolve() }
        val resolvedYB = element.find { it.parent.text == "this.y.b" }!!.parent.children.map { it.references }
                .filter { it.isNotEmpty() }.map { it.first().resolve() }
        val notResolvedX = element.find { it.parent.text == "x" }!!.parent.children.map { it.references }
                .filter { it.isNotEmpty() }.map { it.first().resolve() }
        val resolvedXInHelper = element.findLast { it.parent.text == "this.x" }!!.parent.children.map { it.references }
                .filter { it.isNotEmpty() }.map { it.first().resolve() }
        assert(resolvedA.first() is JSClass && resolvedA[1] is JSField)
        assert(resolvedXB[0] is JSClass && resolvedXB[1] is JSField && resolvedXB[2] is TypeScriptPropertySignature)
        assert(resolvedYB.first() is JSClass && resolvedA[1] is JSField /*&& need to resolve doc*/)
        assert(resolvedXInHelper.first() is JSClass && resolvedA[1] is JSField)
        assert(notResolvedX.isEmpty())
    }

    @Test
    fun testToJsFile() {
        val hbs = """
            {{this.a}}
            {{this.x.b}}
            {{this.y.b}}
            {{x}}
            {{in-helper (fn this.x)}}
        """.trimIndent()
        val js = """
            export default class extends Component {
                @tracked a;
                /**
                 * @type {{b: string}}
                 */
                x: { b: string };
                /**
                 * @type {{b: string}}
                 */
                y;
            }
        """.trimIndent()
        myFixture.addFileToProject("component.js", js)
        myFixture.configureByText("template.hbs", hbs)
        val element = PsiTreeUtil.collectElements(myFixture.file, { it.elementType == HbTokenTypes.ID })
        val resolvedA = element.find { it.parent.text == "this.a" }!!.parent.children.map { it.references }
                .filter { it.isNotEmpty() }.map { it.first().resolve() }
        val resolvedXB = element.find { it.parent.text == "this.x.b" }!!.parent.children.map { it.references }
                .filter { it.isNotEmpty() }.map { it.first().resolve() }
        val resolvedYB = element.find { it.parent.text == "this.y.b" }!!.parent.children.map { it.references }
                .filter { it.isNotEmpty() }.map { it.first().resolve() }
        val notResolvedX = element.find { it.parent.text == "x" }!!.parent.children.map { it.references }
                .filter { it.isNotEmpty() }.map { it.first().resolve() }
        val resolvedXInHelper = element.findLast { it.parent.text == "this.x" }!!.parent.children.map { it.references }
                .filter { it.isNotEmpty() }.map { it.first().resolve() }
        assert(resolvedA.first() is JSClass && resolvedA[1] is JSField)
        assert(resolvedXB[0] is JSClass && resolvedXB[1] is JSField /*&& resolvedXB[2] is TypeScriptPropertySignature*/)
        assert(resolvedYB.first() is JSClass && resolvedA[1] is JSField /*&& need to resolve doc*/)
        assert(resolvedXInHelper.first() is JSClass && resolvedA[1] is JSField)
        assert(notResolvedX.isEmpty())
    }

    @Test
    fun testLetHelper() {
        val hbs = """
            {{#let this as |self|}}
                {{self.a}}
                {{self.x.b}}
                {{self.y.b}}
                {{x}}
                {{in-helper (fn self.x)}}
            {{/let}} 
        """.trimIndent()
        val js = """
            export default class extends Component {
                @tracked a;
                /**
                 * @type {{b: string}}
                 */
                x: { b: string };
                /**
                 * @type {{b: string}}
                 */
                y;
            }
        """.trimIndent()
        myFixture.addFileToProject("component.js", js)
        myFixture.configureByText("template.hbs", hbs)
        val element = PsiTreeUtil.collectElements(myFixture.file, { it.elementType == HbTokenTypes.ID })
        val resolvedBlock = element.find { it.parent.text == "self" }!!.parent.references.first()
        val resolvedA = element.find { it.parent.text == "self.a" }!!.parent.children.map { it.references }
                .filter { it.isNotEmpty() }.map { it.first().resolve() }
        val resolvedXB = element.find { it.parent.text == "self.x.b" }!!.parent.children.map { it.references }
                .filter { it.isNotEmpty() }.map { it.first().resolve() }
        val resolvedYB = element.find { it.parent.text == "self.y.b" }!!.parent.children.map { it.references }
                .filter { it.isNotEmpty() }.map { it.first().resolve() }
        val notResolvedX = element.find { it.parent.text == "x" }!!.parent.children.map { it.references }
                .filter { it.isNotEmpty() }.map { it.first().resolve() }
        val resolvedXInHelper = element.findLast { it.parent.text == "self.x" }!!.parent.children.map { it.references }
                .filter { it.isNotEmpty() }.map { it.first().resolve() }

        assert(resolvedBlock is HbsLocalRenameReference)
        val blockId = element.find { it.text == "self" }
        assert(resolvedBlock.resolve() == blockId)
        assert(resolvedA.first() is PsiElement && resolvedA[1] is JSField)
        assert(resolvedXB[0] is PsiElement && resolvedXB[1] is JSField /*&& resolvedXB[2] is TypeScriptPropertySignature*/)
        assert(resolvedYB.first() is PsiElement && resolvedA[1] is JSField /*&& need to resolve doc*/)
        assert(resolvedXInHelper.first() is PsiElement && resolvedA[1] is JSField)
        assert(notResolvedX.isEmpty())
    }

    @Test
    fun testLetHelper2() {
        val hbs = """
            {{#let this.x as |self|}}
                {{self.b}}
            {{/let}} 
        """.trimIndent()
        val js = """
            export default class extends Component {
                @tracked a;
                /**
                 * @type {{b: string}}
                 */
                x: { b: string };
                /**
                 * @type {{b: string}}
                 */
                y;
            }
        """.trimIndent()
        myFixture.addFileToProject("component.js", js)
        myFixture.configureByText("template.hbs", hbs)
        val element = PsiTreeUtil.collectElements(myFixture.file, { it.elementType == HbTokenTypes.ID })
        val resolvedBlock = element.find { it.parent.text == "self" }!!.parent.references.first()
        val resolvedA = element.find { it.parent.text == "self.b" }!!.parent.children.map { it.references }
                .filter { it.isNotEmpty() }.map { it.first().resolve() }

        assert(resolvedBlock is HbsLocalRenameReference)
        val blockId = element.find { it.text == "self" }
        assert((resolvedBlock.resolve() as EmberNamedElement).target == blockId)
        assert(resolvedA.first() is PsiElement && resolvedA[1] is TypeScriptPropertySignature)
    }

        fun testEachHelper() {
                val hbs = """
            {{#each this.items as |item|}}
                {{item.a}}
            {{/each}} 
        """.trimIndent()
                val ts = """
            export default class extends Component {
                items: {a: string}[];
            }
        """.trimIndent()
                myFixture.addFileToProject("component.ts", ts)
                myFixture.configureByText("template.hbs", hbs)
                val element = PsiTreeUtil.collectElements(myFixture.file, { it.elementType == HbTokenTypes.ID })
                val resolvedA = element.find { it.parent.text == "item.a" }!!.parent.children.map { it.references }
                        .filter { it.isNotEmpty() }.map { it.first().resolve() }
                assert(resolvedA.first() is PsiElement && resolvedA[1] is JSField)
        }

    @Test
    fun testHashHelper() {
        val hbs = """
            {{#let (hash name='Sarah' title=office) as |item|}}
                {{item.name}}
            {{/each}} 
        """.trimIndent()
        myFixture.configureByText("template.hbs", hbs)
        val element = PsiTreeUtil.collectElements(myFixture.file, { it.elementType == HbTokenTypes.ID })
        val resolvedA = element.find { it.parent.text == "item.name" }!!.parent.children.map { it.references }
                .filter { it.isNotEmpty() }.map { it.first().resolve() }
        assert(resolvedA.first() is PsiElement && resolvedA[1]?.parent is HbParam)
    }

    @Test
    fun testBlockToYieldHashRef() {
        val hbsWithYield = """
            {{#let (hash name='Sarah' title=office) as |item|}}
                {{yield x item}}
            {{/each}} 
        """.trimIndent()
        val hbs = """
            <MyComponent as |x item|>
                {{item.name}}
            </MyComponent>
        """.trimIndent()
        myFixture.addFileToProject("app/components/my-component/template.hbs", hbsWithYield)
        myFixture.addFileToProject("app/routes/index/template.hbs", hbs)
        myFixture.addFileToProject("package.json", "{\"keywords\": [\"ember\"]}")
        myFixture.addFileToProject(".ember-cli", "")
        myFixture.configureByFile("app/routes/index/template.hbs")
        val element = PsiTreeUtil.collectElements(myFixture.file, { it.elementType == HbTokenTypes.ID })
        val resolvedA = element.find { it.parent.text == "item.name" }!!.parent.children.map { it.references }
                .map { it.firstOrNull()?.resolve() }
        assert(resolvedA.first() is EmberNamedElement && resolvedA.last() is HbHash)
    }

    @Test
    fun testHtmlBlockToYieldHashRef() {
        val hbsWithYield = """
            {{#let (hash name='Sarah' title=office) as |item|}}
                {{yield item}}
            {{/each}} 
        """.trimIndent()
        val hbs = """
            <MyComponent as |header|>
                <header></header>
                <header.name />
            </MyComponent>
        """.trimIndent()
        myFixture.addFileToProject("app/components/my-component/template.hbs", hbsWithYield)
        myFixture.addFileToProject("app/routes/index/template.hbs", hbs)
        myFixture.addFileToProject("package.json", "{\"keywords\": [\"ember\"]}")
        myFixture.configureByFile("app/routes/index/template.hbs")
        val htmlView = myFixture.file.viewProvider.getPsi(Language.findLanguageByID("HTML")!!)
        val element = PsiTreeUtil.collectElements(htmlView, { it.text == "header" }).first()
        val resolvedA = element.parent.references.lastOrNull()?.resolve()
        assert(resolvedA is EmberNamedAttribute)

        val elementWithDotName = PsiTreeUtil.collectElements(htmlView, { it.text == "header.name" }).first()
        val resolvedItem = elementWithDotName.parent.references[0].resolve()
        assert(resolvedItem is EmberNamedAttribute)

        val resolvedItemNamed = elementWithDotName.parent.references[1].resolve()
        assert((resolvedItemNamed as EmberNamedElement).target is HbParam)
        assert((resolvedItemNamed).target is HbParam && resolvedItemNamed.text == "'Sarah'")
    }
}
