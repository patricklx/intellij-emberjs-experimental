package com.emberjs.gts

import com.intellij.codeInsight.generation.CommentByBlockCommentHandler
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

abstract class GtsActionHandlerTest: BasePlatformTestCase() {
    private fun performWriteAction(project: Project, action: Runnable) {
        ApplicationManager.getApplication().runWriteAction {
            CommandProcessor.getInstance().executeCommand(project, action, "test command", null)
        }
    }

    private fun validateTestStrings(before: String, expected: String) {
        require(
            !(!before.contains("<caret>")
                    || !expected.contains("<caret>"))
        ) { "Test strings must contain \"<caret>\" to indicate caret position" }
    }

    /**
     * Call this method to test behavior when the given charToType is typed at the &lt;caret&gt;.
     * See class documentation for more info: [HbActionHandlerTest]
     */
    fun doCharTest(charToType: Char, before: String, expected: String) {
        EditorActionManager.getInstance()
        val typedAction = TypedAction.getInstance()
        doExecuteActionTest(
            before, expected
        ) {
            typedAction.actionPerformed(
                myFixture.editor,
                charToType,
                (myFixture.editor as EditorEx).dataContext
            )
        }
    }

    /**
     * Call this method to test behavior when Enter is typed.
     * See class documentation for more info: [HbActionHandlerTest]
     */
    protected fun doEnterTest(before: String, expected: String) {
        val enterActionHandler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER)
        val gtsBefore = """<template>
            |${before.prependIndent("    ")}
            |</template>""".trimMargin()
        val gtsAfter = """<template>
            |${expected.prependIndent("    ")}
            |</template>""".trimMargin()
        doExecuteActionTest(
            gtsBefore, gtsAfter
        ) {
            enterActionHandler.execute(
                myFixture.editor,
                null,
                (myFixture.editor as EditorEx).dataContext
            )
        }
    }

    /**
     * Call this method to test behavior when the "Comment with Line Comment" action is executed.
     * See class documentation for more info: [HbActionHandlerTest]
     */
    fun doLineCommentTest(before: String, expected: String) {
        doExecuteActionTest(
            before, expected
        ) { PlatformTestUtil.invokeNamedAction(IdeActions.ACTION_COMMENT_LINE) }
    }

    /**
     * Call this method to test behavior when the "Comment with Block Comment" action is executed.
     * See class documentation for more info: [HbActionHandlerTest]
     */
    fun doBlockCommentTest(before: String, expected: String) {
        doExecuteActionTest(
            before, expected
        ) {
            CommentByBlockCommentHandler().invoke(
                myFixture.project, myFixture.editor,
                myFixture.editor.caretModel.primaryCaret, myFixture.file
            )
        }
    }

    private fun doExecuteActionTest(before: String, expected: String, action: Runnable) {
        validateTestStrings(before, expected)

        myFixture.configureByText(GtsFileType.INSTANCE, before)
        performWriteAction(myFixture.project, action)
        myFixture.checkResult(expected)
    }
}