package com.emberjs.hbs

import com.dmarcotte.handlebars.HbLanguage
import com.emberjs.gts.GjsFileType
import com.emberjs.gts.GtsFileType
import com.emberjs.gts.GtsLanguage
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.generation.AutoIndentLinesHandler
import com.intellij.lang.javascript.JavaScriptSupportLoader
import com.intellij.lang.javascript.inspections.ES6UnusedImportsInspection
import com.intellij.lang.javascript.inspections.JSUnusedLocalSymbolsInspection
import com.intellij.lang.javascript.psi.impl.JSFileImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.templateLanguages.TemplateDataLanguageMappings
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.IncorrectOperationException
import junit.framework.TestCase
import org.jetbrains.annotations.NonNls
import org.junit.Test



class GtsFormatIdentTest : BasePlatformTestCase() {

    @Throws(IncorrectOperationException::class)
    fun doStringBasedTest(text: String, textAfter: String) {
        doTextTest(text, textAfter, "gts", GtsFileType.INSTANCE)
    }


    /**
     * This method runs both a full-file reformat on beforeText, and a line-by-line reformat.  Though the tests
     * would output slightly better errors if these were separate tests, enforcing that they are always both run
     * for any test defined is the easiest way to ensure that the line-by-line is not messed up by formatter changes
     *
     * @param beforeText               The text run the formatter on
     * @param textAfter                The expected result after running the formatter
     * @param templateDataLanguageType The templated language of the file
     */
    @Throws(IncorrectOperationException::class)
    fun doTextTest(beforeText: String, textAfter: String, extension: String, templateDataLanguageType: LanguageFileType) {
        // define action to run "Reformat Code" on the whole "file" defined by beforeText
        val fullFormatRunnableFactory = Runnable {
            val rangeToUse: TextRange = myFixture.file.textRange
            val styleManager: CodeStyleManager =
                CodeStyleManager.getInstance(project)
            styleManager.reformat(myFixture.file)
        }

        // define action to run "Adjust line indent" on every line in the "file" defined by beforeText
        val lineFormatRunnableFactory = Runnable {
            val editor: Editor = myFixture.editor
            editor.selectionModel.setSelection(0, editor.document.textLength)
            AutoIndentLinesHandler().invoke(myFixture.project, editor, myFixture.file)
        }

        doFormatterActionTest(fullFormatRunnableFactory, beforeText, textAfter, extension, templateDataLanguageType)
        doFormatterActionTest(lineFormatRunnableFactory, beforeText, textAfter, extension, templateDataLanguageType)
    }

    private fun doFormatterActionTest(
        formatAction: Runnable,
        beforeText: String,
        textAfter: String,
        extension: String,
        templateDataLanguageType: LanguageFileType
    ) {
        val baseFile: PsiFile = myFixture.configureByText("A.$extension", beforeText)

        val virtualFile = checkNotNull(baseFile.virtualFile)
        TemplateDataLanguageMappings.getInstance(project).setMapping(virtualFile, templateDataLanguageType.language)
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        // fetch a fresh instance of the file -- the template data mapping creates a new instance,
        // which was causing problems in PsiFileImpl.isValid()
        val file = checkNotNull(PsiManager.getInstance(project).findFile(virtualFile))
        WriteCommandAction.runWriteCommandAction(project, formatAction)
        TemplateDataLanguageMappings.getInstance(project).cleanupForNextTest()
        assertEquals("Reformat Code failed", prepareText(textAfter), prepareText(file.text))
    }

    private fun prepareText(act: String): String {
        var actual = act
        actual = StringUtil.trimStart(actual, "\n")
        actual = StringUtil.trimStart(actual, "\n")

        // Strip trailing spaces
        val doc: Document = EditorFactory.getInstance().createDocument(actual)
        CommandProcessor.getInstance().executeCommand(project, {
            ApplicationManager.getApplication().runWriteAction {
                (doc as DocumentImpl).stripTrailingSpaces(
                    project
                )
            }
        }, "formatting", null)

        return doc.text
    }

    @Test
    fun testGtsFormat() {
        val gts = """
            let x = <template>hello world</template>;
            x = <template>
               hello world
                hello world
                hello world
                <div>
             {{demo}}
                </div>
                hello world
                hello world
                 {{demo}}
                hello world
            </template>;
            x = <template>
              hello world hello world hello world hello world hello world hello world
              <div></div>
            </template>;
            x = <template>
                    hello world hello world hello world hello world hello world hello world
                    <div></div>
            </template>;
             x = <template>
             <div></div>
                    hello world hello world hello world hello world hello world hello world                    
            </template>;
            
            class Foo {
                <template>
                Hi
                </template>
            }
            
            class Bar {
                <template>
                        hello world
                    hello world
                    hello world
                    hello world
                     {{demo}}
                    hello world
                    hello world
                </template>
            }
            class Bar {
                <template>
                <div>
         {{demo}}
                </div>
                        hello world
                    hello world
                    hello world
                    hello world
                    hello world
                    hello world
                </template>
            }
            
            <template>
                    hello world
            <div>
         {{demo}}
            </div>
                hello world
                hello world
                hello world
                hello world
                hello world
                 {{demo}}
            </template>
        """.trimIndent()

        val gtsAfter = """
            let x = <template>hello world</template>;
            x = <template>
                hello world
                hello world
                hello world
                <div>
                    {{demo}}
                </div>
                hello world
                hello world
                {{demo}}
                hello world
            </template>;
            x = <template>
                hello world hello world hello world hello world hello world hello world
                <div></div>
            </template>;
            x = <template>
                hello world hello world hello world hello world hello world hello world
                <div></div>
            </template>;
            x = <template>
                <div></div>
                hello world hello world hello world hello world hello world hello world
            </template>;
            
            class Foo {
                <template>
                    Hi
                </template>
            }
            
            class Bar {
                <template>
                    hello world
                    hello world
                    hello world
                    hello world
                    {{demo}}
                    hello world
                    hello world
                </template>
            }
            class Bar {
                <template>
                    <div>
                        {{demo}}
                    </div>
                    hello world
                    hello world
                    hello world
                    hello world
                    hello world
                    hello world
                </template>
            }
            
            <template>
                hello world
                <div>
                    {{demo}}
                </div>
                hello world
                hello world
                hello world
                hello world
                hello world
                {{demo}}
            </template>
        """.trimIndent()
        doStringBasedTest(gts, gtsAfter)
    }


}
