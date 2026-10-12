package com.emberjs

import com.intellij.testFramework.LoggedErrorProcessor
import org.junit.platform.launcher.LauncherSession
import org.junit.platform.launcher.LauncherSessionListener

/**
 * In IntelliJ IDEA Ultimate 2026.2.3 the obfuscated startup activity of the bundled ultimate plugin
 * (`<postStartupActivity implementation="Z.Z.Z.Z.Z"/>`) clashes with an unrelated interface of the same name in
 * lib/product-backend.jar. The test sandbox loads the interface, so every test project open logs
 * "Cannot create extension (class=Z.Z.Z.Z.Z) [Plugin: com.intellij.modules.ultimate]", which the test logger turns
 * into a test failure. Keep exactly that error in the log instead of rethrowing it; any other logged error still
 * fails the test. Remove once the platform no longer has the clash.
 */
class IgnoreKnownPlatformErrors : LauncherSessionListener {
    override fun launcherSessionOpened(session: LauncherSession) {
        val default = LoggedErrorProcessor()
        LoggedErrorProcessor.executeWith(object : LoggedErrorProcessor() {
            override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> =
                if (message.startsWith("Cannot create extension (class=Z.Z.Z.Z.Z) [Plugin: com.intellij.modules.ultimate]"))
                    setOf(Action.LOG)
                else
                    default.processError(category, message, details, t)
        })
    }
}
