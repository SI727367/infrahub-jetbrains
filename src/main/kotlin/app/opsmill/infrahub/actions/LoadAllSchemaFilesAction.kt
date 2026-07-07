package app.opsmill.infrahub.actions

import app.opsmill.infrahub.infrahubctl.InfrahubctlRunner
import app.opsmill.infrahub.toolwindow.schema.SchemaTreeParser
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vfs.VirtualFile

class LoadAllSchemaFilesAction : AnAction("Load All Schema Files") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (!InfrahubctlRunner.checkInfrahubctlBeforeCommand(project)) {
            return
        }

        val schemaDirectory = resolveSchemaTarget(e) ?: SchemaTreeParser(project).resolveSchemaDirectory() ?: return
        val selected = InfrahubctlRunner.promptServerAndBranch(project) ?: return
        InfrahubctlRunner.runCommand(project, selected, schemaDirectory, "schema", "load", schemaDirectory.absolutePath, "--branch", selected.branchName)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && resolveSchemaTarget(e) != null
    }

    private fun resolveSchemaTarget(e: AnActionEvent): java.io.File? {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return e.project?.let { SchemaTreeParser(it).resolveSchemaDirectory() }
        return virtualFile.takeIf(::isSchemaTarget)?.let { java.io.File(it.path) }
    }

    private fun isSchemaTarget(virtualFile: VirtualFile): Boolean {
        return virtualFile.isDirectory || virtualFile.extension == "yml" || virtualFile.extension == "yaml"
    }
}
