package com.emberjs.psi

import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.emberjs.navigation.EmberNavigationTargets
import com.intellij.openapi.vfs.VirtualFile
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

    private fun addAddon(root: String, name: String, vararg files: String): List<VirtualFile> {
        myFixture.addFileToProject("$root/package.json", """{ "name": "$name", "keywords": ["ember-addon"] }""")
        return files.map { myFixture.addFileToProject("$root/$it", "export default class {}").virtualFile }
    }

    // The light test fixture doesn't index node_modules like a real project does, so these feed the
    // candidates the index would return straight into the narrowing step.
    private fun narrowed(candidates: List<VirtualFile>): List<String> {
        val context = myFixture.addFileToProject("app/components/ctx.js", "").virtualFile
        return EmberNavigationTargets.preferResolvedByApp(context, candidates).map { it.path.substringAfter("/src/") }
    }

    private val sessionComponent = """
        import Component from '@glimmer/component';
        import { service } from '@ember/service';
        export default class C extends Component {
          @service session;
          @service cookies;
          @service store;
          @service router;
        }
    """

    fun testAppServiceWinsOverAddon() {
        addAddon("node_modules/ember-simple-auth", "ember-simple-auth", "addon/services/session.js", "app/services/session.js")
        assertEquals(listOf("app/services/session.js"), targetsAt("app/components/c1.js", sessionComponent, "session;"))
    }

    fun testAddonImplementationWinsOverAppReExport() {
        val files = addAddon("node_modules/ember-cookies", "ember-cookies", "addon/services/cookies.js", "app/services/cookies.js")
        assertEquals(listOf("node_modules/ember-cookies/addon/services/cookies.js"), narrowed(files))
    }

    fun testCopyInstalledByTheAppWinsOverOtherCopies() {
        val installed = addAddon("node_modules/ember-data", "ember-data", "app/services/store.js")
        val nested = addAddon("node_modules/other-addon/node_modules/ember-data", "ember-data", "app/services/store.js")
        val scoped = addAddon("node_modules/@scope/fixtures/app/services", "fixture", "store.js")
        assertEquals(listOf("node_modules/ember-data/app/services/store.js"), narrowed(nested + scoped + installed))
    }

    fun testAppFileWinsOverNodeModules() {
        val addon = addAddon("node_modules/ember-simple-auth", "ember-simple-auth", "addon/services/session.js")
        val app = myFixture.findFileInTempDir("app/services/session.js")
        assertEquals(listOf("app/services/session.js"), narrowed(addon + app))
    }

    fun testBuiltinRouterServiceFallsBackToEmberSource() {
        myFixture.addFileToProject("node_modules/ember-source/package.json", """{ "name": "ember-source" }""")
        myFixture.addFileToProject("node_modules/ember-source/dist/packages/@ember/-internals/routing/lib/services/router.js", "export default class RouterService {}")
        assertEquals(listOf("node_modules/ember-source/dist/packages/@ember/-internals/routing/lib/services/router.js"), targetsAt("app/components/c4.js", sessionComponent, "router;"))
    }

    fun testStringArgumentAlsoPrefersTheAppCopy() {
        addAddon("node_modules/ember-simple-auth", "ember-simple-auth", "addon/services/session.js", "app/services/session.js")
        assertEquals(listOf("app/services/session.js"), targetsAt("app/components/c5.js", """
            import Component from '@glimmer/component';
            import { service } from '@ember/service';
            export default class C extends Component {
              @service('session') mySession;
            }
        """, "session')"))
    }
}

