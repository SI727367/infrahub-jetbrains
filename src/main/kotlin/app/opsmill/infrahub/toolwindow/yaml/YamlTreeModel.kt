package app.opsmill.infrahub.toolwindow.yaml

import com.intellij.icons.AllIcons
import javax.swing.Icon
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Tree model for the Infrahub YAML panel.
 */
class YamlTreeModel : DefaultTreeModel(DefaultMutableTreeNode("Infrahub YAML")) {

    private val rootNode: DefaultMutableTreeNode
        get() = root as DefaultMutableTreeNode

    fun update(sections: List<YamlSectionNodeData>) {
        rootNode.removeAllChildren()
        sections.forEach { section ->
            val sectionNode = DefaultMutableTreeNode(section)
            section.items.sortedBy { it.label.lowercase() }.forEach { item ->
                sectionNode.add(DefaultMutableTreeNode(item))
            }
            rootNode.add(sectionNode)
        }
        reload()
    }
}

data class YamlSectionNodeData(
    val name: String,
    val icon: Icon,
    val items: List<YamlItemNodeData>
) {
    override fun toString(): String = name
}

data class YamlItemNodeData(
    val label: String,
    val kind: YamlItemKind,
    val filePath: String,
    val linkedPath: String? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    override fun toString(): String = buildString {
        append(label)
        when (kind) {
            YamlItemKind.ARTIFACT_DEFINITION -> metadata["transform_type"]?.let {
                append(" (")
                append(it)
                append(")")
            }
            else -> Unit
        }
    }
}

enum class YamlItemKind {
    QUERY,
    JINJA_TRANSFORM,
    PYTHON_TRANSFORM,
    ARTIFACT_DEFINITION,
    GENERATOR,
    CHECK,
    OTHER
}

fun yamlItemIcon(kind: YamlItemKind): Icon = when (kind) {
    YamlItemKind.QUERY -> AllIcons.FileTypes.Text
    YamlItemKind.JINJA_TRANSFORM -> AllIcons.FileTypes.Text
    YamlItemKind.PYTHON_TRANSFORM -> AllIcons.FileTypes.Text
    YamlItemKind.ARTIFACT_DEFINITION -> AllIcons.Nodes.Artifact
    YamlItemKind.GENERATOR -> AllIcons.Nodes.DataTables
    YamlItemKind.CHECK -> AllIcons.General.InspectionsOK
    YamlItemKind.OTHER -> AllIcons.Nodes.Tag
}
