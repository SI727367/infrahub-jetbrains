package app.opsmill.infrahub.toolwindow.server

import app.opsmill.infrahub.api.InfrahubClientManager
import app.opsmill.infrahub.settings.InfrahubSettingsState
import app.opsmill.infrahub.settings.InfrahubSettingsState.ServerConfig
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.*
import java.awt.Dimension
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer

/**
 * Panel that displays a tree of servers and their branches.
 * Fetches data in background and refreshes every 10 seconds.
 */
class ServerTreePanel(private val project: Project) : JBScrollPane(), Disposable {

    private val serverTree = com.intellij.ui.treeStructure.Tree()
    private val model = ServerTreeModel(project, emptyList())
    private var currentConfigs: List<ServerConfig> = emptyList()
    private val refreshTimer = javax.swing.Timer(10000) { refresh() }

    init {
        serverTree.model = model
        serverTree.setRootVisible(false)
        serverTree.cellRenderer = ServerTreeCellRenderer()
        serverTree.selectionModel.selectionMode = javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION

        // Add context menu
        val popupMenu = JPopupMenu()
        popupMenu.add(JMenuItem("Refresh").apply {
            addActionListener { refresh() }
        })
        serverTree.componentPopupMenu = popupMenu

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
                GlobalScope.launch(Dispatchers.IO) {
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
        GlobalScope.launch(Dispatchers.IO) {
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

    override fun dispose() {
        refreshTimer.stop()
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
