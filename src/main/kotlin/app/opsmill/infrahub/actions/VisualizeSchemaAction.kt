package app.opsmill.infrahub.actions

import app.opsmill.infrahub.api.InfrahubClientManager
import app.opsmill.infrahub.common.ProjectTaskRunner
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

        val serverNames = servers.map { it.name }.toTypedArray()
        val serverIndex = Messages.showChooseDialog(
            project,
            "Select Infrahub server",
            "Visualize Schema",
            null,
            serverNames,
            serverNames.first()
        )
        if (serverIndex < 0) {
            return
        }
        val serverName = serverNames[serverIndex]
        val client = InfrahubClientManager.getInstance().getClient(serverName)
        if (client == null) {
            Messages.showErrorDialog(project, "No client available for server: $serverName", "Infrahub")
            return
        }

        ProjectTaskRunner.runBackground(project, "Load Infrahub branches") {
            try {
                val branches = runBlocking { client.getAllBranches() }
                val branchNames = branches.map { it.name }.toTypedArray()
                if (branchNames.isEmpty()) {
                    showError(project, "No branches found for server: $serverName")
                    return@runBackground
                }

                ProjectTaskRunner.onUiThread {
                    val branchIndex = Messages.showChooseDialog(
                        project,
                        "Select branch",
                        "Visualize Schema",
                        null,
                        branchNames,
                        branchNames.firstOrNull()
                    )
                    if (branchIndex < 0) {
                        return@onUiThread
                    }
                    val branchName = branchNames[branchIndex]

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
