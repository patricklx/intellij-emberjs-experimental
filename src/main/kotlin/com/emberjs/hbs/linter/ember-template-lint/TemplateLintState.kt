
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.javascript.nodejs.util.NodePackage
import com.intellij.javascript.nodejs.util.NodePackageRef
import com.intellij.lang.javascript.linter.JSNpmLinterState

data class TemplateLintState(
        private val myInterpreterRef: NodeJsInterpreterRef,
        private val myTemplateLintPackage: NodePackage,
        val isRunOnSave: Boolean,
) : JSNpmLinterState<TemplateLintState> {

    private val myPackageRef: NodePackageRef = myTemplateLintPackage.let { NodePackageRef.create(it) }

    override fun withLinterPackage(packageRef: NodePackageRef): TemplateLintState {
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
        val DEFAULT = TemplateLintState(
                NodeJsInterpreterRef.createProjectRef(),
                NodePackage(""),
                false)
    }
}
