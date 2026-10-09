package com.emberjs.project

import com.emberjs.cli.EmberCliFrameworkDetector
import com.emberjs.utils.clearVirtualCache
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project

class ProjectService(project: Project) : Disposable {
    /** Ember roots found by [EmberCliFrameworkDetector] in this project. Lives (and dies) with the project. */
    @Volatile
    var detectedFrameworks: List<EmberCliFrameworkDetector.EmberFrameworkDescription> = emptyList()

    init {
        val dumbModeListener = object : DumbService.DumbModeListener {
            override fun exitDumbMode() {
                clearVirtualCache()
            }
        }
        // Tie the subscription to this service so it is dropped when the project closes or the plugin unloads;
        // a parentless connection keeps plugin classes referenced and blocks dynamic unloading.
        project.messageBus
                .connect(this)
                .subscribe(DumbService.DUMB_MODE, dumbModeListener)
    }

    override fun dispose() {}

    companion object {
        fun getInstance(project: Project): ProjectService = project.getService(ProjectService::class.java)
    }
}
