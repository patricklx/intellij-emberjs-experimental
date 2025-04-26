import com.intellij.lang.javascript.linter.JSLinterConfigFileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ObjectUtils
import com.intellij.util.SmartList

class TemplateLintUtil {
    companion object {
        const val PACKAGE_NAME = "ember-template-lint"
        const val CLI_PACKAGE_NAME = "ember-cli-template-lint"
        const val CONFIG_FILE_NAME = ".template-lintrc"

        fun findWorkingDirectory(fileToLint: VirtualFile): VirtualFile? {
            val parent = fileToLint.parent ?: return null

            val configFile = JSLinterConfigFileUtil.findFileUpToFileSystemRoot(parent, "$CONFIG_FILE_NAME.js") ?:
            JSLinterConfigFileUtil.findFileUpToFileSystemRoot(parent, "$CONFIG_FILE_NAME.cjs")
            val configFileParent = configFile?.parent
            return ObjectUtils.chooseNotNull(configFileParent, parent) as VirtualFile
        }

        fun getPossibleConfigs(dir: VirtualFile): Collection<VirtualFile?> {
            return SmartList(
                JSLinterConfigFileUtil.findFileUpToFileSystemRoot(dir, "$CONFIG_FILE_NAME.js"),
                JSLinterConfigFileUtil.findFileUpToFileSystemRoot(dir, "$CONFIG_FILE_NAME.cjs"),
            ).filterNotNull()
        }

        fun isTemplateLintConfigFile(file: VirtualFile): Boolean {
            return file.name == "$CONFIG_FILE_NAME.js" || file.name == "$CONFIG_FILE_NAME.cjs"
        }
    }
}