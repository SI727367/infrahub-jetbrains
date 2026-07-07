package app.opsmill.infrahub.common

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project

object ProjectTaskRunner {

    fun runBackground(
        project: Project,
        title: String,
        cancellable: Boolean = false,
        task: (ProgressIndicator) -> Unit
    ) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, title, cancellable) {
            override fun run(indicator: ProgressIndicator) {
                task(indicator)
            }
        })
    }

    fun onUiThread(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(action)
    }
}
