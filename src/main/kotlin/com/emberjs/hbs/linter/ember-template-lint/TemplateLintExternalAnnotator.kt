
import com.dmarcotte.handlebars.psi.HbPsiFile
import com.emberjs.glint.GlintAnnotationError
import com.emberjs.glint.GlintLanguageServiceProvider
import com.emberjs.icons.EmberIcons
import com.emberjs.utils.originalVirtualFile
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.javascript.linter.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.xml.util.XmlStringUtil

class TemplateLintExternalAnnotator(onTheFly: Boolean = true) : JSLinterExternalAnnotator<TemplateLintState>(onTheFly) {
    companion object {
        val INSTANCE_FOR_BATCH_INSPECTION = TemplateLintExternalAnnotator(false)
    }

    override fun getConfigurationClass(): Class<TemplateLintConfiguration>? {
        return try {
            TemplateLintConfiguration::class.java
        } catch (ex: Exception) {
            null
        }

    }

    override fun createSettingsConfigurable(project: Project): UntypedJSLinterConfigurable {
        return TemplateLintConfigurable(project, true)
    }

    override fun acceptPsiFile(file: PsiFile): Boolean {
        return file is HbPsiFile
    }

    override fun annotate(input: JSLinterInput<TemplateLintState>): JSLinterAnnotationResult? {
        var res: JSLinterAnnotationResult?
        try {
            res = TemplateLintExternalRunner(this.isOnTheFly).highlight(input)
        } catch (ex: Exception) {
            res = null
        }
        var fileLevelAnnotation: JSLinterFileLevelAnnotation? = null
        val errors: MutableList<JSLinterError> = mutableListOf()
        try {
            val list = GlintLanguageServiceProvider(input.project).getService(input.virtualFile)?.highlight(input.psiFile)?.get()?.map { it as GlintAnnotationError } ?: listOf()
            errors.addAll(list.map { JSLinterError(it.line, it.column, it.description, "glint", it.severity) }.toMutableList())
        } catch (ex: Exception) {
            fileLevelAnnotation = JSLinterFileLevelAnnotation("Failed to get glint annotations")
        }
        errors.addAll(res?.errors ?: listOf())
        if (fileLevelAnnotation != null) {
            return JSLinterAnnotationResult.createLinterResult(input, fileLevelAnnotation, errors.toList(), null as VirtualFile?)
        }
        return JSLinterAnnotationResult.createLinterResult(input, errors.toList(), null as VirtualFile?)
    }

    override fun apply(file: PsiFile, annotationResult: JSLinterAnnotationResult?, holder: AnnotationHolder) {
        annotationResult?.let {
            val prefix = TemplateLintBundle.message("hbs.lint.message.prefix") + " "
            annotationResult.errors.removeIf { it.code?.startsWith("glint") == true }
            val configurable = TemplateLintConfigurable(file.project, true)
            val fixes = JSLinterStandardFixes()
                    .setEditSettingsAction(JSLinterEditSettingsAction(configurable, EmberIcons.TEMPLATE_LINT_16))
            JSLinterAnnotationsBuilder(file, annotationResult, holder, configurable, prefix, this.inspectionClass, fixes)
                    .setHighlightingGranularity(HighlightingGranularity.element)
                    .setDefaultFileLevelErrorIcon(EmberIcons.TEMPLATE_LINT_16)
                    .apply()
            val psiFile = PsiManager.getInstance(file.project).findFile(file.originalVirtualFile!!)!!
            val errors = GlintLanguageServiceProvider(file.project).getService(file.virtualFile)?.highlight(psiFile)?.get()?.map { it as GlintAnnotationError } ?: listOf()
            errors.forEach {
                val start = StringUtil.lineColToOffset(file.text, it.line, it.column)
                val end = StringUtil.lineColToOffset(file.text, it.endLine, it.endColumn)
                val escapedDescription: String = XmlStringUtil.escapeString(it.description)
                val firstLine = StringUtil.notNullize(StringUtil.splitByLines(escapedDescription).first())
                var message = "glint: $firstLine"
                val code: String = it.code!!
                message = "$message ($code)"
                holder.newAnnotation(HighlightSeverity.ERROR, message)
                        .range(TextRange(start, end))
                        .tooltip(getTooltip("glint: ", escapedDescription, code))
                        .create()
            }
        }
    }

    private fun getTooltip(prefix: String, description: String, code: String?): String {
        val builder = StringBuilder()
        builder.append("<html>").append(prefix)
        val descriptionWithLinks = description
        if (StringUtil.contains(description, "\n")) {
            builder.append("<br>").append("<pre>").append(descriptionWithLinks).append("</pre>")
        } else {
            builder.append(descriptionWithLinks)
        }
        if (code != null) {
            builder.append("(").append(code).append(")")
        }
        builder.append("</html>")
        return builder.toString()
    }

    override fun getInspectionClass(): Class<out JSLinterInspection> {
        return TemplateLintInspection::class.java
    }

    // We only overwrite this method because super returns early for MultiplePsiFilesPerDocumentFileViewProvider
    override fun collectInformation(psiFile: PsiFile, editor: Editor?): JSLinterInput<TemplateLintState>? {
        val project = psiFile.project

        if (this.isOnTheFly && !JSLinterInspection.isToolEnabled(project, this.inspectionClass, psiFile)) {
            return super.collectInformation(psiFile, editor)
        }

        if (psiFile.context == null && acceptPsiFile(psiFile)) {
            val virtualFile = psiFile.virtualFile
            if (virtualFile != null && virtualFile.isInLocalFileSystem && this.configurationClass != null) {
                val configuration: JSLinterConfiguration<TemplateLintState> = JSLinterConfiguration.getInstance(project, this.configurationClass!!)
                val state: TemplateLintState = configuration.extendedState.state
                if (acceptState(state)) {
                    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                    if (document != null && !StringUtil.isEmptyOrSpaces(document.text)) {
                        val colorsScheme = editor?.colorsScheme
                        return createInfo(psiFile, state, colorsScheme)
                    }
                }
            }
        }

        return null
    }
}
