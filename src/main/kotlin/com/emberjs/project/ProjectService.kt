package com.emberjs.project

import com.emberjs.utils.clearVirtualCache
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project

class ProjectService(project: Project) {
    init {
        val dumbModeListener = object : DumbService.DumbModeListener {
            override fun exitDumbMode() {
                clearVirtualCache()
            }
        }
        project.messageBus
                .connect()
                .subscribe(DumbService.DUMB_MODE, dumbModeListener)
    }
}