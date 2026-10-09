package com.emberjs.psi

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Cmd+click on an injected service should navigate to the service's definition.
 */
class EmberServiceNavigationTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject("package.json", """{ "name": "my-app", "devDependencies": { "ember-cli": "*" } }""")
        myFixture.addFileToProject("app/services/session.js", """
            import Service from '@ember/service';
            export default class SessionService extends Service {
              isAuthenticated = false;
            }
        """.trimIndent())
    }

    private fun targetFileNames(): List<String> {
        val targets = GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, myFixture.caretOffset)
        return targets.mapNotNull { (it as? PsiFile ?: it.containingFile)?.virtualFile?.path?.substringAfter("/src/") }
    }

    fun testStringArgument() {
        val file = myFixture.addFileToProject("app/components/foo.js", """
            import Component from '@glimmer/component';
            import { service } from '@ember/service';
            export default class Foo extends Component {
              @service('session') session;
            }
        """.trimIndent())
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf("session') session") + 3)
        assertEquals(listOf("app/services/session.js"), targetFileNames())
    }

    fun testStringArgumentLegacyInject() {
        val file = myFixture.addFileToProject("app/components/bar.js", """
            import Component from '@ember/component';
            import { inject as service } from '@ember/service';
            export default Component.extend({
              session: service('session'),
            });
        """.trimIndent())
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf("'session'") + 3)
        assertEquals(listOf("app/services/session.js"), targetFileNames())
    }

    private fun targetsAt(path: String, text: String, caretBefore: String): List<String> {
        val file = myFixture.addFileToProject(path, text.trimIndent())
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf(caretBefore) + 1)
        return targetFileNames()
    }

    fun testBareDecoratorField() {
        assertEquals(listOf("app/services/session.js"), targetsAt("app/components/baz.js", """
            import Component from '@glimmer/component';
            import { service } from '@ember/service';
            export default class Baz extends Component {
              @service session;
            }
        """, "session;"))
    }

    fun testDecoratorWithNameUsesTheArgument() {
        assertEquals(listOf("app/services/session.js"), targetsAt("app/components/named.js", """
            import Component from '@glimmer/component';
            import { service } from '@ember/service';
            export default class Named extends Component {
              @service('session') mySession;
            }
        """, "mySession;"))
    }

    fun testCamelCaseFieldIsDasherized() {
        myFixture.addFileToProject("app/services/current-user.js", "import Service from '@ember/service';\nexport default class CurrentUserService extends Service {}")
        assertEquals(listOf("app/services/current-user.js"), targetsAt("app/components/camel.js", """
            import Component from '@glimmer/component';
            import { inject as service } from '@ember/service';
            export default class Camel extends Component {
              @service currentUser;
            }
        """, "currentUser;"))
    }

    fun testInjectDecorator() {
        assertEquals(listOf("app/services/session.js"), targetsAt("app/components/inj.js", """
            import Component from '@glimmer/component';
            import { inject } from '@ember/service';
            export default class Inj extends Component {
              @inject session;
            }
        """, "session;"))
    }

    fun testTypedDecoratorField() {
        assertEquals(listOf("app/services/session.js"), targetsAt("app/components/qux.ts", """
            import Component from '@glimmer/component';
            import { service } from '@ember/service';
            import type SessionService from 'my-app/services/session';
            export default class Qux extends Component {
              @service declare session: SessionService;
            }
        """, "session:"))
    }

    fun testOtherDecoratorsAreIgnored() {
        assertFalse(targetsAt("app/components/tracked.js", """
            import Component from '@glimmer/component';
            import { tracked } from '@glimmer/tracking';
            export default class Tracked extends Component {
              @tracked session;
            }
        """, "session;").contains("app/services/session.js"))
    }
}

