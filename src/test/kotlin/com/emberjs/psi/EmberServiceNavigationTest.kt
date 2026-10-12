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

    private fun targetDescriptions(): List<String> =
        GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, myFixture.caretOffset)
            .map { "${it.javaClass.simpleName}:${(it as? com.intellij.psi.PsiNamedElement)?.name}" }

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

    /** Mirrors how ember-source's stable types register the router service. */
    private fun addEmberServiceTypes() {
        myFixture.addFileToProject("types/ember/service.d.ts", """
            declare module '@ember/service' {
              export default class Service {}
              export function service(name?: string): any;
              export interface Registry extends Record<string, object | undefined> {}
            }
        """.trimIndent())
        myFixture.addFileToProject("types/ember/routing/router-service.d.ts", """
            declare module '@ember/routing/router-service' {
              class RouterService { currentRouteName: string; }
              export { RouterService as default };
            }
        """.trimIndent())
        myFixture.addFileToProject("types/ember/routing/service-ext.d.ts", """
            import '@ember/service';
            import type RouterService from '@ember/routing/router-service';
            declare module '@ember/service' {
              export interface Registry {
                router: RouterService;
              }
            }
        """.trimIndent())
    }

    fun testBuiltinRouterServiceResolvesThroughTheServiceRegistry() {
        addEmberServiceTypes()
        assertEquals(listOf("types/ember/routing/router-service.d.ts"), targetsAt("app/components/c4.js", sessionComponent, "router;"))
        assertEquals(listOf("TypeScriptClassImpl:RouterService"), targetDescriptions())
    }

    fun testStringArgumentResolvesThroughTheServiceRegistry() {
        addEmberServiceTypes()
        assertEquals(listOf("types/ember/routing/router-service.d.ts"), targetsAt("app/components/c6.js", """
            import Component from '@ember/component';
            import { inject as service } from '@ember/service';
            export default Component.extend({ r: service('router') });
        """, "router'"))
    }

    fun testDasherizedRegistryEntry() {
        addEmberServiceTypes()
        myFixture.addFileToProject("types/flash-messages.d.ts", """
            declare module 'ember-cli-flash/services/flash-messages' {
              export default class FlashMessagesService { success(message: string): void; }
            }
            declare module '@ember/service' {
              import type FlashMessagesService from 'ember-cli-flash/services/flash-messages';
              export interface Registry { 'flash-messages': FlashMessagesService; }
            }
        """.trimIndent())
        assertEquals(listOf("types/flash-messages.d.ts"), targetsAt("app/components/c7.js", """
            import Component from '@glimmer/component';
            import { service } from '@ember/service';
            export default class C extends Component {
              @service flashMessages;
            }
        """, "flashMessages;"))
    }

    fun testUnregisteredServiceHasNoTarget() {
        addEmberServiceTypes()
        assertEquals(emptyList<String>(), targetsAt("app/components/c8.js", """
            import Component from '@glimmer/component';
            import { service } from '@ember/service';
            export default class C extends Component {
              @service doesNotExist;
            }
        """, "doesNotExist;"))
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

    private fun definitionOf(path: String): String? {
        val file = psiManager.findFile(myFixture.findFileInTempDir(path))!!
        return EmberNavigationTargets.defaultExportDefinition(file)?.let {
            "${it.containingFile?.virtualFile?.path?.substringAfter("/src/")}#${(it as? com.intellij.psi.PsiNamedElement)?.name}"
        }
    }

    /** ember-data 4.12: app/services/store.js -> 'ember-data/store' -> './-private' -> class Store */
    fun testFollowsEmberDataStoreReExportChain() {
        myFixture.addFileToProject("node_modules/ember-data/package.json", """{ "name": "ember-data", "keywords": ["ember-addon"] }""")
        myFixture.addFileToProject("node_modules/ember-data/app/services/store.js", "export { default } from 'ember-data/store';")
        myFixture.addFileToProject("node_modules/ember-data/addon/store.ts", "export { Store as default } from './-private';")
        myFixture.addFileToProject("node_modules/ember-data/addon/-private/index.ts", "export class Store { findRecord() {} }")
        // the package's main module; must not be mistaken for 'ember-data/store'
        myFixture.addFileToProject("node_modules/ember-data/addon/index.js", "export { default as Model } from './model';\nexport default {};")
        assertEquals("node_modules/ember-data/addon/-private/index.ts#Store", definitionOf("node_modules/ember-data/app/services/store.js"))
    }

    fun testFollowsAddonAppReExportToItsServiceClass() {
        myFixture.addFileToProject("node_modules/ember-simple-auth/package.json", """{ "name": "ember-simple-auth", "keywords": ["ember-addon"] }""")
        myFixture.addFileToProject("node_modules/ember-simple-auth/app/services/session.js", "export { default } from 'ember-simple-auth/services/session';")
        myFixture.addFileToProject("node_modules/ember-simple-auth/addon/services/session.js", "import Service from '@ember/service';\nexport default class SessionService extends Service {}")
        assertEquals("node_modules/ember-simple-auth/addon/services/session.js#SessionService", definitionOf("node_modules/ember-simple-auth/app/services/session.js"))
    }

    fun testAppServiceDefinitionIsItsClass() {
        assertEquals("app/services/session.js#SessionService", definitionOf("app/services/session.js"))
    }

    fun testUnresolvableReExportGivesNull() {
        myFixture.addFileToProject("app/services/broken.js", "export { default } from 'does-not-exist/services/broken';")
        assertNull(definitionOf("app/services/broken.js"))
    }
}

