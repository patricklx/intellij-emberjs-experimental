package com.emberjs.resolver

import com.intellij.lang.ecmascript6.psi.ES6ExportDeclaration
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EmberModuleReferenceContributorTest : BasePlatformTestCase() {

    private fun fromClauseTargets(path: String): List<String> {
        val file = myFixture.findFileInTempDir(path).let { psiManager.findFile(it)!! }
        val export = PsiTreeUtil.findChildOfType(file, ES6ExportDeclaration::class.java)!!
        return export.fromClause!!.references
                .flatMap { ref -> (ref as? PsiPolyVariantReference)?.multiResolve(false)?.mapNotNull { it.element } ?: listOfNotNull(ref.resolve()) }
                .mapNotNull { it.containingFile?.virtualFile?.path?.substringAfter("/src/") }
                .filter { it.endsWith(".js") }
                .distinct()
    }

    /**
     * A classic addon's app/ folder re-exports its addon/ implementation. `<addon-name>/...` is served from addon/
     * only; app/ belongs to the consuming app's namespace. Resolving it to app/ made every re-export resolve to
     * itself, a cycle the IDE reports as a non-idempotent resolve ("1 != 2").
     */
    fun testAddonNameImportResolvesToAddonFolderNotItsAppReExport() {
        myFixture.addFileToProject("package.json", """{ "name": "my-addon", "keywords": ["ember-addon"] }""")
        myFixture.addFileToProject("addon/services/session.js", "export default class SessionService {}")
        myFixture.addFileToProject("app/services/session.js", "export { default } from 'my-addon/services/session';")

        assertEquals(listOf("addon/services/session.js"), fromClauseTargets("app/services/session.js"))
    }

    fun testAppNameImportResolvesToAppFolder() {
        myFixture.addFileToProject("package.json", """{ "name": "my-app", "devDependencies": { "ember-cli": "*" } }""")
        myFixture.addFileToProject("app/services/session.js", "export default class SessionService {}")
        myFixture.addFileToProject("app/services/other.js", "export { default } from 'my-app/services/session';")

        assertEquals(listOf("app/services/session.js"), fromClauseTargets("app/services/other.js"))
    }
}
