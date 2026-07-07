package app.opsmill.infrahub.toolwindow.server

import app.opsmill.infrahub.api.BranchCreateInput
import app.opsmill.infrahub.api.InfrahubClientManager
import app.opsmill.infrahub.api.BranchInfo
import app.opsmill.infrahub.common.ProjectTaskRunner
import app.opsmill.infrahub.common.SelectionDialogs
import app.opsmill.infrahub.settings.InfrahubSettingsState
import app.opsmill.infrahub.settings.InfrahubSettingsState.ServerConfig
import app.opsmill.infrahub.toolwindow.InfrahubProjectService
import app.opsmill.infrahub.visualizer.SchemaVisualizerPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBScrollPane
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.awt.Dimension
import javax.swing.*
import javax.swing.JOptionPane
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer

/**
 * Panel that displays a tree of servers and their branches.
 * Fetches data in background and refreshes every 10 seconds.
 */
class ServerTreePanel(private val project: Project) : JBScrollPane(), Disposable {

    private val serverTree = com.intellij.ui.treeStructure.Tree()
    private val model = ServerTreeModel(project, emptyList())
    private val panelScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentConfigs: List<ServerConfig> = emptyList()
    private val refreshTimer = javax.swing.Timer(10000) { refresh() }

    init {
        serverTree.model = model
        serverTree.setRootVisible(false)
        serverTree.cellRenderer = ServerTreeCellRenderer()
        serverTree.selectionModel.selectionMode = javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION

        installPopupMenu()

        viewport.view = serverTree
        preferredSize = Dimension(250, 400)

        // Start auto-refresh
        refreshTimer.start()
    }

    /**
     * Initialize the panel with current server configurations.
     */
    fun init() {
        currentConfigs = InfrahubSettingsState.getInstance().servers

        // Rebuild tree model with current configs
        val newModel = ServerTreeModel(project, currentConfigs)
        serverTree.model = newModel

        if (currentConfigs.isEmpty()) {
            return
        }

        refresh()
    }

    /**
     * Refresh server status and branches.
     */
    fun refresh() {
        currentConfigs = InfrahubSettingsState.getInstance().servers

        if (currentConfigs.isEmpty()) {
            return
        }

        // Refresh all servers in background
        currentConfigs.forEach { config ->
            val client = InfrahubClientManager.getInstance().getClient(config.name)
            if (client != null) {
                panelScope.launch {
                    try {
                        val version = client.getVersion()
                        val branches = client.getAllBranches()
                        ApplicationManager.getApplication().invokeLater {
                            model.updateServer(config.name, online = true, version = version, branches = branches)
                        }
                    } catch (e: Exception) {
                        ApplicationManager.getApplication().invokeLater {
                            model.updateServer(config.name, online = false, version = null, branches = emptyList())
                        }
                    }
                }
            }
        }
    }

    /**
     * Refresh a specific server.
     */
    fun refreshServer(serverName: String) {
        panelScope.launch {

            val client = InfrahubClientManager.getInstance().getClient(serverName)
            if (client != null) {
                try {
                    val version = client.getVersion()
                    val branches = client.getAllBranches()
                    ApplicationManager.getApplication().invokeLater {
                        model.updateServer(serverName, online = true, version = version, branches = branches)
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        model.updateServer(serverName, online = false, version = null, branches = emptyList())
                    }
                }
            }
        }
    }

    private fun installPopupMenu() {
        val popupMenu = JPopupMenu()
        popupMenu.add(JMenuItem("Refresh").apply {
            addActionListener { refresh() }
        })
        popupMenu.addSeparator()
        popupMenu.add(JMenuItem("New Branch").apply {
            addActionListener { getSelectedServerNode()?.let { createBranch(it) } }
        })
        popupMenu.add(JMenuItem("Visualize Schema").apply {
            addActionListener { getSelectedServerNode()?.takeIf { it.online }?.let { visualizeSchema(it) } }
        })
        popupMenu.add(JMenuItem("Delete Branch").apply {
            addActionListener {
                val selectedBranch = getSelectedBranchNode() ?: return@addActionListener
                val selectedServer = getSelectedServerFromBranch() ?: return@addActionListener
                deleteBranch(selectedServer, selectedBranch)
            }
        })
        serverTree.componentPopupMenu = popupMenu
    }

    private fun getSelectedServerNode(): ServerTreeNodeData? {
        val node = serverTree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return null
        return node.userObject as? ServerTreeNodeData
    }

    private fun getSelectedBranchNode(): BranchTreeNodeData? {
        val node = serverTree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return null
        return node.userObject as? BranchTreeNodeData
    }

    private fun getSelectedServerFromBranch(): ServerTreeNodeData? {
        val node = serverTree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return null
        val parent = node.parent as? DefaultMutableTreeNode ?: return null
        return parent.userObject as? ServerTreeNodeData
    }

    private fun createBranch(serverData: ServerTreeNodeData) {
        val branchName = Messages.showInputDialog(
            project,
            "Enter new branch name",
            "New Branch",
            Messages.getInformationIcon(),
            "",
            null
        )?.trim()

        if (branchName.isNullOrEmpty()) {
            return
        }
        if (!branchName.matches(Regex("^[-\\w/]+$"))) {
            Messages.showErrorDialog(project, "Invalid branch name.", "Infrahub")
            return
        }

        val description = Messages.showInputDialog(
            project,
            "Enter branch description (optional)",
            "New Branch",
            Messages.getInformationIcon(),
            "",
            null
        )?.trim().orEmpty()

        val syncWithGit = Messages.showYesNoDialog(
            project,
            "Sync with Git remote?",
            "New Branch",
            Messages.getQuestionIcon()
        ) == JOptionPane.YES_OPTION

        val confirmed = Messages.showYesNoDialog(
            project,
            "Create branch '$branchName' on server '${serverData.config.name}'?",
            "Confirm Branch Creation",
            Messages.getQuestionIcon()
        )
        if (confirmed != JOptionPane.YES_OPTION) {
            return
        }

        object : Task.Backgroundable(project, "Creating branch '$branchName'...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                try {
                    val client = InfrahubClientManager.getInstance().getClient(serverData.config.name)
                        ?: throw IllegalStateException("No client available for server: ${serverData.config.name}")
                    runBlocking {
                        client.createBranch(
                            BranchCreateInput(
                                name = branchName,
                                description = description.ifBlank { null },
                                sync_with_git = syncWithGit
                            )
                        )
                    }
                    ProjectTaskRunner.onUiThread {
                        Messages.showInfoMessage(project, "Branch '$branchName' created successfully.", "Infrahub")
                        InfrahubProjectService.getService(project).refreshServerTree()
                    }
                } catch (e: Exception) {
                    ProjectTaskRunner.onUiThread {
                        Messages.showErrorDialog(project, e.message ?: "Failed to create branch", "Infrahub")
                    }
                }
            }
        }.queue()
    }

    private fun deleteBranch(serverData: ServerTreeNodeData, branchData: BranchTreeNodeData) {
        if (branchData.branch.is_default) {
            Messages.showErrorDialog(project, "Cannot delete the default branch '${branchData.branch.name}'.", "Infrahub")
            return
        }

        val confirmed = Messages.showYesNoDialog(
            project,
            "Are you sure you want to delete branch '${branchData.branch.name}' from '${serverData.config.name}'?\n\nThis action cannot be undone.",
            "Delete Branch",
            Messages.getWarningIcon()
        )
        if (confirmed != JOptionPane.YES_OPTION) {
            return
        }

        object : Task.Backgroundable(project, "Deleting branch '${branchData.branch.name}'...", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                try {
                    val client = InfrahubClientManager.getInstance().getClient(serverData.config.name)
                        ?: throw IllegalStateException("No client available for server: ${serverData.config.name}")
                    val success = runBlocking { client.deleteBranch(branchData.branch.name) }
                    ProjectTaskRunner.onUiThread {
                        if (success) {
                            Messages.showInfoMessage(project, "Branch '${branchData.branch.name}' deleted successfully.", "Infrahub")
                            InfrahubProjectService.getService(project).refreshServerTree()
                        } else {
                            Messages.showErrorDialog(project, "Failed to delete branch '${branchData.branch.name}'.", "Infrahub")
                        }
                    }
                } catch (e: Exception) {
                    ProjectTaskRunner.onUiThread {
                        Messages.showErrorDialog(project, e.message ?: "Failed to delete branch", "Infrahub")
                    }
                }
            }
        }.queue()
    }

    private fun visualizeSchema(serverData: ServerTreeNodeData) {
        val client = InfrahubClientManager.getInstance().getClient(serverData.config.name)
        if (client == null) {
            Messages.showErrorDialog(project, "No client available for server: ${serverData.config.name}", "Infrahub")
            return
        }

        ProjectTaskRunner.runBackground(project, "Load Infrahub branches") {
            try {
                val branches = runBlocking { client.getAllBranches() }
                showSchemaBranchPicker(serverData, client, branches)
            } catch (e: Exception) {
                ProjectTaskRunner.onUiThread {
                    Messages.showErrorDialog(project, e.message ?: "Failed to visualize schema", "Infrahub")
                }
            }
        }
    }

    private fun showSchemaBranchPicker(
        serverData: ServerTreeNodeData,
        client: app.opsmill.infrahub.api.InfrahubClient,
        branches: List<BranchInfo>
    ) {
        val branchNames = branches.map { it.name }
        if (branchNames.isEmpty()) {
            ProjectTaskRunner.onUiThread {
                Messages.showErrorDialog(project, "No branches found for server: ${serverData.config.name}", "Infrahub")
            }
            return
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
                        SchemaVisualizerPanel.show(project, schema, serverData.config.name, branchName)
                    }
                } catch (e: Exception) {
                    ProjectTaskRunner.onUiThread {
                        Messages.showErrorDialog(project, e.message ?: "Failed to visualize schema", "Infrahub")
                    }
                }
            }
        }
    }

    override fun dispose() {
        refreshTimer.stop()
        panelScope.cancel()
    }
}

/**
 * Custom cell renderer for server and branch nodes.
 */
class ServerTreeCellRenderer : DefaultTreeCellRenderer() {

    override fun getTreeCellRendererComponent(
        tree: JTree?,
        value: Any?,
        sel: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): java.awt.Component {

        val component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)

        when (value) {
            is DefaultMutableTreeNode -> {
                val userObject = value.userObject
                when (userObject) {
                    is ServerTreeNodeData -> {
                        icon = if (userObject.online) AllIcons.General.Web else AllIcons.General.Error
                        text = buildString {
                            append(userObject.config.name)
                            append(if (userObject.online) " \u2705" else " \u274C")
                            userObject.version?.let { append(" ($it)") }
                        }
                    }
                    is BranchTreeNodeData -> {
                        val branch = userObject.branch
                        icon = getBranchIcon(branch)
                        text = buildString {
                            append(branch.name)
                            if (branch.is_default) append(" [default]")
                            if (branch.sync_with_git) append(" [git]")
                        }
                    }
                }
            }
        }

        return component
    }
}
