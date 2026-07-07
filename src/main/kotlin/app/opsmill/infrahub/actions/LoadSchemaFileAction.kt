package app.opsmill.infrahub.actions

import app.opsmill.infrahub.infrahubctl.InfrahubctlRunner
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import java.io.File

class LoadSchemaFileAction : AnAction("Load Schema File") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (!InfrahubctlRunner.checkInfrahubctlBeforeCommand(project)) {
            return
        }

        val selected = InfrahubctlRunner.promptServerAndBranch(project) ?: return
        val virtualFile = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE) ?: return
        val file = File(virtualFile.path)
        InfrahubctlRunner.runCommand(project, selected, file.parentFile, "schema", "load", file.absolutePath, "--branch", selected.branchName)
    }
}
