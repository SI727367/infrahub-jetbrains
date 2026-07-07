package app.opsmill.infrahub.toolwindow.server

import app.opsmill.infrahub.api.BranchInfo
import app.opsmill.infrahub.settings.InfrahubSettingsState.ServerConfig
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.Icon
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.SwingUtilities
import javax.swing.tree.TreeModel
import javax.swing.event.TreeModelListener
import javax.swing.event.TreeModelEvent
import javax.swing.tree.TreePath
import java.awt.event.ActionEvent

/**
 * Represents a server node in the tree.
 * Contains server config, connection status, version, and list of branches.
 */
data class ServerTreeNodeData(
    val config: ServerConfig,
    var online: Boolean = false,
    var version: String? = null,
    var branches: List<BranchInfo> = emptyList()
) {
    override fun toString(): String {
        val statusIcon = if (online) " \u2705" else " \u274C"
        val versionStr = version?.let { " ($it)" } ?: ""
        return "Server ${config.name}$statusIcon$versionStr"
    }
}

/**
 * Builds a DefaultTreeModel from server configs.
 * Each config becomes a DefaultMutableTreeNode; online servers expand to show branches.
 */
class ServerTreeModel(
    private val project: Project,
    private val configs: List<ServerConfig>
) : TreeModel {

    private val root = DefaultMutableTreeNode("Servers")
    private val serverNodes = mutableMapOf<String, DefaultMutableTreeNode>()
    private val listeners = mutableListOf<TreeModelListener>()

    init {
        initTree()
    }

    private fun initTree() {
        root.removeAllChildren()
        serverNodes.clear()
        configs.forEach { config ->
            val serverNode = DefaultMutableTreeNode(ServerTreeNodeData(config))
            root.add(serverNode)
            serverNodes[config.name] = serverNode
        }
    }

    override fun getRoot(): Any = root

    override fun getChildCount(parent: Any): Int {
        val node = getMutableTreeNode(parent) ?: return 0
        return node.childCount
    }

    override fun getChild(parent: Any, index: Int): Any {
        val node = getMutableTreeNode(parent) ?: throw IndexOutOfBoundsException()
        return node.getChildAt(index)
    }

    override fun getIndexOfChild(parent: Any, child: Any): Int {
        val parentNode = getMutableTreeNode(parent) ?: return -1
        val childNode = getMutableTreeNode(child) ?: return -1
        return parentNode.getIndex(childNode)
    }

    override fun isLeaf(node: Any): Boolean {
        val treeNode = getMutableTreeNode(node) ?: return false
        return treeNode.isLeaf
    }

    override fun valueForPathChanged(path: TreePath, newValue: Any) {
        // No-op
    }

    override fun addTreeModelListener(l: TreeModelListener) {
        listeners.add(l)
    }

    override fun removeTreeModelListener(l: TreeModelListener) {
        listeners.remove(l)
    }

    /**
     * Refresh a specific server node with updated data.
     */
    fun updateServer(configName: String, online: Boolean, version: String?, branches: List<BranchInfo>) {
        val treeNode = serverNodes[configName] ?: return
        val data = treeNode.userObject as? ServerTreeNodeData ?: return

        data.online = online
        data.version = version
        data.branches = branches

        // Clear existing children and add updated branches
        treeNode.removeAllChildren()
        branches.forEach { branch ->
            treeNode.add(DefaultMutableTreeNode(BranchTreeNodeData(branch)))
        }

        // Fire structure change event
        SwingUtilities.invokeLater {
            val event = TreeModelEvent(root, arrayOf(treeNode))
            listeners.forEach { it.treeStructureChanged(event) }
        }
    }

    private fun getMutableTreeNode(any: Any): DefaultMutableTreeNode? {
        return when (any) {
            is DefaultMutableTreeNode -> any
            is TreePath -> any.lastPathComponent as? DefaultMutableTreeNode
            else -> null
        }
    }
}

/**
 * Represents a branch node in the tree.
 */
data class BranchTreeNodeData(
    val branch: BranchInfo
) {
    override fun toString(): String {
        val suffix = mutableListOf<String>()
        if (branch.is_default) suffix.add("[default]")
        if (branch.sync_with_git) suffix.add("[git]")
        return "Branch ${branch.name}${suffix.takeIf { it.isNotEmpty() }?.let { " " + it.joinToString(" ") } ?: ""}"
    }
}

/**
 * Helper to get icon for a branch node.
 */
fun getBranchIcon(branch: BranchInfo): Icon {
    return when {
        branch.is_default -> AllIcons.Vcs.BranchNode
        branch.sync_with_git -> AllIcons.Vcs.BranchNode
        else -> AllIcons.Nodes.Folder
    }
}

/**
 * Helper to get icon for server based on online status.
 */
fun getServerIcon(online: Boolean): Icon {
    return if (online) AllIcons.General.Web else AllIcons.General.Error
}

/**
 * Get popup actions for a server node.
 */
fun getServerPopupActions(
    serverData: ServerTreeNodeData,
    project: Project
): List<Action> {
    val actions = mutableListOf<Action>()

    if (serverData.online) {
        actions.add(object : AbstractAction("Visualize Schema") {
            override fun actionPerformed(e: ActionEvent) {
                // TODO: Phase 9 - Open schema visualizer
            }
        })
    }

    return actions
}

/**
 * Get popup actions for a branch node.
 */
fun getBranchPopupActions(
    branchData: BranchTreeNodeData,
    project: Project
): List<Action> {
    return listOf(object : AbstractAction("Delete Branch") {
        override fun actionPerformed(e: ActionEvent) {
            // TODO: Phase 6 - Delete the branch
        }
    })
}
