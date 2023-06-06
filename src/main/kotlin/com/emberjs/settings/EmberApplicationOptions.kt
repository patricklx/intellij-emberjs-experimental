package com.emberjs.settings

import com.intellij.ide.util.PropertiesComponent


object EmberApplicationOptions {
    private val RUN_ESLINT_HBS = "com.emberjs.settings.runEslintOnHbs"
    private val RUN_ESLINT_GTS = "com.emberjs.settings.runEslintOnGts"
    private val FIX_ON_SAVE = "com.emberjs.settings.fixOnSave"

    private val props = PropertiesComponent.getInstance()

    var fixOnSave: Boolean
        get() = props.getBoolean(FIX_ON_SAVE, false)
        set(value) = props.setValue(FIX_ON_SAVE, value.toString())

    var runEslintOnHbs: Boolean
        get() = props.getBoolean(RUN_ESLINT_HBS, false)
        set(value) = props.setValue(RUN_ESLINT_HBS, value.toString())

    var runEslintOnGts: Boolean
        get() = props.getBoolean(RUN_ESLINT_GTS, false)
        set(value) = props.setValue(RUN_ESLINT_GTS, value.toString())
}
