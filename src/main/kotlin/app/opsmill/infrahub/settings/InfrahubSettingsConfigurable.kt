package app.opsmill.infrahub.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.border.EmptyBorder
import javax.swing.BoxLayout
import javax.swing.BoxLayout.Y_AXIS

/**
 * Settings UI panel for Infrahub configuration.
 * Equivalent to VSCode's contributes.configuration section.
 */
class InfrahubSettingsConfigurable : Configurable {

    private var myPanel: JPanel? = null
    private lateinit var schemaDirectoryField: JBTextField
    private lateinit var showWarningsCheckbox: JBCheckBox
    private lateinit var infrahubctlPathField: JBTextField
    private lateinit var serversList: JList<InfrahubSettingsState.ServerConfig>
    private val serversModel = DefaultListModel<InfrahubSettingsState.ServerConfig>()

    override fun createComponent(): JComponent {
        myPanel = JPanel(BorderLayout()).also {
            it.border = EmptyBorder(10, 10, 10, 10)
        }

        // Create settings fields
        schemaDirectoryField = JBTextField(20)
        showWarningsCheckbox = JBCheckBox("Show infrahubctl warnings")
        infrahubctlPathField = JBTextField(20)
        serversList = JList<InfrahubSettingsState.ServerConfig>(serversModel)
        serversList.preferredSize = Dimension(400, 150)

        // Build main panel with grid-like layout using nested JPanels
        val mainPanel = JPanel(BorderLayout())
        
        // Top section: Application settings
        val settingsPanel = JPanel(BorderLayout()).also {
            it.border = EmptyBorder(0, 0, 20, 0)
        }
        
        val formPanel = JPanel().also {
            it.layout = BoxLayout(it, BoxLayout.Y_AXIS)
            it.border = EmptyBorder(0, 0, 0, 10)
        }
        
        formPanel.add(JBLabel("Schema Directory:"))
        formPanel.add(schemaDirectoryField)
        formPanel.add(JBLabel(" "))
        formPanel.add(showWarningsCheckbox)
        formPanel.add(JBLabel(" "))
        formPanel.add(JBLabel("infrahubctl Path:"))
        formPanel.add(infrahubctlPathField)
        
        settingsPanel.add(formPanel, BorderLayout.WEST)
        mainPanel.add(settingsPanel, BorderLayout.NORTH)

        // Middle section: Servers list
        val serversPanel = JPanel(BorderLayout())
        serversPanel.add(JBLabel("Servers:"), BorderLayout.NORTH)
        serversPanel.add(JBScrollPane(serversList), BorderLayout.CENTER)
        mainPanel.add(serversPanel, BorderLayout.CENTER)

        // Load existing settings
        val settings = InfrahubSettingsState.getInstance()
        schemaDirectoryField.text = settings.schemaDirectory
        showWarningsCheckbox.isSelected = settings.showInfrahubctlWarnings
        infrahubctlPathField.text = settings.infrahubctlPath
        settings.servers.forEach { serversModel.addElement(it) }

        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val settings = InfrahubSettingsState.getInstance()
        return settings.schemaDirectory != schemaDirectoryField.text ||
               settings.showInfrahubctlWarnings != showWarningsCheckbox.isSelected ||
               settings.infrahubctlPath != infrahubctlPathField.text
    }

    override fun apply() {
        val settings = InfrahubSettingsState.getInstance()
        settings.schemaDirectory = schemaDirectoryField.text
        settings.showInfrahubctlWarnings = showWarningsCheckbox.isSelected
        settings.infrahubctlPath = infrahubctlPathField.text
    }

    override fun reset() {
        val settings = InfrahubSettingsState.getInstance()
        schemaDirectoryField.text = settings.schemaDirectory
        showWarningsCheckbox.isSelected = settings.showInfrahubctlWarnings
        infrahubctlPathField.text = settings.infrahubctlPath
    }

    override fun disposeUIResources() {
        myPanel = null
    }

    override fun getDisplayName(): String {
        return "Infrahub"
    }
}
