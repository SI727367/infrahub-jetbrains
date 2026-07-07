package app.opsmill.infrahub.toolwindow.schema

import com.intellij.icons.AllIcons
import javax.swing.Icon
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Tree model for the schema panel.
 */
class SchemaTreeModel : DefaultTreeModel(DefaultMutableTreeNode("Schema")) {

    private val rootNode: DefaultMutableTreeNode
        get() = root as DefaultMutableTreeNode

    fun update(files: List<SchemaFileNodeData>) {
        rootNode.removeAllChildren()
        files.sortedBy { it.relativePath.lowercase() }.forEach { file ->
            rootNode.add(buildFileNode(file))
        }
        reload()
    }

    private fun buildFileNode(file: SchemaFileNodeData): DefaultMutableTreeNode {
        val fileNode = DefaultMutableTreeNode(file)
        file.entries.sortedWith(compareBy<SchemaEntryNodeData> { it.kind.sortOrder }.thenBy { it.label.lowercase() })
            .forEach { entry ->
                val entryNode = DefaultMutableTreeNode(entry)
                buildProperties(entry).forEach(entryNode::add)
                fileNode.add(entryNode)
            }
        return fileNode
    }

    private fun buildProperties(entry: SchemaEntryNodeData): List<DefaultMutableTreeNode> {
        val nodes = mutableListOf<DefaultMutableTreeNode>()

        if (entry.namespace.isNotBlank()) {
            nodes.add(DefaultMutableTreeNode(SchemaPropertyNodeData("namespace", entry.namespace, AllIcons.Nodes.Package)))
        }

        if (entry.labelValue.isNotBlank()) {
            nodes.add(DefaultMutableTreeNode(SchemaPropertyNodeData("label", entry.labelValue, AllIcons.Nodes.Tag)))
        }

        if (entry.includeInMenu != null) {
            nodes.add(DefaultMutableTreeNode(SchemaPropertyNodeData("include_in_menu", entry.includeInMenu.toString(), AllIcons.Actions.Menu_open)))
        }

        if (entry.description.isNotBlank()) {
            nodes.add(DefaultMutableTreeNode(SchemaPropertyNodeData("description", entry.description, AllIcons.General.ContextHelp)))
        }

        if (entry.inheritFrom.isNotEmpty()) {
            val inheritNode = DefaultMutableTreeNode(SchemaSectionNodeData("inherit_from", AllIcons.Nodes.Symlink))
            entry.inheritFrom.forEach { inheritNode.add(DefaultMutableTreeNode(SchemaValueNodeData(it, AllIcons.Nodes.Class))) }
            nodes.add(inheritNode)
        }

        if (entry.generics.isNotEmpty()) {
            val genericsNode = DefaultMutableTreeNode(SchemaSectionNodeData("generics", AllIcons.Nodes.Type))
            entry.generics.sortedBy { it.name.lowercase() }.forEach { generic ->
                genericsNode.add(DefaultMutableTreeNode(generic))
            }
            nodes.add(genericsNode)
        }

        if (entry.attributes.isNotEmpty()) {
            val attributesNode = DefaultMutableTreeNode(SchemaSectionNodeData("attributes", AllIcons.Nodes.Parameter))
            entry.attributes.sortedBy { it.name.lowercase() }.forEach { attribute ->
                attributesNode.add(DefaultMutableTreeNode(attribute))
            }
            nodes.add(attributesNode)
        }

        if (entry.relationships.isNotEmpty()) {
            val relationshipsNode = DefaultMutableTreeNode(SchemaSectionNodeData("relationships", AllIcons.Nodes.DataTables))
            entry.relationships.sortedBy { it.name.lowercase() }.forEach { relationship ->
                relationshipsNode.add(DefaultMutableTreeNode(relationship))
            }
            nodes.add(relationshipsNode)
        }

        return nodes
    }
}

enum class SchemaEntryKind(val singularLabel: String, val sortOrder: Int) {
    NODE("node", 0),
    GENERIC("generic", 1),
    PROFILE("profile", 2),
    MENU("menu", 3),
    OTHER("item", 4)
}

data class SchemaFileNodeData(
    val relativePath: String,
    val absolutePath: String,
    val entries: List<SchemaEntryNodeData>
) {
    override fun toString(): String = relativePath
}

data class SchemaEntryNodeData(
    val kind: SchemaEntryKind,
    val name: String,
    val namespace: String,
    val description: String,
    val labelValue: String,
    val includeInMenu: Boolean?,
    val inheritFrom: List<String>,
    val generics: List<SchemaGenericNodeData>,
    val attributes: List<SchemaAttributeNodeData>,
    val relationships: List<SchemaRelationshipNodeData>
) {
    val label: String = buildString {
        if (namespace.isNotBlank()) {
            append(namespace)
        }
        append(name)
        append(" (")
        append(kind.singularLabel)
        append(")")
    }

    override fun toString(): String = label
}

data class SchemaGenericNodeData(
    val name: String,
    val kind: String,
    val defaultValue: String?
) {
    override fun toString(): String = buildString {
        append(name)
        if (kind.isNotBlank()) {
            append(": ")
            append(kind)
        }
        if (!defaultValue.isNullOrBlank()) {
            append(" = ")
            append(defaultValue)
        }
    }
}

data class SchemaAttributeNodeData(
    val name: String,
    val kind: String,
    val optional: Boolean?,
    val description: String
) {
    override fun toString(): String = buildString {
        append(name)
        if (kind.isNotBlank()) {
            append(": ")
            append(kind)
        }
        optional?.let { append(if (it) " [optional]" else " [required]") }
        if (description.isNotBlank()) {
            append(" - ")
            append(description)
        }
    }
}

data class SchemaRelationshipNodeData(
    val name: String,
    val peer: String,
    val cardinality: String,
    val optional: Boolean?
) {
    override fun toString(): String = buildString {
        append(name)
        if (peer.isNotBlank()) {
            append(" -> ")
            append(peer)
        }
        if (cardinality.isNotBlank()) {
            append(" [")
            append(cardinality)
            append("]")
        }
        optional?.let { append(if (it) " [optional]" else " [required]") }
    }
}

data class SchemaSectionNodeData(
    val label: String,
    val icon: Icon
) {
    override fun toString(): String = label
}

data class SchemaPropertyNodeData(
    val key: String,
    val value: String,
    val icon: Icon
) {
    override fun toString(): String = "$key: $value"
}

data class SchemaValueNodeData(
    val value: String,
    val icon: Icon
) {
    override fun toString(): String = value
}
