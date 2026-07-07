package app.opsmill.infrahub.actions

import app.opsmill.infrahub.api.BranchCreateInput
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
 * Action to create a new branch in Infrahub.
 * Equivalent to VSCode's newBranchCommand.
 *
 * Workflow:
 * 1. Prompt for server selection (if not pre-selected)
 * 2. Prompt for branch name (validated, non-empty, alphanumeric with [-\w/])
 * 3. Prompt for optional description
 * 4. Prompt for "Sync with Git" option
 * 5. Confirm before creating
 * 6. Run createBranch in background task with progress
 * 7. Refresh server tree on success
 */
class NewBranchAction : AnAction(
    "New Branch",
    "Create a new Infrahub branch",
    com.intellij.icons.AllIcons.General.Add
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

        // Step 2: Prompt for branch name with validation loop
        var nameResult: String? = null
        while (nameResult == null) {
            val input = Messages.showInputDialog(
                project,
                "Enter new branch name",
                "Create Branch",
                Messages.getInformationIcon(),
                "",
                null
            )

            if (input == null) {
                return // Cancelled
            }

            when {
                input.isEmpty() -> {
                    Messages.showErrorDialog(project, "Branch name cannot be empty.", "Invalid Name")
                }
                !input.matches(Regex("^[-\\w/]+$")) -> {
                    Messages.showErrorDialog(
                        project,
                        "Invalid characters. Only alphanumeric, hyphens, underscores, and forward slashes.",
                        "Invalid Name"
                    )
                }
                else -> nameResult = input
            }
        }

        // Step 3: Prompt for optional description
        val description = Messages.showInputDialog(
            project,
            "Enter branch description (optional)",
            "Create Branch",
            Messages.getInformationIcon(),
            "",
            null
        ) ?: ""

        // Step 4: Prompt for sync with Git option
        val syncWithGit = Messages.showYesNoDialog(
            project,
            "Sync with Git remote?",
            "Sync with Git",
            Messages.getOkButton(),
            Messages.getCancelButton(),
            Messages.getQuestionIcon()
        ) == JOptionPane.YES_OPTION

        // Step 5: Confirm before creating
        val confirmResult = Messages.showYesNoDialog(
            project,
            "Create branch '$nameResult'?",
            "Confirm Branch Creation",
            Messages.getOkButton(),
            Messages.getCancelButton(),
            Messages.getQuestionIcon()
        )

        if (confirmResult != JOptionPane.YES_OPTION) {
            return
        }

        // Step 6: Execute createBranch in background task with progress
        object : Task.Backgroundable(project, "Creating branch '$nameResult'...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        val input = BranchCreateInput(
                            name = nameResult,
                            description = if (description.isEmpty()) null else description,
                            sync_with_git = syncWithGit
                        )

                        val branchId = InfrahubClientManager.getInstance().getClient(selectedServer.name)
                            ?.createBranch(input)

                        if (branchId != null) {
                            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                                Messages.showInfoMessage(
                                    project,
                                    "Branch '$nameResult' created successfully.",
                                    "Success"
                                )
                                val projectService = InfrahubProjectService.getService(project)
                                projectService.refreshServerTree()
                            }
                        } else {
                            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                                Messages.showErrorDialog(
                                    project,
                                    "Branch creation returned no ID. Check server logs.",
                                    "Creation Failed"
                                )
                            }
                        }
                    } catch (ex: Exception) {
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            Messages.showErrorDialog(
                                project,
                                "Error creating branch '$nameResult': ${ex.message}",
                                "Creation Error"
                            )
                        }
                    }
                }
            }
        }.queue()
    }
}
