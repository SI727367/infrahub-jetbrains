package app.opsmill.infrahub.toolwindow

import app.opsmill.infrahub.settings.InfrahubSettingsState
import app.opsmill.infrahub.toolwindow.server.ServerTreePanel
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import org.jdom.Element

/**
 * Factory for the Infrahub tool window.
 * Registers three tabs: Servers, Schema, YAML.
 *
 * Phase 4 and Phase 5 will implement the Schema and YAML panels respectively.
 */
class InfrahubToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        val projectService = InfrahubProjectService.getService(project)

        // Tab 1: Servers
        val serverPanel = ServerTreePanel(project).also { it.init() }
        projectService.setServerTreePanel(serverPanel)
        val serversContent = contentFactory.createContent(serverPanel, "Servers", false)
        toolWindow.contentManager.addContent(serversContent)

        // Tab 2: Schema (stub for Phase 4)
        val schemaPanel = SchemaTreePanel(project).apply {
            content.text = "Schema panel — coming in Phase 4"
        }
        projectService.setSchemaTreePanel(schemaPanel)
        val schemaContent = contentFactory.createContent(schemaPanel, "Schema", false)
        toolWindow.contentManager.addContent(schemaContent)

        // Tab 3: YAML (stub for Phase 5)
        val yamlPanel = YamlTreePanel(project).apply {
            content.text = "YAML panel — coming in Phase 5"
        }
        projectService.setYamlTreePanel(yamlPanel)
        val yamlContent = contentFactory.createContent(yamlPanel, "YAML", false)
        toolWindow.contentManager.addContent(yamlContent)
    }

    override fun init(toolWindow: ToolWindow) {
        super.init(toolWindow)
    }
}

/**
 * Stub Schema tree panel — to be implemented in Phase 4.
 */
class SchemaTreePanel(private val project: Project) : javax.swing.JPanel() {
    val content = javax.swing.JLabel()

    init {
        layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
        add(content)
    }
}

/**
 * Stub YAML tree panel — to be implemented in Phase 5.
 */
class YamlTreePanel(private val project: Project) : javax.swing.JPanel() {
    val content = javax.swing.JLabel()

    init {
        layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
        add(content)
    }
}

/**
 * Project-level service that keeps references to all three tree panels.
 * Allows other components to access panels for refreshing, etc.
 */
@Service(Service.Level.PROJECT)
class InfrahubProjectService(private val project: Project) {

    private var serverTreePanel: ServerTreePanel? = null
    private var schemaTreePanel: SchemaTreePanel? = null
    private var yamlTreePanel: YamlTreePanel? = null

    fun setServerTreePanel(panel: ServerTreePanel) {
        serverTreePanel = panel
    }

    fun setSchemaTreePanel(panel: SchemaTreePanel) {
        schemaTreePanel = panel
    }

    fun setYamlTreePanel(panel: YamlTreePanel) {
        yamlTreePanel = panel
    }

    fun refreshServerTree() {
        serverTreePanel?.refresh()
    }

    fun refreshAll() {
        refreshServerTree()
    }

    companion object {
        fun getService(project: Project): InfrahubProjectService =
            project.getService(InfrahubProjectService::class.java)
    }
}
