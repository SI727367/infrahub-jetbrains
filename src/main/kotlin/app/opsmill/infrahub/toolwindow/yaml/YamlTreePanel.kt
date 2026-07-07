package app.opsmill.infrahub.toolwindow.yaml

import app.opsmill.infrahub.api.InfrahubClientManager
import app.opsmill.infrahub.infrahubctl.InfrahubctlRunner
import app.opsmill.infrahub.graphql.GraphQLResultDialog
import app.opsmill.infrahub.graphql.GraphQLVariableParser
import app.opsmill.infrahub.graphql.GraphQLVariablesDialog
import app.opsmill.infrahub.settings.InfrahubSettingsState
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.Tree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.yaml.snakeyaml.Yaml
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.io.File
import javax.swing.*
import javax.swing.event.TreeSelectionEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer

/**
 * Panel that displays sections from .infrahub.yml or .infrahub.yaml.
 */
class YamlTreePanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val tree = Tree()
    private val model = YamlTreeModel()
    private val cardLayout = CardLayout()
    private val cards = JPanel(cardLayout)
    private val emptyState = JLabel("No .infrahub.yml or .infrahub.yaml found", SwingConstants.CENTER)
    private val refreshButton = JButton("Refresh", AllIcons.Actions.Refresh)

    init {
        tree.model = model
        tree.isRootVisible = false
        tree.cellRenderer = YamlTreeCellRenderer()
        tree.selectionModel.selectionMode = javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.addTreeSelectionListener(::handleSelection)

        val toolbar = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
            add(JLabel("YAML"), BorderLayout.WEST)
            add(refreshButton, BorderLayout.EAST)
        }
        refreshButton.addActionListener { refresh() }

        val scrollPane = ScrollPaneFactory.createScrollPane(tree)
        cards.add(scrollPane, "tree")
        cards.add(emptyState, "empty")

        add(toolbar, BorderLayout.NORTH)
        add(cards, BorderLayout.CENTER)
        preferredSize = Dimension(250, 400)

        installPopupMenu()
    }

    fun init() {
        refresh()
    }

    fun refresh() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val parseResult = YamlTreeParser(project).parse()
            ApplicationManager.getApplication().invokeLater {
                if (parseResult == null) {
                    model.update(emptyList())
                    cardLayout.show(cards, "empty")
                } else {
                    model.update(parseResult.sections)
                    cardLayout.show(cards, "tree")
                    expandAll()
                }
            }
        }
    }

    private fun expandAll() {
        for (row in 0 until tree.rowCount) {
            tree.expandRow(row)
        }
    }

    private fun handleSelection(event: TreeSelectionEvent) {
        val node = event.path?.lastPathComponent as? DefaultMutableTreeNode ?: return
        val userObject = node.userObject
        when (userObject) {
            is YamlSectionNodeData -> {
                val infrahubFile = YamlTreeParser(project).findInfrahubFile() ?: return
                openFile(infrahubFile.absolutePath)
            }
            is YamlItemNodeData -> openFile(userObject.filePath)
        }
    }

    private fun installPopupMenu() {
        val popupMenu = JPopupMenu()
        popupMenu.add(JMenuItem("Refresh").apply {
            addActionListener { refresh() }
        })
        popupMenu.add(JMenuItem("Open Infrahub File").apply {
            addActionListener {
                YamlTreeParser(project).findInfrahubFile()?.let { openFile(it.absolutePath) }
            }
        })
        popupMenu.add(JMenuItem("Execute GraphQL Query").apply {
            addActionListener {
                val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return@addActionListener
                val item = node.userObject as? YamlItemNodeData ?: return@addActionListener
                if (item.kind == YamlItemKind.QUERY) {
                    executeGraphQLQuery(item)
                }
            }
        })
        popupMenu.add(JMenuItem("Run Transform").apply {
            addActionListener {
                getSelectedTransformName()?.let { transformName ->
                    val basePath = project.basePath ?: return@let
                    InfrahubctlRunner.runTransformCommand(project, transformName, File(basePath))
                }
            }
        })
        popupMenu.add(JMenuItem("Run Artifact Transform").apply {
            addActionListener {
                getSelectedArtifactTransformName()?.let { transformName ->
                    val basePath = project.basePath ?: return@let
                    InfrahubctlRunner.runTransformCommand(project, transformName, File(basePath))
                }
            }
        })
        tree.componentPopupMenu = popupMenu
    }

    private fun getSelectedTransformName(): String? {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return null
        val item = node.userObject as? YamlItemNodeData ?: return null
        return when (item.kind) {
            YamlItemKind.JINJA_TRANSFORM, YamlItemKind.PYTHON_TRANSFORM -> item.label
            else -> null
        }
    }

    private fun getSelectedArtifactTransformName(): String? {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return null
        val item = node.userObject as? YamlItemNodeData ?: return null
        return if (item.kind == YamlItemKind.ARTIFACT_DEFINITION) {
            item.metadata["transformation"]
        } else {
            null
        }
    }

    private fun openFile(path: String) {
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path) ?: return
        OpenFileDescriptor(project, virtualFile).navigate(true)
    }

    private fun executeGraphQLQuery(item: YamlItemNodeData) {
        val queryFile = item.linkedPath ?: item.filePath
        val queryText = runCatching { File(queryFile).readText() }.getOrElse {
            Messages.showErrorDialog(project, "Failed to read GraphQL file: $queryFile", "Infrahub")
            return
        }

        val variableInfo = GraphQLVariableParser.parse(queryText)
        val variableDialog = GraphQLVariablesDialog(variableInfo.required + variableInfo.optional)
        if (!variableDialog.showAndGet()) {
            return
        }
        val variables = variableDialog.getVariables()

        val serverConfigs = InfrahubSettingsState.getInstance().servers
        if (serverConfigs.isEmpty()) {
            Messages.showErrorDialog(project, "No Infrahub servers configured.", "Infrahub")
            return
        }

        val serverNames = serverConfigs.map { it.name }.toTypedArray()
        val serverIndex = Messages.showChooseDialog(
            project,
            "Select Infrahub server",
            "Execute GraphQL Query",
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

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val branches = client.getAllBranches()
                val branchNames = branches.map { it.name }.toTypedArray()
                ApplicationManager.getApplication().invokeAndWait {
                    val branchIndex = Messages.showChooseDialog(
                        project,
                        "Select branch",
                        "Execute GraphQL Query",
                        null,
                        branchNames,
                        branchNames.firstOrNull()
                    )
                    if (branchIndex < 0) {
                        return@invokeAndWait
                    }
                    val branchName = branchNames[branchIndex]

                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            val result = client.executeGraphQL(queryText, variables, branchName)
                            val formatted = Json { prettyPrint = true }.encodeToString(result)
                            ApplicationManager.getApplication().invokeLater {
                                GraphQLResultDialog(
                                    "GraphQL Result: ${item.label} [$branchName] ($serverName)",
                                    formatted
                                ).show()
                            }
                        } catch (e: Exception) {
                            ApplicationManager.getApplication().invokeLater {
                                Messages.showErrorDialog(
                                    project,
                                    e.message ?: "GraphQL execution failed",
                                    "Infrahub"
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(project, e.message ?: "Failed to load branches", "Infrahub")
                }
            }
        }
    }

    override fun dispose() = Unit
}

class YamlTreeCellRenderer : DefaultTreeCellRenderer() {
    override fun getTreeCellRendererComponent(
        tree: JTree?,
        value: Any?,
        sel: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): java.awt.Component {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)

        val userObject = (value as? DefaultMutableTreeNode)?.userObject
        when (userObject) {
            is YamlSectionNodeData -> icon = userObject.icon
            is YamlItemNodeData -> icon = yamlItemIcon(userObject.kind)
        }

        return this
    }
}

data class YamlParseResult(
    val file: File,
    val sections: List<YamlSectionNodeData>
)

class YamlTreeParser(private val project: Project) {

    private val yaml = Yaml()

    fun findInfrahubFile(): File? {
        val basePath = project.basePath ?: return null
        val yml = File(basePath, ".infrahub.yml")
        if (yml.exists() && yml.isFile) {
            return yml
        }

        val yamlFile = File(basePath, ".infrahub.yaml")
        if (yamlFile.exists() && yamlFile.isFile) {
            return yamlFile
        }

        return null
    }

    fun parse(): YamlParseResult? {
        val infrahubFile = findInfrahubFile() ?: return null
        val root = runCatching {
            yaml.load<Any>(infrahubFile.readText())
        }.getOrNull() as? Map<*, *> ?: return null

        val transformTypes = collectTransformTypes(root)
        val sections = root.entries.mapNotNull { (key, value) ->
            val sectionName = key?.toString() ?: return@mapNotNull null
            val items = (value as? List<*>)
                ?.mapNotNull { parseItem(sectionName, infrahubFile.parentFile, it as? Map<*, *>, transformTypes) }
                .orEmpty()
            YamlSectionNodeData(sectionName, sectionIcon(sectionName), items)
        }

        return YamlParseResult(infrahubFile, sections)
    }

    private fun collectTransformTypes(root: Map<*, *>): Map<String, String> {
        val transformTypes = mutableMapOf<String, String>()
        root.mapList("python_transforms").forEach { item ->
            item.stringValue("name")?.let { transformTypes[it] = "python" }
        }
        root.mapList("jinja2_transforms").forEach { item ->
            item.stringValue("name")?.let { transformTypes[it] = "jinja" }
        }
        return transformTypes
    }

    private fun parseItem(
        sectionName: String,
        baseDir: File,
        item: Map<*, *>?,
        transformTypes: Map<String, String>
    ): YamlItemNodeData? {
        if (item == null) return null

        val name = item.stringValue("name") ?: "item"
        val kind = itemKind(sectionName)
        val linkedPath = item.stringValue("file_path")
        val resolvedFile = resolveFile(baseDir, linkedPath)
        val metadata = buildMetadata(sectionName, item, transformTypes)

        return YamlItemNodeData(
            label = name,
            kind = kind,
            filePath = resolvedFile?.absolutePath ?: baseDir.resolve(".infrahub.yml").absolutePath,
            linkedPath = resolvedFile?.absolutePath,
            metadata = metadata,
            sourceFilePath = if (File(baseDir, ".infrahub.yml").exists()) {
                File(baseDir, ".infrahub.yml").absolutePath
            } else {
                File(baseDir, ".infrahub.yaml").absolutePath
            }
        )
    }

    private fun buildMetadata(
        sectionName: String,
        item: Map<*, *>,
        transformTypes: Map<String, String>
    ): Map<String, String> {
        val metadata = mutableMapOf<String, String>()

        if (sectionName == "artifact_definitions") {
            item.stringValue("transformation")?.let { transformationName ->
                metadata["transformation"] = transformationName
                transformTypes[transformationName]?.let { metadata["transform_type"] = it }
            }
        }

        if (sectionName == "python_transforms") {
            metadata["transform_type"] = "python"
        }
        if (sectionName == "jinja2_transforms") {
            metadata["transform_type"] = "jinja"
        }

        item.stringValue("file_path")?.let { metadata["file_path"] = it }
        return metadata
    }

    private fun resolveFile(baseDir: File, filePath: String?): File? {
        if (filePath.isNullOrBlank()) {
            return null
        }

        val file = File(filePath)
        return if (file.isAbsolute) file else File(baseDir, filePath)
    }

    private fun itemKind(sectionName: String): YamlItemKind = when (sectionName) {
        "queries" -> YamlItemKind.QUERY
        "jinja2_transforms" -> YamlItemKind.JINJA_TRANSFORM
        "python_transforms" -> YamlItemKind.PYTHON_TRANSFORM
        "artifact_definitions" -> YamlItemKind.ARTIFACT_DEFINITION
        "generators" -> YamlItemKind.GENERATOR
        "checks" -> YamlItemKind.CHECK
        else -> YamlItemKind.OTHER
    }

    private fun sectionIcon(sectionName: String) = when (sectionName) {
        "queries" -> AllIcons.Nodes.Folder
        "jinja2_transforms" -> AllIcons.Nodes.Template
        "python_transforms" -> AllIcons.Nodes.Template
        "artifact_definitions" -> AllIcons.Nodes.Artifact
        "generators" -> AllIcons.Nodes.DataTables
        "checks" -> AllIcons.General.InspectionsOK
        else -> AllIcons.Nodes.Tag
    }

    private fun Map<*, *>.stringValue(key: String): String? = this[key]?.toString()

    private fun Map<*, *>.mapList(key: String): List<Map<*, *>> = (this[key] as? List<*>)
        ?.mapNotNull { it as? Map<*, *> }
        ?: emptyList()
}
