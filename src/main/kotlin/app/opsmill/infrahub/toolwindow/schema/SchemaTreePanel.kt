package app.opsmill.infrahub.toolwindow.schema

import app.opsmill.infrahub.infrahubctl.InfrahubctlRunner
import app.opsmill.infrahub.settings.InfrahubSettingsState
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.Tree
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
 * Panel that displays schema files and parsed schema items from the configured schema directory.
 */
class SchemaTreePanel(private val project: Project) : JPanel(BorderLayout()), Disposable {

    private val tree = Tree()
    private val model = SchemaTreeModel()
    private val emptyState = JLabel("No schema files found", SwingConstants.CENTER)
    private val cardLayout = CardLayout()
    private val cards = JPanel(cardLayout)
    private val refreshButton = JButton("Refresh", AllIcons.Actions.Refresh)
    private val checkAllButton = JButton("Check All")
    private val loadAllButton = JButton("Load All")
    private val messageBusConnection = project.messageBus.connect(this)

    init {
        tree.model = model
        tree.isRootVisible = false
        tree.cellRenderer = SchemaTreeCellRenderer()
        tree.selectionModel.selectionMode = javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.addTreeSelectionListener(::handleSelection)

        val toolbarActions = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(checkAllButton)
            add(Box.createHorizontalStrut(4))
            add(loadAllButton)
            add(Box.createHorizontalStrut(4))
            add(refreshButton)
        }
        val toolbar = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(4, 4, 4, 4)
            add(JLabel("Schema"), BorderLayout.WEST)
            add(toolbarActions, BorderLayout.EAST)
        }
        refreshButton.addActionListener { refresh() }
        checkAllButton.addActionListener { runSchemaDirectoryCommand("check") }
        loadAllButton.addActionListener { runSchemaDirectoryCommand("load") }

        val scrollPane = ScrollPaneFactory.createScrollPane(tree)
        cards.add(scrollPane, "tree")
        cards.add(emptyState, "empty")

        add(toolbar, BorderLayout.NORTH)
        add(cards, BorderLayout.CENTER)
        preferredSize = Dimension(250, 400)

        installPopupMenu()
        installFileWatcher()
    }

    fun init() {
        refresh()
    }

    fun refresh() {
        ApplicationManager.getApplication().executeOnPooledThread {
            val files = SchemaTreeParser(project).parseSchemaFiles()
            ApplicationManager.getApplication().invokeLater {
                model.update(files)
                if (files.isEmpty()) {
                    emptyState.text = buildEmptyStateMessage()
                    cardLayout.show(cards, "empty")
                } else {
                    cardLayout.show(cards, "tree")
                    expandFirstLevel()
                }
            }
        }
    }

    private fun buildEmptyStateMessage(): String {
        val schemaDirectory = InfrahubSettingsState.getInstance().schemaDirectory
        return "No schema files found in $schemaDirectory"
    }

    private fun expandFirstLevel() {
        for (row in 0 until tree.rowCount) {
            tree.expandRow(row)
        }
    }

    private fun handleSelection(event: TreeSelectionEvent) {
        val node = event.path?.lastPathComponent as? DefaultMutableTreeNode ?: return
        val userObject = node.userObject
        when (userObject) {
            is SchemaFileNodeData -> openFile(userObject.absolutePath)
        }
    }

    private fun installPopupMenu() {
        val popupMenu = JPopupMenu()
        popupMenu.add(JMenuItem("Refresh").apply {
            addActionListener { refresh() }
        })
        popupMenu.add(JMenuItem("Open Schema Directory").apply {
            addActionListener { openSchemaDirectory() }
        })
        popupMenu.addSeparator()
        popupMenu.add(JMenuItem("Check Selected Schema File").apply {
            addActionListener {
                getSelectedSchemaFile()?.let { InfrahubctlRunner.runSchemaCommand(project, File(it.absolutePath), "check") }
            }
        })
        popupMenu.add(JMenuItem("Load Selected Schema File").apply {
            addActionListener {
                getSelectedSchemaFile()?.let { InfrahubctlRunner.runSchemaCommand(project, File(it.absolutePath), "load") }
            }
        })
        popupMenu.add(JMenuItem("Check All Schema Files").apply {
            addActionListener { runSchemaDirectoryCommand("check") }
        })
        popupMenu.add(JMenuItem("Load All Schema Files").apply {
            addActionListener { runSchemaDirectoryCommand("load") }
        })
        tree.componentPopupMenu = popupMenu
    }

    private fun getSelectedSchemaFile(): SchemaFileNodeData? {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return null
        return when (val userObject = node.userObject) {
            is SchemaFileNodeData -> userObject
            is SchemaEntryNodeData -> (node.parent as? DefaultMutableTreeNode)?.userObject as? SchemaFileNodeData
            else -> null
        }
    }

    private fun openSchemaDirectory() {
        val schemaDir = SchemaTreeParser(project).resolveSchemaDirectory() ?: return
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(schemaDir) ?: return
        OpenFileDescriptor(project, virtualFile).navigate(true)
    }

    private fun runSchemaDirectoryCommand(action: String) {
        SchemaTreeParser(project).resolveSchemaDirectory()?.let {
            InfrahubctlRunner.runSchemaCommand(project, it, action)
        }
    }

    private fun openFile(path: String) {
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(path) ?: return
        OpenFileDescriptor(project, virtualFile).navigate(true)
    }

    private fun installFileWatcher() {
        messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.any { isSchemaChange(it.path) }) {
                    ApplicationManager.getApplication().invokeLater {
                        if (!project.isDisposed) {
                            refresh()
                        }
                    }
                }
            }
        })
    }

    private fun isSchemaChange(path: String): Boolean {
        val schemaDir = SchemaTreeParser(project).resolveSchemaDirectory() ?: return false
        val normalizedPath = File(path).invariantSeparatorsPath
        val normalizedSchemaDir = schemaDir.invariantSeparatorsPath.trimEnd('/') + "/"
        return normalizedPath.startsWith(normalizedSchemaDir) &&
            (normalizedPath.endsWith(".yml") || normalizedPath.endsWith(".yaml"))
    }

    override fun dispose() {
        messageBusConnection.disconnect()
    }
}

class SchemaTreeCellRenderer : DefaultTreeCellRenderer() {
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
            is SchemaFileNodeData -> icon = AllIcons.FileTypes.Yaml
            is SchemaEntryNodeData -> icon = when (userObject.kind) {
                SchemaEntryKind.NODE -> AllIcons.Nodes.Class
                SchemaEntryKind.GENERIC -> AllIcons.Nodes.Type
                SchemaEntryKind.PROFILE -> AllIcons.Nodes.Plugin
                SchemaEntryKind.MENU -> AllIcons.Actions.Show
                SchemaEntryKind.OTHER -> AllIcons.Nodes.Tag
            }
            is SchemaSectionNodeData -> icon = userObject.icon
            is SchemaPropertyNodeData -> icon = userObject.icon
            is SchemaValueNodeData -> icon = userObject.icon
            is SchemaGenericNodeData -> icon = AllIcons.Nodes.Type
            is SchemaAttributeNodeData -> icon = AllIcons.Nodes.Parameter
            is SchemaRelationshipNodeData -> icon = AllIcons.Nodes.DataTables
        }

        return this
    }
}

class SchemaTreeParser(private val project: Project) {

    private val yaml = Yaml()

    fun resolveSchemaDirectory(): File? {
        val basePath = project.basePath ?: return null
        val schemaDirectory = InfrahubSettingsState.getInstance().schemaDirectory
        val directory = File(schemaDirectory)
        return if (directory.isAbsolute) directory else File(basePath, schemaDirectory)
    }

    fun parseSchemaFiles(): List<SchemaFileNodeData> {
        val schemaDir = resolveSchemaDirectory() ?: return emptyList()
        if (!schemaDir.exists() || !schemaDir.isDirectory) {
            return emptyList()
        }

        return schemaDir.walkTopDown()
            .filter { it.isFile && (it.extension == "yml" || it.extension == "yaml") }
            .mapNotNull { parseSchemaFile(schemaDir, it) }
            .toList()
    }

    private fun parseSchemaFile(schemaDir: File, file: File): SchemaFileNodeData? {
        val content = runCatching { file.readText() }.getOrNull() ?: return null
        val root = runCatching { yaml.load<Any>(content) }.getOrNull() as? Map<*, *> ?: return null

        val entries = mutableListOf<SchemaEntryNodeData>()
        root.forEach { (key, value) ->
            val typeName = key?.toString()?.lowercase() ?: return@forEach
            if (typeName == "version" || typeName == "extensions") {
                return@forEach
            }

            val kind = mapKind(typeName)
            val items = value as? List<*> ?: return@forEach
            items.mapNotNullTo(entries) { item -> parseSchemaEntry(kind, item as? Map<*, *>) }
        }

        return SchemaFileNodeData(
            relativePath = file.relativeTo(schemaDir.parentFile ?: schemaDir).invariantSeparatorsPath,
            absolutePath = file.absolutePath,
            entries = entries
        )
    }

    private fun mapKind(typeName: String): SchemaEntryKind = when (typeName) {
        "nodes" -> SchemaEntryKind.NODE
        "generics" -> SchemaEntryKind.GENERIC
        "profiles" -> SchemaEntryKind.PROFILE
        "menus" -> SchemaEntryKind.MENU
        else -> SchemaEntryKind.OTHER
    }

    private fun parseSchemaEntry(kind: SchemaEntryKind, item: Map<*, *>?): SchemaEntryNodeData? {
        if (item == null) return null

        val name = item.stringValue("name") ?: return null
        val namespace = item.stringValue("namespace").orEmpty()
        val description = item.stringValue("description").orEmpty()
        val label = item.stringValue("label").orEmpty()
        val includeInMenu = item.booleanValue("include_in_menu")
        val inheritFrom = item.stringList("inherit_from")
        val generics = item.mapList("generics").mapNotNull(::parseGeneric)
        val attributes = item.mapList("attributes").mapNotNull(::parseAttribute)
        val relationships = item.mapList("relationships").mapNotNull(::parseRelationship)

        return SchemaEntryNodeData(
            kind = kind,
            name = name,
            namespace = namespace,
            description = description,
            labelValue = label,
            includeInMenu = includeInMenu,
            inheritFrom = inheritFrom,
            generics = generics,
            attributes = attributes,
            relationships = relationships
        )
    }

    private fun parseGeneric(item: Map<*, *>): SchemaGenericNodeData? {
        val name = item.stringValue("name") ?: return null
        return SchemaGenericNodeData(
            name = name,
            kind = item.stringValue("kind").orEmpty(),
            defaultValue = item["default"]?.toString()
        )
    }

    private fun parseAttribute(item: Map<*, *>): SchemaAttributeNodeData? {
        val name = item.stringValue("name") ?: return null
        return SchemaAttributeNodeData(
            name = name,
            kind = item.stringValue("kind").orEmpty(),
            optional = item.booleanValue("optional"),
            description = item.stringValue("description").orEmpty()
        )
    }

    private fun parseRelationship(item: Map<*, *>): SchemaRelationshipNodeData? {
        val name = item.stringValue("name") ?: return null
        return SchemaRelationshipNodeData(
            name = name,
            peer = item.stringValue("peer").orEmpty(),
            cardinality = item.stringValue("cardinality").orEmpty(),
            optional = item.booleanValue("optional")
        )
    }

    private fun Map<*, *>.stringValue(key: String): String? = this[key]?.toString()

    private fun Map<*, *>.booleanValue(key: String): Boolean? = when (val value = this[key]) {
        is Boolean -> value
        is String -> value.toBooleanStrictOrNull()
        else -> null
    }

    private fun Map<*, *>.stringList(key: String): List<String> = (this[key] as? List<*>)
        ?.mapNotNull { it?.toString() }
        ?: emptyList()

    private fun Map<*, *>.mapList(key: String): List<Map<*, *>> = (this[key] as? List<*>)
        ?.mapNotNull { it as? Map<*, *> }
        ?: emptyList()
}
