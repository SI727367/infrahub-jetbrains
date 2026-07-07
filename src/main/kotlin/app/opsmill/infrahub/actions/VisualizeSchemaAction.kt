package app.opsmill.infrahub.actions

import app.opsmill.infrahub.api.InfrahubClientManager
import app.opsmill.infrahub.common.ProjectTaskRunner
import app.opsmill.infrahub.common.SelectionDialogs
import app.opsmill.infrahub.settings.InfrahubSettingsState
import app.opsmill.infrahub.visualizer.SchemaVisualizerPanel
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.runBlocking

class VisualizeSchemaAction : AnAction("Visualize Schema") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val servers = InfrahubSettingsState.getInstance().servers
        if (servers.isEmpty()) {
            Messages.showErrorDialog(project, "No Infrahub servers configured.", "Infrahub")
            return
        }

        val serverName = SelectionDialogs.chooseString(
            project,
            "Visualize Schema - Select Infrahub server",
            servers.map { it.name }
        ) ?: return
        val client = InfrahubClientManager.getInstance().getClient(serverName)
        if (client == null) {
            Messages.showErrorDialog(project, "No client available for server: $serverName", "Infrahub")
            return
        }

        ProjectTaskRunner.runBackground(project, "Load Infrahub branches") {
            try {
                val branches = runBlocking { client.getAllBranches() }
                val branchNames = branches.map { it.name }
                if (branchNames.isEmpty()) {
                    showError(project, "No branches found for server: $serverName")
                    return@runBackground
                }

                ProjectTaskRunner.onUiThread {
                    val branchName = SelectionDialogs.chooseString(
                        project,
                        "Visualize Schema - Select branch",
                        branchNames
                    ) ?: return@onUiThread

                    ProjectTaskRunner.runBackground(project, "Fetch Infrahub schema") {
                        try {
                            val schema = runBlocking { client.getSchema(branchName) }
                            ProjectTaskRunner.onUiThread {
                                SchemaVisualizerPanel.show(project, schema, serverName, branchName)
                            }
                        } catch (ex: Exception) {
                            showError(project, ex.message ?: "Failed to fetch schema")
                        }
                    }
                }
            } catch (ex: Exception) {
                showError(project, ex.message ?: "Failed to load branches")
            }
        }
    }

    private fun showError(project: Project, message: String) {
        ApplicationManager.getApplication().invokeLater {
            Messages.showErrorDialog(project, message, "Infrahub")
        }
    }
}
