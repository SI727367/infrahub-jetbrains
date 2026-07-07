package app.opsmill.infrahub.graphql

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

private val variableRegex = Regex("""\$(\w+)\s*:\s*([\w\[\]!]+)""")

data class GraphQLVariableDefinition(
    val name: String,
    val type: String,
    val required: Boolean
)

data class GraphQLVariableInfo(
    val required: List<GraphQLVariableDefinition>,
    val optional: List<GraphQLVariableDefinition>
)

object GraphQLVariableParser {
    fun parse(query: String): GraphQLVariableInfo {
        val allVariables = variableRegex.findAll(query)
            .map {
                val type = it.groupValues[2]
                GraphQLVariableDefinition(
                    name = it.groupValues[1],
                    type = type,
                    required = type.endsWith("!")
                )
            }
            .toList()
            .distinctBy { it.name }

        return GraphQLVariableInfo(
            required = allVariables.filter { it.required },
            optional = allVariables.filterNot { it.required }
        )
    }
}

class GraphQLVariablesDialog(
    variables: List<GraphQLVariableDefinition>
) : DialogWrapper(true) {

    private val fields = linkedMapOf<GraphQLVariableDefinition, JTextField>()

    init {
        title = "GraphQL Variables"
        variables.forEach { variable ->
            fields[variable] = JTextField()
        }
        init()
    }

    override fun createCenterPanel(): JComponent {
        var builder = FormBuilder.createFormBuilder()
        if (fields.isEmpty()) {
            return JPanel(BorderLayout()).apply {
                add(JLabel("This query does not require variables."), BorderLayout.CENTER)
            }
        }

        fields.forEach { (variable, field) ->
            val label = buildString {
                append(variable.name)
                append(" (")
                append(variable.type)
                append(")")
            }
            builder = builder.addLabeledComponent(label, field, 1, false)
        }
        return builder.panel
    }

    override fun doValidateAll(): MutableList<com.intellij.openapi.ui.ValidationInfo> {
        val validations = mutableListOf<com.intellij.openapi.ui.ValidationInfo>()
        fields.forEach { (variable, field) ->
            if (variable.required && field.text.isBlank()) {
                validations.add(com.intellij.openapi.ui.ValidationInfo("Required", field))
            }
        }
        return validations
    }

    fun getVariables(): Map<String, Any?> = buildMap {
        fields.forEach { (variable, field) ->
            if (field.text.isNotBlank()) {
                put(variable.name, castValue(variable.type, field.text.trim()))
            }
        }
    }

    private fun castValue(type: String, value: String): Any = when (type.removeSuffix("!")) {
        "Int" -> value.toIntOrNull() ?: value
        "Float" -> value.toDoubleOrNull() ?: value
        "Boolean" -> value.equals("true", ignoreCase = true)
        else -> value
    }
}

class GraphQLResultDialog(
    title: String,
    private val content: String
) : DialogWrapper(true) {

    init {
        this.title = title
        init()
    }

    override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout()).apply {
            val textArea = JBTextArea(content).apply {
                isEditable = false
                lineWrap = false
                wrapStyleWord = false
                caretPosition = 0
            }
            add(JBScrollPane(textArea), BorderLayout.CENTER)
        }
    }
}
