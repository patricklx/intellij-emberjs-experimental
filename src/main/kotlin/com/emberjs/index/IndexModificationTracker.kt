package com.emberjs.index

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.util.indexing.FileBasedIndex

class IndexModificationTracker(val project: Project): ModificationTracker {
    override fun getModificationCount(): Long {
        return FileBasedIndex.getInstance().getIndexModificationStamp(EmberNameIndex.NAME, project)
    }
}