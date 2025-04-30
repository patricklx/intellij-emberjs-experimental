import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.javascript.linter.JSLinterError
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.text.StringUtil
import kotlinx.serialization.json.*
import java.io.IOException
import java.util.*

class TemplateLintResultParser {
    companion object {
        private val LOG = Logger.getInstance(TemplateLintResultParser::class.java)

        private const val WARNING_SEVERITY = 1
        private const val ERROR_SEVERITY = 2

        private const val LINE = "line"
        private const val COLUMN = "column"
        private const val RULE = "rule"
        private const val SEVERITY = "severity"
        private const val MESSAGE = "message"

        private fun parseItem(map: JsonObject): JSLinterError {
            val line = map[LINE]!!.jsonPrimitive.int
            val column = map[COLUMN]!!.jsonPrimitive.int
            val rule = map[RULE]!!.jsonPrimitive.content
            val text = map[MESSAGE]!!.jsonPrimitive.content

            val highlightSeverity = when (map[SEVERITY]!!.jsonPrimitive.int) {
                WARNING_SEVERITY -> HighlightSeverity.WARNING
                ERROR_SEVERITY -> HighlightSeverity.ERROR
                else -> null
            }

            return JSLinterError(line, column + 1, text, rule, highlightSeverity)
        }
    }

    @Throws(Exception::class)
    fun parse(stdout: String?): List<JSLinterError>? {
        if (StringUtil.isEmptyOrSpaces(stdout)) {
            return null
        }

        val errorList: ArrayList<JSLinterError> = ArrayList()
        try {
            val obj = Json.parseToJsonElement(stdout!!).jsonObject

            val issues = obj.values.flatMap { map -> map.jsonArray }
            for (i in 0 until issues.size) {
                val issue = issues[i].jsonObject

                // we skip fatal errors
                if (issue.containsKey("fatal") && issue["fatal"]?.jsonPrimitive?.boolean == true) continue

                errorList.add(parseItem(issue))
            }

            return errorList
        } catch (ioException: IOException) {
            return null
        } catch (exception: Exception) {
            LOG.warn("TemplateLint result parsing error: $exception")
            throw Exception("TemplateLint result parsing error", exception)
        }
    }
}
