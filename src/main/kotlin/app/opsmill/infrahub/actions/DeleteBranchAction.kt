package app.opsmill.infrahub.actions

import app.opsmill.infrahub.api.InfrahubClientManager
import app.opsmill.infrahub.settings.InfrahubSettingsState
import app.opsmill.infrahub.toolwindow.InfrahubProjectService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.*
import javax.swing.JOptionPane

/**
 * Action to delete a branch from Infrahub.
 * Equivalent to VSCode's deleteBranchCommand.
 *
 * Workflow:
 * 1. Prompt for server selection
 * 2. Fetch branches from selected server
 * 3. Block deletion of default branch
 * 4. Show branch picker for selection
 * 5. Confirm deletion with warning
 * 6. Run deleteBranch in background task
 * 7. Refresh server tree on success
 */
class DeleteBranchAction : AnAction(
    "Delete Branch",
    "Delete an Infrahub branch",
    com.intellij.icons.AllIcons.Actions.GC
) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = InfrahubSettingsState.getInstance()

        if (settings.servers.isEmpty()) {
            Messages.showErrorDialog(
                project,
                "No Infrahub servers configured.",
                "Server Configuration Required"
            )
            return
        }

        // Step 1: Prompt for server selection
        val serverNames = settings.servers.map { it.name }.toTypedArray()
        val selectedIndex = Messages.showChooseDialog(
            project,
            "Select Infrahub server:",
            "Select Server",
            Messages.getInformationIcon(),
            serverNames,
            serverNames[0]
        )
        if (selectedIndex < 0) return
        val selectedServerName = serverNames[selectedIndex]

        val selectedServer = settings.servers.find { it.name == selectedServerName }
        if (selectedServer == null) {
            Messages.showErrorDialog(
                project,
                "Invalid server name '$selectedServerName'.",
                "Server Not Found"
            )
            return
        }

        val client = InfrahubClientManager.getInstance().getClient(selectedServer.name) ?: run {
            Messages.showErrorDialog(
                project,
                "Failed to connect to server '$selectedServerName'.",
                "Connection Error"
            )
            return
        }

        // Step 2: Fetch branches in background task with progress
        object : Task.Backgroundable(project, "Fetching branches...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        val branches = client.getAllBranches()

                        if (branches.isEmpty()) {
                            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                                Messages.showInfoMessage(
                                    project,
                                    "No branches found for this server.",
                                    "No Branches"
                                )
                            }
                            return@launch
                        }

                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            // Step 3: Show branch selection
                            val branchNames = branches.map { it.name }.toTypedArray()
                            val branchIndex = Messages.showChooseDialog(
                                project,
                                "Select branch to delete:",
                                "Select Branch",
                                Messages.getInformationIcon(),
                                branchNames,
                                branchNames[0]
                            )
                            if (branchIndex < 0) return@invokeLater
                            val selectedBranchName = branchNames[branchIndex]

                            val selectedBranch = branches.find { it.name == selectedBranchName }
                            if (selectedBranch == null) {
                                Messages.showErrorDialog(
                                    project,
                                    "Invalid branch name '$selectedBranchName'.",
                                    "Branch Not Found"
                                )
                                return@invokeLater
                            }

                            // Block deletion of default branch
                            if (selectedBranch.is_default) {
                                Messages.showErrorDialog(
                                    project,
                                    "Cannot delete the default branch '${selectedBranch.name}'.",
                                    "Cannot Delete Default Branch"
                                )
                                return@invokeLater
                            }

                            // Step 4: Confirm deletion with warning
                            val confirmResult = Messages.showYesNoDialog(
                                project,
                                "Are you sure you want to delete branch '${selectedBranch.name}'?\n\nThis action cannot be undone.",
                                "Delete Branch",
                                Messages.getOkButton(),
                                Messages.getCancelButton(),
                                Messages.getWarningIcon()
                            )

                            if (confirmResult != JOptionPane.YES_OPTION) {
                                return@invokeLater
                            }

                            // Step 5: Execute delete in another background task with progress
                            object : Task.Backgroundable(project, "Deleting branch '${selectedBranch.name}'...", true) {
                                override fun run(indicator: ProgressIndicator) {
                                    indicator.isIndeterminate = true

                                    GlobalScope.launch(Dispatchers.IO) {
                                        try {
                                            val success = client.deleteBranch(selectedBranch.name)

                                            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                                                val projectService = InfrahubProjectService.getService(project)
                                                projectService.refreshServerTree()

                                                if (success) {
                                                    Messages.showInfoMessage(
                                                        project,
                                                        "Branch '${selectedBranch.name}' deleted successfully.",
                                                        "Success"
                                                    )
                                                } else {
                                                    Messages.showErrorDialog(
                                                        project,
                                                        "Failed to delete branch '${selectedBranch.name}'. The operation returned an error.",
                                                        "Deletion Failed"
                                                    )
                                                }
                                            }
                                        } catch (ex: Exception) {
                                            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                                                Messages.showErrorDialog(
                                                    project,
                                                    "Error deleting branch '${selectedBranch.name}': ${ex.message}",
                                                    "Deletion Error"
                                                )
                                            }
                                        }
                                    }
                                }
                            }.queue()
                        }
                    } catch (ex: Exception) {
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            Messages.showErrorDialog(
                                project,
                                "Failed to fetch branches: ${ex.message}",
                                "Fetch Error"
                            )
                        }
                    }
                }
            }
        }.queue()
    }
}
