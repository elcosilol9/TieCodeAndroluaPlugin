package com.dingyi.tiecode.plugin.androlua.build.task

import com.dingyi.tiecode.plugin.androlua.build.base.LuaTask
import com.dingyi.tiecode.plugin.androlua.build.base.createKeyValueData
import com.dingyi.tiecode.plugin.androlua.data.KeyValueData
import com.dingyi.tiecode.plugin.androlua.ktx.md5
import com.dingyi.tiecode.plugin.androlua.ktx.substringPath
import com.tiecode.plugin.api.log.Logger
import com.tiecode.plugin.api.log.model.TieLogMessage
import com.tiecode.plugin.api.project.model.Project
import com.tiecode.plugin.api.project.task.define.BuildVariant
import com.tiecode.plugin.api.project.task.define.TaskPerformState
import java.io.File

class CompileLuaTask : LuaTask() {

    private lateinit var compileLuaDir: File

    private lateinit var sourceDir: File

    private lateinit var needCompileFiles: List<File>


    private lateinit var compileHistoryData: KeyValueData<String, String>

    override fun initialize(
        project: Project,
        buildVariant: BuildVariant,
        logger: Logger
    ): TaskPerformState {
        super.initialize(project, buildVariant, logger)

        compileLuaDir = getBuildDir("compiled")

        sourceDir = project.projectDir.resolve("src")

        compileHistoryData = createKeyValueData("compileHistory")

        return checkCompileFiles()

    }

    private fun checkCompileFiles(): TaskPerformState {

        val allSourceFile = sourceDir
            .walkBottomUp()
            .filter { it.isFile && (it.extension == "aly" || it.extension == "lua") }
            .toList()


        needCompileFiles = allSourceFile
            .filter { file ->
                val targetMD5 = file.md5
                val historyMD5 = compileHistoryData.getOrPut(file.absolutePath, "")

                targetMD5 != historyMD5.also {
                    compileHistoryData.put(file.absolutePath, targetMD5)
                }

            }


        return when {
            allSourceFile.isEmpty() && needCompileFiles.isEmpty() -> TaskPerformState.NO_SOURCE
            allSourceFile.size == needCompileFiles.size -> TaskPerformState.NORMAL
            needCompileFiles.isNotEmpty() && allSourceFile.isNotEmpty() -> TaskPerformState.INCREMENTAL
            allSourceFile.isNotEmpty() -> TaskPerformState.UP_TO_DATE
            else -> TaskPerformState.NORMAL
        }

    }


    //TODO:编译aly
    private fun compileAly(file: File): File {
        val filePathWithoutSourceDir = file.substringPath(sourceDir)

        val targetCode = """
            local layout = ${file.readText()}
            return layout
        """.trimIndent()

        val targetFile = compileLuaDir.resolve(filePathWithoutSourceDir)

        try {

            targetFile.createNewFile()

            targetFile.writeText(targetCode)

        } catch (e: Exception) {
            runOnUiThread {

                logger.postLog(
                    TieLogMessage(
                        "编译${filePathWithoutSourceDir}时出错：",
                        TieLogMessage.ERROR
                    )
                )

                logger.postTrace(e)
            }
        }

        return targetFile
    }

    //TODO:编译lua
    private fun compileLua(file: File): File {

        val filePathWithoutSourceDir = file.substringPath(sourceDir)

        val targetFile = compileLuaDir.resolve(filePathWithoutSourceDir)

        try {

            file.copyTo(targetFile, true)

        } catch (e: Exception) {
            runOnUiThread {

                logger.postLog(
                    TieLogMessage(
                        "编译${filePathWithoutSourceDir}时出错：",
                        TieLogMessage.ERROR
                    )
                )

                logger.postTrace(e)
            }
        }

        return targetFile

    }


    override fun perform(): Boolean {
        try {
            needCompileFiles.map {
                when (it.extension) {
                    "lua" -> compileLua(it)
                    "aly" -> compileAly(it)
                    else -> {}
                }
            }
            compileHistoryData.updateAll()
            return true
        } catch (e: Exception) {
            runOnUiThread {

                logger.postLog(
                    TieLogMessage(
                        "无法编译lua代码，项目构建失败",
                        TieLogMessage.ERROR
                    )
                )

                logger.postTrace(e)
            }

            return false
        }

    }


    override fun getName() = "编译Lua和Aly代码"

}