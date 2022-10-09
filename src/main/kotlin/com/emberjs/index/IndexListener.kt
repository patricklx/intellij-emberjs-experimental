package com.emberjs.index

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.FileContentUtil
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistory
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistoryListener

class IndexListener : ProjectIndexingHistoryListener {
    override fun onFinishedIndexing(projectIndexingHistory: ProjectIndexingHistory) {
        // ApplicationManager.getApplication().invokeLater {
        //    ApplicationManager.getApplication().runWriteAction {
        //        FileContentUtil.reparseOpenedFiles()
        //    }
        //}
    }
}
