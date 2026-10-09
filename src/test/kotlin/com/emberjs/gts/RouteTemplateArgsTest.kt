package com.emberjs.gts

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.lang.javascript.psi.ecmal4.JSClass
import com.intellij.lang.javascript.psi.JSFunction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Route templates (app/templates/<route>.gjs/.gts, e.g. `RouteTemplate(<template>...</template>)` from
 * ember-route-template) receive `@controller` and `@model`.
 */
class RouteTemplateArgsTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("package.json", """{ "name": "my-app", "devDependencies": { "ember-cli": "*" } }""")
        myFixture.addFileToProject("app/controllers/klant/opdracht.js", """
            import Controller from '@ember/controller';
            export default class KlantOpdrachtController extends Controller {
              error = null;
              resetError() {}
            }
        """.trimIndent())
        myFixture.addFileToProject("app/routes/klant/opdracht.js", """
            import Route from '@ember/routing/route';
            export default class KlantOpdrachtRoute extends Route {
              model(params) { return { name: 'x' }; }
            }
        """.trimIndent())
    }

    private val template = """
        import RouteTemplate from 'ember-route-template';
        import { on } from '@ember/modifier';
        export default RouteTemplate(<template>
          {{@controller.error}}
          <button {{on "click" @controller.resetError}}></button>
          {{@controller.model.name}}
          {{@model.name}}
        </template>);
    """.trimIndent()

    private fun target(ext: String, caretAfter: String): PsiElement? {
        val file = myFixture.addFileToProject("app/templates/klant/opdracht.$ext", template)
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf(caretAfter) + caretAfter.length - 1)
        return GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, myFixture.caretOffset).firstOrNull()
    }

    private fun describe(e: PsiElement?): String? = e?.let {
        val named = it as? PsiNamedElement ?: PsiTreeUtil.getParentOfType(it, PsiNamedElement::class.java, false)
        "${it.containingFile?.virtualFile?.path?.substringAfter("/src/")}#${named?.name}"
    }

    fun testControllerResolvesToTheRoutesController() {
        val t = target("gjs", "{{@controller")
        assertTrue(describe(t), t is JSClass || PsiTreeUtil.getParentOfType(t, JSClass::class.java, false) != null)
        assertEquals("app/controllers/klant/opdracht.js#KlantOpdrachtController", describe(t))
    }

    fun testControllerPropertyResolvesToTheField() {
        assertEquals("app/controllers/klant/opdracht.js#error", describe(target("gjs", "@controller.error")))
    }

    fun testControllerActionResolvesToTheMethod() {
        assertEquals("app/controllers/klant/opdracht.js#resetError", describe(target("gjs", "@controller.resetError")))
    }

    fun testModelResolvesToTheRouteModelHook() {
        val t = target("gjs", "{{@model")
        assertTrue(describe(t), t is JSFunction || PsiTreeUtil.getParentOfType(t, JSFunction::class.java, false) != null)
        assertEquals("app/routes/klant/opdracht.js#model", describe(t))
    }

    fun testControllerModelFallsBackToTheRouteModelHook() {
        assertEquals("app/routes/klant/opdracht.js#model", describe(target("gjs", "@controller.model")))
    }

    fun testWorksInGtsToo() {
        assertEquals("app/controllers/klant/opdracht.js#error", describe(target("gts", "@controller.error")))
    }

    fun testClassicHbsRouteTemplate() {
        val file = myFixture.addFileToProject("app/templates/klant/opdracht.hbs", "{{@controller.error}} {{@model.name}}")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf("error") + 1)
        assertEquals("app/controllers/klant/opdracht.js#error", describe(GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, myFixture.caretOffset).firstOrNull()))
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf("@model") + 2)
        assertEquals("app/routes/klant/opdracht.js#model", describe(GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, myFixture.caretOffset).firstOrNull()))
    }

    fun testComponentArgNamedControllerIsNotARouteController() {
        val file = myFixture.addFileToProject("app/components/thing.gjs", """
            import Component from '@glimmer/component';
            export default class Thing extends Component {
              <template>{{@controller.error}}</template>
            }
        """.trimIndent())
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf("@controller") + 2)
        val t = GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, myFixture.caretOffset).firstOrNull()
        assertFalse(describe(t), describe(t)?.startsWith("app/controllers/") == true)
    }

    fun testControllerDeclaredModelWins() {
        myFixture.addFileToProject("app/controllers/declared.js", "import Controller from '@ember/controller';\nexport default class DeclaredController extends Controller { model; }")
        myFixture.addFileToProject("app/routes/declared.js", "import Route from '@ember/routing/route';\nexport default class DeclaredRoute extends Route { model() { return 1; } }")
        val file = myFixture.addFileToProject("app/templates/declared.gjs", "import RouteTemplate from 'ember-route-template';\nexport default RouteTemplate(<template>{{@controller.model}}</template>);")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf("model}}") + 1)
        assertEquals("app/controllers/declared.js#model", describe(GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, myFixture.caretOffset).firstOrNull()))
    }

    fun testCompletionAfterControllerDot() {
        val file = myFixture.addFileToProject("app/templates/klant/opdracht.gjs", "import RouteTemplate from 'ember-route-template';\nexport default RouteTemplate(<template>{{@controller.}}</template>);")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf("@controller.") + "@controller.".length)
        myFixture.completeBasic()
        assertEquals(listOf("error", "resetError"), myFixture.lookupElementStrings.orEmpty().sorted())
    }

    fun testCompletionAfterAt() {
        val file = myFixture.addFileToProject("app/templates/klant/opdracht.gjs", "import RouteTemplate from 'ember-route-template';\nexport default RouteTemplate(<template>{{@}}</template>);")
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf("{{@") + 3)
        myFixture.completeBasic()
        val items = myFixture.lookupElementStrings.orEmpty()
        assertTrue(items.toString(), "controller" in items && "model" in items)
    }

    fun testFindUsagesOfControllerFieldIncludesTemplate() {
        myFixture.addFileToProject("app/templates/klant/opdracht.gjs", template)
        val controller = myFixture.findFileInTempDir("app/controllers/klant/opdracht.js")
        myFixture.configureFromExistingVirtualFile(controller)
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("error = null") + 1)
        val usages = myFixture.findUsages(myFixture.elementAtCaret)
        assertTrue(usages.toString(), usages.any { it.file?.name == "opdracht.gjs" && it.element?.text == "error" })
    }
}
