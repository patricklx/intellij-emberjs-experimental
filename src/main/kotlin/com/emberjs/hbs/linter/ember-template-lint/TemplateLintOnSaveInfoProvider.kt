import com.intellij.ide.actionsOnSave.ActionOnSaveContext
import com.intellij.ide.actionsOnSave.ActionOnSaveInfo
import com.intellij.ide.actionsOnSave.ActionOnSaveInfoProvider

class TemplateLintOnSaveInfoProvider : ActionOnSaveInfoProvider() {
    override fun getActionOnSaveInfos(context: ActionOnSaveContext): MutableCollection<out ActionOnSaveInfo> {
        return mutableListOf(TemplateLintConfigurable.TemplateLintOnSaveActionInfo(context))
    }
}
