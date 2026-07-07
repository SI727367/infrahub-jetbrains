package app.opsmill.infrahub.actions

import app.opsmill.infrahub.infrahubctl.InfrahubctlRunner
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import java.io.File

class RunTransformAction : AnAction("Run Transform") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (!InfrahubctlRunner.checkInfrahubctlBeforeCommand(project)) {
            return
        }

        val selected = InfrahubctlRunner.promptServerAndBranch(project) ?: return
        val transformName = Messages.showInputDialog(
            project,
            "Enter transform name",
            "Run Transform",
            null
        )?.trim()

        if (transformName.isNullOrEmpty()) {
            return
        }

        val basePath = project.basePath ?: return
        InfrahubctlRunner.runCommand(
            project,
            selected,
            File(basePath),
            "transform",
            "run",
            transformName,
            "--branch",
            selected.branchName
        )
    }
}
