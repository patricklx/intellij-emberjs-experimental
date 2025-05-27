package com.emberjs.gts

import com.dmarcotte.handlebars.file.HbFileViewProvider
import com.emberjs.glint.GlintTypeScriptService
import com.emberjs.icons.EmberIcons
import com.intellij.CommonBundle
import com.intellij.ide.actionsOnSave.ActionOnSaveBackedByOwnConfigurable
import com.intellij.ide.actionsOnSave.ActionOnSaveContext
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterField
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.javascript.nodejs.util.JSLinterPackage
import com.intellij.javascript.nodejs.util.NodePackage
import com.intellij.javascript.nodejs.util.NodePackageField
import com.intellij.javascript.nodejs.util.NodePackageRef
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.javascript.integration.JSAnnotationError
import com.intellij.lang.javascript.linter.*
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.OptionsBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMExternalizerUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.reference.SoftReference
import com.intellij.ui.components.ActionLink
import com.intellij.util.containers.ContainerUtil
import org.jdom.Element
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.lang.ref.Reference
import java.util.*
import javax.swing.JComponent


class GLintBundle {
    companion object {
        private var ourBundle: Reference<ResourceBundle>? = null
        @NonNls
        private const val BUNDLE = "com.emberjs.locale.GlintBundle"

        private fun getBundle(): ResourceBundle {
            var bundle = SoftReference.dereference(ourBundle)

            if (bundle == null) {
                bundle = ResourceBundle.getBundle(BUNDLE)
                ourBundle = java.lang.ref.SoftReference<ResourceBundle>(bundle)
            }

            return bundle!!
        }

        fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
            return CommonBundle.message(getBundle(), key, *params)
        }
    }
}


data class GlintState(
    private val myInterpreterRef: NodeJsInterpreterRef,
    private val myTemplateLintPackage: NodePackage,
) : JSNpmLinterState<GlintState> {

    private val myPackageRef: NodePackageRef = NodePackageRef.create(myTemplateLintPackage)

    override fun withLinterPackage(packageRef: NodePackageRef): GlintState {
        val constantPackage = packageRef.constantPackage ?: return DEFAULT

        return copy(myTemplateLintPackage = constantPackage)
    }

    override fun getInterpreterRef(): NodeJsInterpreterRef {
        return this.myInterpreterRef
    }

    override fun getNodePackageRef(): NodePackageRef {
        return this.myPackageRef
    }

    val templateLintPackage: NodePackage
        get() {
            return this.myTemplateLintPackage
        }

    companion object {
        val DEFAULT = GlintState(
            NodeJsInterpreterRef.createProjectRef(),
            NodePackage("@glint/core"))
    }
}


@Service(Service.Level.PROJECT)
@State(name = "GlintConfiguration", storages = [Storage("emberLinters/glint.xml")])
class GlintConfiguration(project: Project) : JSLinterConfiguration<GlintState>(project) {
    private val myPackage: JSLinterPackage = JSLinterPackage(project, "@glint/core")
    override fun savePrivateSettings(state: GlintState) {
        this.myPackage.force(NodePackageRef.create(state.interpreterRef.referenceName))
    }

    override fun loadPrivateSettings(state: GlintState): GlintState {
        this.myPackage.readOrDetect()
        val constantPackage = myPackage.getPackage().constantPackage

        if (constantPackage == null) {
            return defaultState
        }

        return state.copy(
            myInterpreterRef = this.myPackage.interpreter,
            myTemplateLintPackage = constantPackage
        )
    }

    override fun getInspectionClass(): Class<out JSLinterInspection> {
        return GlintInspection::class.java
    }

    override fun fromXml(p0: Element): GlintState {
        return this.defaultState.copy()
    }

    override fun getDefaultState(): GlintState {
        return GlintState.DEFAULT
    }

    override fun toXml(state: GlintState): Element? {
        if (state == defaultState) {
            return null
        }
        val parent = Element("glint-core")
        return parent
    }

}


class GlintConfigurable(project: Project, fullModeDialog: Boolean = false) :
    JSLinterConfigurable<GlintState>(
        project, GlintConfiguration::class.java, fullModeDialog) {

    companion object {
        const val ID = "configurable.emberjs.glint"
    }

    constructor(project: Project) : this(project, false)

    override fun getId(): String {
        return ID
    }

    override fun createView(): JSLinterView<GlintState> {
        return object : JSLinterView<GlintState> {
            private val myNodeInterpreterField: NodeJsInterpreterField = NodeJsInterpreterField(project, false)
            private val myTemplateLintPackageField: NodePackageField = NodePackageField(myNodeInterpreterField,
                "@glint/core"
            )
            var state: ExtendedLinterState<GlintState> = ExtendedLinterState(false, GlintState(
                myNodeInterpreterField.interpreterRef, myTemplateLintPackageField.selectedRef.constantPackage!!))
            override fun getComponent(): JComponent? {
                return null
            }

            override fun getExtendedState(): ExtendedLinterState<GlintState> {
                return state
            }

            override fun setExtendedState(p0: ExtendedLinterState<GlintState>) {
                state = p0
            }

        }
    }

    override fun getDisplayName(): String {
        return GLintBundle.message("g.lint.configurable.name")
    }
}


class GlintInspection : JSLinterInspection() {

    override fun getStaticDescription(): String {
        return "Glint"
    }

    override fun getExternalAnnotatorForBatchInspection(): JSLinterWithInspectionExternalAnnotator<*, *> {
        return GlintExternalAnnotator.INSTANCE_FOR_BATCH_INSPECTION
    }

    override fun getSettingsPath(): List<String?> {
        return ContainerUtil.newArrayList(
            OptionsBundle.message("configurable.group.language.settings.display.name", *arrayOfNulls(0)),
            GLintBundle.message("g.lint.configurable.title"),
            this.displayName
        )
    }
}


class GlintExternalAnnotator : JSLinterExternalAnnotator<GlintState>(true) {
    companion object {
        val INSTANCE_FOR_BATCH_INSPECTION = GlintExternalAnnotator()
    }

    override fun acceptPsiFile(file: PsiFile): Boolean {
        return (file.viewProvider is GtsFileViewProvider && file.fileType is GtsFileType)
                || (file is JSFile && file.viewProvider !is GtsFileViewProvider)
                || file.viewProvider is HbFileViewProvider
    }

    override fun annotate(input: JSLinterInput<GlintState>): JSLinterAnnotationResult? {
        val file = input.psiFile
        if (file.viewProvider is GtsFileViewProvider && file !is GtsFile) {
            return JSLinterAnnotationResult.createLinterResult(input, listOf(), null as VirtualFile?)
        }
        var res: List<JSAnnotationError>? = null
        try {
            val service = GlintTypeScriptService.getInstance(input.project)
            res = service.highlight(input.psiFile)?.get()?.toList()
        } catch (ex: Exception) {
            res = null
        }

        val errors: MutableList<JSLinterError> = mutableListOf()
        errors.addAll(res?.map { JSLinterError(it.line + 1, it.column, it.description, it.category, it.severity) } ?: listOf())
        return JSLinterAnnotationResult.createLinterResult(input, errors.toList(), null as VirtualFile?)
    }

    override fun apply(file: PsiFile, annotationResult: JSLinterAnnotationResult?, holder: AnnotationHolder) {
        annotationResult?.let {
            val prefix = GLintBundle.message("g.lint.message.prefix") + " "
            val configurable = GlintConfigurable(file.project)
            val fixes = JSLinterStandardFixes()
            JSLinterAnnotationsBuilder(file, annotationResult, holder, configurable, prefix, this.inspectionClass, fixes)
                .setHighlightingGranularity(HighlightingGranularity.element)
                .setDefaultFileLevelErrorIcon(EmberIcons.GLINT_16)
                .apply()
        }
    }

    override fun getConfigurationClass(): Class<out JSLinterConfiguration<GlintState>>? {
        return GlintConfiguration::class.java
    }

    override fun getInspectionClass(): Class<out JSLinterInspection> {
        return GlintInspection::class.java
    }
}
