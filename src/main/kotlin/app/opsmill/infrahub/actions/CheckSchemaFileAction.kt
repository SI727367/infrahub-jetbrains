package app.opsmill.infrahub.actions

import app.opsmill.infrahub.infrahubctl.InfrahubctlRunner
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

class CheckSchemaFileAction : AnAction("Check Schema File") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (!InfrahubctlRunner.checkInfrahubctlBeforeCommand(project)) {
            return
        }

        val selected = InfrahubctlRunner.promptServerAndBranch(project) ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)?.takeIf(::isSchemaFile) ?: return
        val file = File(virtualFile.path)
        InfrahubctlRunner.runCommand(project, selected, file.parentFile, "schema", "check", file.absolutePath, "--branch", selected.branchName)
    }

    override fun update(e: AnActionEvent) {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = virtualFile != null && isSchemaFile(virtualFile)
    }

    private fun isSchemaFile(virtualFile: VirtualFile): Boolean {
        return !virtualFile.isDirectory && (virtualFile.extension == "yml" || virtualFile.extension == "yaml")
    }
}
