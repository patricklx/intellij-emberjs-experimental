package com.emberjs.index

import com.emberjs.resolver.EmberName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.CommonProcessors
import com.intellij.util.FilteringProcessor
import com.intellij.util.Processor
import com.intellij.util.indexing.*

class EmberNameIndex() : ScalarIndexExtension<EmberName>() {

    override fun getName() = NAME
    override fun getVersion() = 5
    override fun getKeyDescriptor() = EmberNameKeyDescriptor()
    override fun dependsOnFileContent() = false

    override fun getInputFilter() = FileBasedIndex.InputFilter { it.extension in FILE_EXTENSIONS }

    override fun getIndexer() = DataIndexer<EmberName, Void?, FileContent> { inputData ->
        EmberName.from(inputData.file)?.let { mapOf(it to null) } ?: emptyMap()
    }

    companion object {
        val NAME: ID<EmberName, Void> = ID.create("ember.names")
        private val FILE_EXTENSIONS = setOf("css", "scss", "js", "ts", "hbs", "handlebars", "d.ts")

        private val index: FileBasedIndex get() = FileBasedIndex.getInstance()

        fun getAllKeys(project: Project): Collection<EmberName>
                = index.getAllKeys(NAME, project)

        fun processAllKeys(processor: Processor<EmberName>, scope: GlobalSearchScope, idFilter: IdFilter? = null)
                = index.processAllKeys(NAME, processor, scope, idFilter)

        fun getContainingFiles(module: EmberName, scope: GlobalSearchScope): Collection<VirtualFile>
                = index.getContainingFiles(NAME, module, scope)

        fun hasContainingFiles(module: EmberName, scope: GlobalSearchScope): Boolean {
            val processor = AnyValueProcessor<Void>()
            index.processValues(NAME, module, null, processor, scope)
            return processor.called
        }

        fun getFilteredKeys(scope: GlobalSearchScope, filterFn: (EmberName) -> Boolean): Collection<EmberName> {
            val collector = CommonProcessors.CollectProcessor<EmberName>()
            val filter = FilteringProcessor<EmberName>(Condition { filterFn(it) }, collector)

            processAllKeys(filter, scope)

            return collector.results
        }
    }
}
