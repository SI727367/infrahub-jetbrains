package app.opsmill.infrahub.actions

import app.opsmill.infrahub.infrahubctl.InfrahubctlRunner
import app.opsmill.infrahub.toolwindow.schema.SchemaTreeParser
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class LoadAllSchemaFilesAction : AnAction("Load All Schema Files") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (!InfrahubctlRunner.checkInfrahubctlBeforeCommand(project)) {
            return
        }

        val schemaDirectory = SchemaTreeParser(project).resolveSchemaDirectory() ?: return
        val selected = InfrahubctlRunner.promptServerAndBranch(project) ?: return
        InfrahubctlRunner.runCommand(project, selected, schemaDirectory, "schema", "load", schemaDirectory.absolutePath, "--branch", selected.branchName)
    }
}
