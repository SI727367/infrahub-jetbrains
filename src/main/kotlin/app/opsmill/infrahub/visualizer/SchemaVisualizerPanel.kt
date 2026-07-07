package app.opsmill.infrahub.visualizer

import app.opsmill.infrahub.api.SchemaResponse
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel

object SchemaVisualizerPanel {

    fun show(project: Project, schema: SchemaResponse, serverName: String, branchName: String) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Infrahub")
        if (toolWindow == null) {
            Messages.showErrorDialog(project, "Infrahub tool window is not available.", "Infrahub")
            return
        }

        val contentFactory = ContentFactory.getInstance()
        val panel = createPanel(schema, serverName, branchName)
        val content = contentFactory.createContent(panel, "Schema: $serverName [$branchName]", true)
        toolWindow.contentManager.addContent(content)
        toolWindow.contentManager.setSelectedContent(content)
        toolWindow.show()
    }

    private fun createPanel(schema: SchemaResponse, serverName: String, branchName: String): JPanel {
        if (!JBCefApp.isSupported()) {
            return JPanel(BorderLayout()).apply {
                add(JLabel("JCEF is not available in this IDE runtime."), BorderLayout.CENTER)
            }
        }

        val browser = JBCefBrowser()
        val html = buildHtml(schema, serverName, branchName)
        browser.loadHTML(html)
        return JPanel(BorderLayout()).apply {
            add(browser.component, BorderLayout.CENTER)
        }
    }

    private fun buildHtml(schema: SchemaResponse, serverName: String, branchName: String): String {
        val json = Json { prettyPrint = true }.encodeToString(schema)
        return """
            <!DOCTYPE html>
            <html lang=\"en\">
            <head>
                <meta charset=\"UTF-8\" />
                <title>Schema Visualizer</title>
                <style>
                    body { font-family: sans-serif; margin: 0; padding: 16px; }
                    h1 { font-size: 18px; margin-bottom: 8px; }
                    h2 { font-size: 14px; margin-top: 20px; }
                    pre { background: #f5f5f5; padding: 12px; border-radius: 4px; overflow: auto; }
                    ul { line-height: 1.6; }
                </style>
            </head>
            <body>
                <h1>Schema Visualizer</h1>
                <div><strong>Server:</strong> $serverName</div>
                <div><strong>Branch:</strong> $branchName</div>
                <h2>Nodes</h2>
                <ul>
                    ${schema.nodes.joinToString("\n") { "<li>${it.namespace}${it.name}</li>" }}
                </ul>
                <h2>Generics</h2>
                <ul>
                    ${schema.generics.joinToString("\n") { "<li>${it.namespace}${it.name}</li>" }}
                </ul>
                <h2>Raw Schema JSON</h2>
                <pre>${escapeHtml(json)}</pre>
            </body>
            </html>
        """.trimIndent()
    }

    private fun escapeHtml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
