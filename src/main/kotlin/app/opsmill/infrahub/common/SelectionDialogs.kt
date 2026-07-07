package app.opsmill.infrahub.common

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CollectionListModel
import com.intellij.ui.components.JBList
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel

object SelectionDialogs {

    fun chooseString(
        project: Project,
        title: String,
        items: List<String>,
        initialSelection: String? = items.firstOrNull()
    ): String? {
        if (items.isEmpty()) {
            return null
        }

        val dialog = StringSelectionDialog(project, title, items, initialSelection)
        return if (dialog.showAndGet()) dialog.getSelectedValue() else null
    }
}

private class StringSelectionDialog(
    project: Project,
    title: String,
    items: List<String>,
    initialSelection: String?
) : DialogWrapper(project) {

    private val list = JBList(CollectionListModel(items))

    init {
        this.title = title
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        if (initialSelection != null) {
            list.setSelectedValue(initialSelection, true)
        } else if (items.isNotEmpty()) {
            list.selectedIndex = 0
        }
        init()
    }

    override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout()).apply {
            add(list, BorderLayout.CENTER)
        }
    }

    fun getSelectedValue(): String? = list.selectedValue
}
