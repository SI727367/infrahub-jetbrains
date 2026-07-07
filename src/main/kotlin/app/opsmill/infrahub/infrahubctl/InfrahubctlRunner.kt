package app.opsmill.infrahub.infrahubctl

import app.opsmill.infrahub.api.InfrahubClientManager
import app.opsmill.infrahub.common.SelectionDialogs
import app.opsmill.infrahub.settings.InfrahubSettingsState
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.runBlocking
import java.io.File

data class SelectedServerBranch(
    val serverName: String,
    val serverAddress: String,
    val token: String,
    val branchName: String
)

object InfrahubctlRunner {

    fun runSchemaCommand(project: Project, schemaPath: File, action: String) {
        if (!checkInfrahubctlBeforeCommand(project)) {
            return
        }

        val selected = promptServerAndBranch(project) ?: return
        val workingDirectory = if (schemaPath.isDirectory) schemaPath else schemaPath.parentFile
        runCommand(
            project,
            selected,
            workingDirectory,
            "schema",
            action,
            schemaPath.absolutePath,
            "--branch",
            selected.branchName
        )
    }

    fun runTransformCommand(project: Project, transformName: String, workingDirectory: File) {
        if (!checkInfrahubctlBeforeCommand(project)) {
            return
        }

        val selected = promptServerAndBranch(project) ?: return
        runCommand(
            project,
            selected,
            workingDirectory,
            "transform",
            "run",
            transformName,
            "--branch",
            selected.branchName
        )
    }

    fun checkInfrahubctlBeforeCommand(project: Project): Boolean {
        val settings = InfrahubSettingsState.getInstance()
        if (!settings.showInfrahubctlWarnings) {
            return true
        }

        val checker = InfrahubctlChecker()
        val result = checker.checkAvailability()
        if (result.isAvailable) {
            return true
        }

        val choice = Messages.showYesNoCancelDialog(
            project,
            "infrahubctl is required for this operation but was not found. ${result.errorMessage ?: "Not found"}\n\n${checker.getInstallationGuidance()}",
            "Infrahub",
            "Install Guide",
            "Continue Anyway",
            "Cancel",
            Messages.getWarningIcon()
        )

        return when (choice) {
            Messages.YES -> {
                com.intellij.ide.BrowserUtil.browse("https://docs.infrahub.app/python-sdk/guides/installation")
                false
            }
            Messages.NO -> true
            else -> false
        }
    }

    fun promptServerAndBranch(project: Project): SelectedServerBranch? {
        val servers = InfrahubSettingsState.getInstance().servers
        if (servers.isEmpty()) {
            Messages.showErrorDialog(project, "No Infrahub servers configured.", "Infrahub")
            return null
        }

        val serverName = SelectionDialogs.chooseString(
            project,
            "Infrahub - Select server",
            servers.map { it.name }
        ) ?: return null

        val server = servers.firstOrNull { it.name == serverName } ?: return null
        val client = InfrahubClientManager.getInstance().getClient(server.name)
        if (client == null) {
            Messages.showErrorDialog(project, "No client available for server: ${server.name}", "Infrahub")
            return null
        }

        return try {
            val branches = runBlocking { client.getAllBranches() }
            val branchNames = branches.map { it.name }
            if (branchNames.isEmpty()) {
                Messages.showErrorDialog(project, "No branches found for server: ${server.name}", "Infrahub")
                null
            } else {
                val branchName = SelectionDialogs.chooseString(
                    project,
                    "Infrahub - Select branch",
                    branchNames
                )
                if (branchName == null) {
                    null
                } else {
                    SelectedServerBranch(
                        serverName = server.name,
                        serverAddress = server.address,
                        token = server.apiToken,
                        branchName = branchName
                    )
                }
            }
        } catch (e: Exception) {
            Messages.showErrorDialog(project, e.message ?: "Failed to load branches", "Infrahub")
            null
        }
    }

    fun runCommand(
        project: Project,
        selected: SelectedServerBranch,
        workingDirectory: File,
        vararg args: String
    ) {
        val checker = InfrahubctlChecker()
        val commandPath = checker.getInfrahubctlPath() ?: "infrahubctl"

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val commandLine = GeneralCommandLine(listOf(commandPath) + args.toList())
                    .withWorkDirectory(workingDirectory)
                    .withEnvironment("INFRAHUB_ADDRESS", selected.serverAddress)
                    .withEnvironment("INFRAHUB_API_TOKEN", selected.token)

                if (selected.token.isBlank()) {
                    commandLine.environment.remove("INFRAHUB_API_TOKEN")
                }

                val output = CapturingProcessHandler(commandLine).runProcess()
                ApplicationManager.getApplication().invokeLater {
                    if (output.exitCode == 0) {
                        Messages.showInfoMessage(
                            project,
                            output.stdout.ifBlank { "Command completed successfully." },
                            "Infrahub"
                        )
                    } else {
                        Messages.showErrorDialog(
                            project,
                            output.stderr.ifBlank { output.stdout.ifBlank { "Command failed." } },
                            "Infrahub"
                        )
                    }
                }
            } catch (e: ExecutionException) {
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(project, e.message ?: "Failed to run infrahubctl", "Infrahub")
                }
            }
        }
    }
}
