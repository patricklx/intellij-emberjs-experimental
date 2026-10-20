package com.emberjs

import com.intellij.testFramework.LoggedErrorProcessor
import org.junit.platform.launcher.LauncherSession
import org.junit.platform.launcher.LauncherSessionListener

/**
 * Suppresses known intermittent platform errors that would otherwise turn into test failures.
 *
 * - "Cannot create extension (class=Z.Z.Z.Z.Z) [Plugin: com.intellij.modules.ultimate]": In IntelliJ IDEA Ultimate
 *   2026.2.3 the obfuscated startup activity of the bundled ultimate plugin clashes with an unrelated interface of
 *   the same name in lib/product-backend.jar. The test sandbox loads the interface, so every test project open logs
 *   this error. Remove once the platform no longer has the clash.
 *
 * - "JSFilterLexer.start was not called": The JavaScript plugin's string-literal words index intermittently logs
 *   this while indexing .gts/.gjs files. It is a bug inside the JavaScript plugin triggered by concurrent indexing,
 *   not by anything in our code. Suppressing it here covers all test classes without requiring per-test wrappers.
 */
class IgnoreKnownPlatformErrors : LauncherSessionListener {
    override fun launcherSessionOpened(session: LauncherSession) {
        val default = LoggedErrorProcessor()
        LoggedErrorProcessor.executeWith(object : LoggedErrorProcessor() {
            override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> =
                when {
                    message.startsWith("Cannot create extension (class=Z.Z.Z.Z.Z) [Plugin: com.intellij.modules.ultimate]") ->
                        setOf(Action.LOG)
                    message == "JSFilterLexer.start was not called" ->
                        Action.NONE
                    else ->
                        default.processError(category, message, details, t)
                }
        })
    }
}
