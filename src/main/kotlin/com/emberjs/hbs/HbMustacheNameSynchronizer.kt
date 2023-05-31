// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.emberjs.hbs

import com.dmarcotte.handlebars.HbLanguage
import com.dmarcotte.handlebars.psi.HbBlockWrapper
import com.dmarcotte.handlebars.psi.HbCloseBlockMustache
import com.dmarcotte.handlebars.psi.HbOpenBlockMustache
import com.emberjs.utils.ifTrue
import com.intellij.application.options.editor.WebEditorOptions
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.CommandListener
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.*
import com.intellij.pom.core.impl.PomModelImpl
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import java.util.*
import java.util.stream.Stream

object HbMustacheNameSynchronizer : EditorFactoryListener {
    private val SKIP_COMMAND = Key.create<Boolean>("tag.name.synchronizer.skip.command")
    private val LOG = Logger.getInstance(HbMustacheNameSynchronizer::class.java)
    private val SYNCHRONIZER_KEY: Key<MustacheNameSynchronizer> = Key.create("tag_name_synchronizer")
    private fun createSynchronizerFor(editor: Editor) {
        val project = editor.project
        if (project == null || editor !is EditorImpl) {
            return
        }
        val document = editor.document
        val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return
        val file = PsiManager.getInstance(project).findFile(virtualFile)
        if (file?.viewProvider?.getPsi(HbLanguage.INSTANCE) == null) {
            return
        }
        val language = HbLanguage.INSTANCE
        if (language != null) {
            MustacheNameSynchronizer(editor, project, language).listenForDocumentChanges()
        }
    }

    private fun recreateSynchronizers() {
        for (editor in EditorFactory.getInstance().allEditors) {
            val synchronizer = editor.getUserData(SYNCHRONIZER_KEY)
            if (synchronizer != null) {
                Disposer.dispose(synchronizer)
            }
            createSynchronizerFor(editor)
        }
    }

    private fun findSynchronizers(document: Document?): Stream<MustacheNameSynchronizer?>? {
        return if (document == null || !WebEditorOptions.getInstance().isSyncTagEditing) {
            Stream.empty()
        } else EditorFactory.getInstance().editors(document, null)
                .map { editor: Editor -> editor.getUserData(SYNCHRONIZER_KEY) }
                .filter { obj: MustacheNameSynchronizer? -> Objects.nonNull(obj) }
    }

    class MyEditorFactoryListener : EditorFactoryListener {
        override fun editorCreated(event: EditorFactoryEvent) {
            createSynchronizerFor(event.editor)
        }
    }

    internal class MyCommandListener : CommandListener {
        override fun beforeCommandFinished(event: CommandEvent) {
            findSynchronizers(event.document)?.forEach { synchronizer: MustacheNameSynchronizer? -> synchronizer?.beforeCommandFinished() }
        }
    }

    class MyDynamicPluginListener : DynamicPluginListener {
        override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
            recreateSynchronizers()
        }

        override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
            recreateSynchronizers()
        }
    }

    class MustacheNameSynchronizer(private val myEditor: EditorImpl, private val myProject: Project, private val myLanguage: Language) : DocumentListener, CaretListener, Disposable {
        private val myDocumentManager: PsiDocumentManagerBase
        private var myApplying = false

        init {
            myDocumentManager = PsiDocumentManager.getInstance(myProject) as PsiDocumentManagerBase
        }

        override fun dispose() {
            myEditor.putUserData(SYNCHRONIZER_KEY, null)
        }

        fun listenForDocumentChanges() {
            Disposer.register(myEditor.disposable, this)
            myEditor.document.addDocumentListener(this, this)
            myEditor.caretModel.addCaretListener(this, this)
            myEditor.putUserData(SYNCHRONIZER_KEY, this)
            for (caret in myEditor.caretModel.allCarets) {
                val markers = getMarkers(caret)
                if (markers != null) {
                    allMarkers.add(markers.first)
                    allMarkers.add(markers.second)
                }
            }
        }

        override fun caretRemoved(event: CaretEvent) {
            val caret = event.caret
            if (caret != null) {
                clearMarkers(caret)
            }
        }

        override fun beforeDocumentChange(event: DocumentEvent) {
            if (!WebEditorOptions.getInstance().isSyncTagEditing) return
            val document = event.document
            val project = Objects.requireNonNull(myEditor.project)
            if (myApplying || project!!.isDefault || UndoManager.getInstance(project).isUndoInProgress ||
                    !PomModelImpl.isAllowPsiModification() || document.isInBulkUpdate) {
                return
            }
            val offset = event.offset
            val oldLength = event.oldLength
            val fragment = event.newFragment
            val newLength = event.newLength
            if (document.getUserData(SKIP_COMMAND) === java.lang.Boolean.TRUE) {
                // xml completion inserts extra space after tag name to ensure correct parsing
                // js auto-import may change beginning of the document when component is imported
                // we need to ignore it
                return
            }
            val caret: Caret = myEditor.caretModel.currentCaret
            for (i in 0 until newLength) {
                if (fragment[i] == ' ' || fragment[i] == '{'){
                    clearMarkers(caret)
                    return
                }
            }
            var markers: Couple<RangeMarker>? = getMarkers(caret)
            if (markers != null && !fitsInMarker(markers, offset, oldLength)) {
                clearMarkers(caret)
                markers = null
            }
            val caretOffset = caret.offset
            val floor = allMarkers.floor(TextRange(caretOffset, caretOffset))
            // Skip markers creation if cursors cover same tag area as other cursors
            if (floor != null && (markers == null || floor !== markers.second && floor !== markers.first) && caretOffset <= floor.endOffset) {
                clearMarkers(caret)
                return
            }
            if (markers == null) {
                val file = myDocumentManager.getPsiFile(document)
                if (file == null || myDocumentManager.synchronizer.isInSynchronization(document)) return
                val leader = createMustacheNameMarker(caret, file) ?: return
                leader.isGreedyToLeft = true
                leader.isGreedyToRight = true
                if (myDocumentManager.isUncommited(document)) {
                    myDocumentManager.commitDocument(document)
                }
                val support = findSupport(leader, file, document) ?: return
                support.isGreedyToLeft = true
                support.isGreedyToRight = true
                markers = Couple.of(leader, support)
                if (!fitsInMarker(markers, offset, oldLength)) return
                setMarkers(caret, markers)
            }
        }

        private fun createMustacheNameMarker(caret: Caret, file: PsiFile): RangeMarker? {
            val offset = caret.offset
            val document: Document = myEditor.document
            val element: PsiElement = file.viewProvider.findElementAt(offset, HbLanguage.INSTANCE) ?: return null
            val block = PsiTreeUtil.findFirstParent(element) { it is HbBlockWrapper } ?: return null
            val open = block.children.firstOrNull() as? HbOpenBlockMustache ?: return null
            val close = block.children.lastOrNull() as? HbCloseBlockMustache ?: return null
            val start = open.children.firstOrNull()?.nextSibling ?: return null
            val end = close.children.firstOrNull()?.nextSibling ?: return null
            if (start.text != end.text || !close.text.endsWith("}}")) {
                return null
            }
            val leader = open.textRange.contains(offset).ifTrue { start } ?: end
            return document.createRangeMarker(leader.startOffset, leader.endOffset, true)
        }

        fun beforeCommandFinished() {
            val action = CaretAction { caret: Caret ->
                val markers = getMarkers(caret)
                val document: Document = myEditor.document
                if (markers == null || !markers.first.isValid
                        || !markers.second.isValid || getNameToReplace(document, markers) == null) {
                    return@CaretAction
                }
                val apply = Runnable {
                    val name = getNameToReplace(document, markers)
                    if (name != null) {
                        val support = markers.second
                        document.replaceString(support.startOffset, support.endOffset, name)
                    }
                }
                ApplicationManager.getApplication().runWriteAction {
                    val lookup = LookupManager.getActiveLookup(myEditor) as LookupImpl?
                    lookup?.performGuardedChange(apply) ?: apply.run()
                }
            }
            myApplying = true
            try {
                if (myEditor.caretModel.isIteratingOverCarets) {
                    action.perform(myEditor.caretModel.currentCaret)
                } else {
                    myEditor.caretModel.runForEachCaret(action)
                }
            } finally {
                myApplying = false
            }
        }

        private fun findSupport(leader: RangeMarker, file: PsiFile, document: Document): RangeMarker? {
            val offset = leader.startOffset
            val element: PsiElement = file.viewProvider.findElementAt(offset, HbLanguage.INSTANCE) ?: return null
            val block = PsiTreeUtil.findFirstParent(element) { it is HbBlockWrapper } ?: return null
            val open = block.children.firstOrNull() as? HbOpenBlockMustache ?: return null
            val close = block.children.lastOrNull() as? HbCloseBlockMustache ?: return null
            val start = open.children.firstOrNull()?.nextSibling ?: return null
            val end = close.children.firstOrNull()?.nextSibling ?: return null
            if (start.text != end.text) {
                return null
            }
            val support = open.textRange.contains(offset).ifTrue { end } ?: start
            return document.createRangeMarker(support.startOffset, support.endOffset, true)
        }

        companion object {
            private val MARKERS_KEY = Key.create<Couple<RangeMarker>>("tag.name.synchronizer.markers")
            private val allMarkers = TreeSet(Comparator.comparingInt { obj: Segment -> obj.startOffset })
            private fun fitsInMarker(markers: Couple<RangeMarker>, offset: Int, oldLength: Int): Boolean {
                val leader = markers.first
                return leader.isValid && offset >= leader.startOffset && offset + oldLength <= leader.endOffset
            }

            private fun getMarkers(caret: Caret): Couple<RangeMarker>? {
                return caret.getUserData(MARKERS_KEY)
            }

            private fun setMarkers(caret: Caret, markers: Couple<RangeMarker>) {
                caret.putUserData(MARKERS_KEY, markers)
                allMarkers.add(markers.first)
                allMarkers.add(markers.second)
            }

            private fun clearMarkers(caret: Caret) {
                val markers = caret.getUserData(MARKERS_KEY)
                if (markers != null) {
                    allMarkers.remove(markers.first)
                    allMarkers.remove(markers.second)
                    markers.first.dispose()
                    markers.second.dispose()
                    caret.putUserData(MARKERS_KEY, null)
                }
            }

            fun getNameToReplace(document: Document, markers: Couple<RangeMarker>): String? {
                val leader = markers.first
                val support = markers.second
                if (document.textLength < leader.endOffset) {
                    return null
                }
                val name = document.getText(leader.textRange)
                return if (document.textLength >= support.endOffset &&
                        name != document.getText(support.textRange)) {
                    name
                } else null
            }

        }
    }
}
