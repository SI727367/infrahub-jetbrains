package app.opsmill.infrahub.toolwindow

import app.opsmill.infrahub.toolwindow.schema.SchemaTreePanel
import app.opsmill.infrahub.toolwindow.server.ServerTreePanel
import app.opsmill.infrahub.toolwindow.yaml.YamlTreePanel
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

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

        // Tab 2: Schema
        val schemaPanel = SchemaTreePanel(project).also { it.init() }
        projectService.setSchemaTreePanel(schemaPanel)
        val schemaContent = contentFactory.createContent(schemaPanel, "Schema", false)
        toolWindow.contentManager.addContent(schemaContent)

        // Tab 3: YAML
        val yamlPanel = YamlTreePanel(project).also { it.init() }
        projectService.setYamlTreePanel(yamlPanel)
        val yamlContent = contentFactory.createContent(yamlPanel, "YAML", false)
        toolWindow.contentManager.addContent(yamlContent)
    }

    override fun init(toolWindow: ToolWindow) {
        super.init(toolWindow)
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

    fun refreshSchemaTree() {
        schemaTreePanel?.refresh()
    }

    fun refreshYamlTree() {
        yamlTreePanel?.refresh()
    }

    fun refreshAll() {
        refreshServerTree()
        refreshSchemaTree()
        refreshYamlTree()
    }

    companion object {
        fun getService(project: Project): InfrahubProjectService =
            project.getService(InfrahubProjectService::class.java)
    }
}
