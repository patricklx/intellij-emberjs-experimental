import com.emberjs.icons.EmberIcons
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType
import com.intellij.execution.process.*
import com.intellij.javascript.nodejs.interpreter.NodeCommandLineConfigurator
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterRef
import com.intellij.javascript.nodejs.reference.NodeModuleManager
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.javascript.linter.*
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ObjectUtils
import com.intellij.util.TimeoutUtil
import com.intellij.webcore.util.ProcessOutputCatcher
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets


class GlintRunner {
    companion object {

        var glint: KillableColoredProcessHandler? = null
        val cache = HashMap<String, MutableList<JSLinterError>>()
        fun parseGlintText(glintText: String) {
            cache.clear()
            val lines = glintText.split("\n")
            var started = false
            var file = ""
            var text = ""
            var column = 0
            var line = 0
            val highlightSeverity = HighlightSeverity.ERROR
            val failedLines: MutableList<String> = mutableListOf()
            lines.forEach {
                val start = it.contains("- error TS")
                val end = it.contains("Watching for file changes")
                if (started && (start || end)) {
                    //end
                    started = false
                    val err = JSLinterError(line, column + 1, text, "glint", highlightSeverity)
                    cache[file] = cache.get(file) ?: mutableListOf()
                    cache[file]!!.add(err)
                }
                if (start) {
                    started = true
                    val parts = it.split(":")
                    file = parts[0]
                    line = parts[1].toInt()
                    column = parts[2].split(" ")[0].toInt()
                    text = parts[2].split(" ")[1]
                    return@forEach
                }
                if (!started) {
                    return@forEach
                }
                text += it + "\n"
            }
        }

        fun startGlint(project: Project, workingDir: VirtualFile) {
            if (glint != null) {
                return
            }
            val workDirectory = VfsUtilCore.virtualToIoFile(workingDir)
            val commandLine = GeneralCommandLine()
                    .withCharset(StandardCharsets.UTF_8)
                    .withParentEnvironmentType(ParentEnvironmentType.CONSOLE)
                    .withWorkDirectory(workDirectory)

            val glintPkg = NodeModuleManager.getInstance(project).collectVisibleNodeModules(workingDir).find { it.name == "@glint/core" }!!.virtualFile
            if (glintPkg == null) {
                return
            }
            val file = glintPkg.findFileByRelativePath("bin/glint.js")
            if (file == null) {
                return
            }
            commandLine.addParameter(file.path)
            commandLine.addParameter("--watch")

            NodeCommandLineConfigurator
                    .find(NodeJsInterpreterRef.createProjectRef().resolve(project)!!)
                    .configure(commandLine)

            val processHandler = KillableColoredProcessHandler(commandLine)
            processHandler.addProcessListener(object : ProcessListener {
                var currentText = ""
                override fun startNotified(event: ProcessEvent) {
                    currentText += event.text
                }

                override fun processTerminated(event: ProcessEvent) {
                    glint = null
                    startGlint(project, workingDir)
                }

                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    currentText += event.text
                    val ready = currentText.contains("Watching for file changes")
                    if (!ready) {
                        return
                    }
                    parseGlintText(currentText)
                    currentText = ""
                }
            })
            glint = processHandler
            processHandler.startNotify()
        }
    }
}

class TemplateLintExternalRunner(private val myIsOnTheFly: Boolean = false) {
    companion object {
        private val LOG = Logger.getInstance(TemplateLintExternalRunner::class.java)


        private fun templateLint(input: JSLinterInput<TemplateLintState>, sessionData: TemplateLintSessionData): JSLinterAnnotationResult {
            val startNanoTime = System.nanoTime()
            val result = runProcess(input, sessionData)
            logEnd(startNanoTime, result.errors.size)
            return result
        }

        private fun runProcess(input: JSLinterInput<TemplateLintState>, sessionData: TemplateLintSessionData): JSLinterAnnotationResult {
            try {
                val commandLine = createCommandLine(sessionData)
                logStart(sessionData, commandLine)
                val processHandler = KillableColoredProcessHandler(commandLine, false)
                try {
                    writeFileContentToStdin(processHandler, sessionData, commandLine.charset)
                } catch (error: IOException) {
                    LOG.warn(error)
                    val output = captureOutput(processHandler)
                    output.appendStderr("\n" + error.message + "\n")
                    return createFileLevelWarning(error.message!!, input, commandLine, processHandler, output)
                }
                val output = captureOutput(processHandler)
                val stderr = output.stderr
                if (!StringUtil.isEmptyOrSpaces(stderr)) {
                    return createFileLevelWarning(stderr, input, commandLine, processHandler, output)
                }
                val templateLintResultParser = TemplateLintResultParser()
                val stdout = output.stdout
                try {
                    val workDirectory = VfsUtilCore.virtualToIoFile(sessionData.workingDir)
                    val pathToLint = FileUtil.toSystemDependentName(sessionData.fileToLint.path)
                    val relativePath = FileUtil.getRelativePath(workDirectory.absolutePath, pathToLint, File.separatorChar)
                    val errors: List<JSLinterError> = GlintRunner.cache.getOrDefault(relativePath?.replace("\\", "/"), mutableListOf()) + (templateLintResultParser.parse(stdout) ?: listOf())

                    if (errors.isEmpty()) {
                        if (StringUtil.isEmptyOrSpaces(stdout)) {
                            return JSLinterAnnotationResult.createLinterResult(input, emptyList(), null as VirtualFile?)
                        }
                        return createFileLevelWarning(stdout, input, commandLine, processHandler, output)
                    }
                    return JSLinterAnnotationResult.createLinterResult(input, errors.toList(), null as VirtualFile?)
                } catch (exception: Exception) {
                    return createFileLevelWarning(exception.message!!, input, commandLine, processHandler, output)
                }
            } catch (executionException: ExecutionException) {
                return createFileLevelWarning(executionException.message!!, input)
            }
        }

        @Throws(IOException::class)
        private fun writeFileContentToStdin(processHandler: KillableColoredProcessHandler, sessionData: TemplateLintSessionData, charset: Charset) {
            //for projects using hbs-imports, filter out the imports, but keep empty lines
            val content = sessionData.fileToLintContent
            try {
                val stdin = ObjectUtils.assertNotNull(processHandler.processInput)
                var throwable: Throwable? = null
                try {
                    stdin.write(content.toByteArray(charset))
                    stdin.flush()
                } catch (t: Throwable) {
                    throwable = t
                    throw t
                } finally {
                    if (throwable != null) {
                        try {
                            stdin.close()
                        } catch (t: Throwable) {
                            throwable.addSuppressed(t)
                        }
                    } else {
                        stdin.close()
                    }
                }
            } catch (ioException: IOException) {
                throw IOException("Failed to write file content to stdin\n\n$content", ioException)
            }
        }

        @Throws(ExecutionException::class)
        private fun createCommandLine(sessionData: TemplateLintSessionData): GeneralCommandLine {
            val workDirectory = VfsUtilCore.virtualToIoFile(sessionData.workingDir)
            val commandLine = GeneralCommandLine()
                    .withCharset(StandardCharsets.UTF_8)
                    .withParentEnvironmentType(ParentEnvironmentType.CONSOLE)
                    .withWorkDirectory(workDirectory)

            sessionData.templateLintPackage
                    .addMainEntryJsFile(commandLine, sessionData.interpreter)

            if (sessionData.templateLintPackage.versionStr.split(".").first().toInt() >= 4) {
                commandLine.addParameter("--format=json")
            } else {
                commandLine.addParameter("--json")
            }

            val pathToLint = FileUtil.toSystemDependentName(sessionData.fileToLint.path)
            val relativePath = FileUtil.getRelativePath(workDirectory.absolutePath, pathToLint, File.separatorChar)

            if (relativePath != null) {
                commandLine.withParameters(
                        "--filename",
                        relativePath)
            }
            // TODO: else case?

            if (sessionData.fix) {
                commandLine.addParameter("--fix")
            }

            NodeCommandLineConfigurator
                    .find(sessionData.interpreter)
                    .configure(commandLine)
            return commandLine
        }

        private fun captureOutput(processHandler: BaseOSProcessHandler): ProcessOutput {
            val catcher = ProcessOutputCatcher(processHandler)
            catcher.startNotify()
            catcher.run()
            return catcher.output
        }

        private fun createFileLevelWarning(message: String, input: JSLinterInput<TemplateLintState>): JSLinterAnnotationResult {
            return JSLinterAnnotationResult.create(input, JSLinterFileLevelAnnotation(StringUtil.decapitalize(message)), null as VirtualFile?)
        }

        private fun createFileLevelWarning(message: String, input: JSLinterInput<TemplateLintState>, commandLine: GeneralCommandLine, processHandler: ProcessHandler, output: ProcessOutput): JSLinterAnnotationResult {
            val detailsAction = JsqtViewProcessOutputAction("Failed to lint " + input.virtualFile.path, EmberIcons.TEMPLATE_LINT_16, commandLine, processHandler, output)

            var (errorMessage) = StringUtil.splitByLines(message)
            errorMessage = StringUtil.trimStart(errorMessage!!, "Error: ")
            errorMessage = StringUtil.decapitalize(errorMessage)

            return JSLinterAnnotationResult
                    .create(input, JSLinterFileLevelAnnotation(errorMessage, detailsAction)
                            .withIcon(EmberIcons.TEMPLATE_LINT_16), null as VirtualFile?)
        }

        private fun logStart(sessionData: TemplateLintSessionData, commandLine: GeneralCommandLine) {
            val templateLintVersion = sessionData.templateLintPackage.versionStr
            val fileToLintPath = sessionData.fileToLint.path
            LOG.debug("Running ${TemplateLintUtil.PACKAGE_NAME}@${templateLintVersion} at $fileToLintPath ${commandLine.commandLineString}")
        }

        private fun logEnd(startTime: Long, resultSize: Int) {
            val durationMillis = TimeoutUtil.getDurationMillis(startTime)
            LOG.debug(TemplateLintBundle.message("hbs.lint.message.prefix") + " done in $durationMillis ms, found $resultSize problems")
        }
    }

    fun highlight(input: JSLinterInput<TemplateLintState>): JSLinterAnnotationResult? {
        return execute(input, false)
    }

    fun fixFile(input: JSLinterInput<TemplateLintState>) {
        execute(input, true)
    }

    private fun execute(input: JSLinterInput<TemplateLintState>, fix: Boolean): JSLinterAnnotationResult? {
        val fileToLint = input.virtualFile
        return if (fileToLint.isValid && fileToLint.parent != null) {
            try {
                val state = input.state
                val project = input.project
                if (project.isDisposed) {
                    null
                } else {
                    val workingDirectory = ReadAction.compute<VirtualFile?, RuntimeException> { TemplateLintUtil.findWorkingDirectory(fileToLint) }
                    if (workingDirectory == null) {
                        null
                    } else {
                        if (myIsOnTheFly) {
                            (ServiceManager.getService(project, TemplateLintConfigFileChangeTracker::class.java) as TemplateLintConfigFileChangeTracker).startIfNeeded()
                        }
                        val canRun = !myIsOnTheFly || ReadAction.compute<Boolean, RuntimeException> {
                            if (project.isDisposed) {
                                return@compute false
                            } else {
                                val configs = TemplateLintUtil.getPossibleConfigs(workingDirectory)
                                return@compute (ServiceManager.getService(project, TemplateLintUnsavedConfigFileManager::class.java) as TemplateLintUnsavedConfigFileManager).requestSaveIfNeeded(configs)
                            }
                        } as Boolean
                        if (!canRun) {
                            LOG.debug("TemplateLint postponed because of unsaved configs")
                            null
                        } else {
                            val interpreter = state.interpreterRef.resolveNotNull(input.project)
                            val error = JSLinterUtil.validateInterpreterAndPackage(input.project, interpreter, state.templateLintPackage, TemplateLintUtil.PACKAGE_NAME, fileToLint)
                            if (error != null) {
                                JSLinterAnnotationResult.create(input, error, null as VirtualFile?)
                            } else {
                                val templateLintPackage = TemplateLintPackage.fromNodePackage(input.project, state.templateLintPackage)
                                val sessionData = TemplateLintSessionData(interpreter, templateLintPackage, workingDirectory, fileToLint, input.fileContent, fix)
                                templateLint(input, sessionData)
                            }
                        }
                    }
                }
            } catch (executionException: ExecutionException) {
                createFileLevelWarning(executionException.message!!, input)
            }
        } else {
            null
        }
    }
}
