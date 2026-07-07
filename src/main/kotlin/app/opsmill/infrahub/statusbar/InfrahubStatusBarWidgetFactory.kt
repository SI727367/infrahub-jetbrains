package app.opsmill.infrahub.statusbar

import app.opsmill.infrahub.api.InfrahubClientManager
import app.opsmill.infrahub.settings.InfrahubSettingsState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.awt.event.MouseEvent
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.JComponent
import javax.swing.JLabel

class InfrahubStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = "InfrahubStatusBar"

    override fun getDisplayName(): String = "Infrahub Status"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = InfrahubStatusBarWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

class InfrahubStatusBarWidget(private val project: Project) : CustomStatusBarWidget, StatusBarWidget {

    private val label = JLabel("Infrahub: No server set")
    private var statusBar: StatusBar? = null
    private var refreshTask: ScheduledFuture<*>? = null

    init {
        startRefresh()
    }

    override fun ID(): String = "InfrahubStatusBar"

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        refreshNow()
    }

    override fun dispose() {
        refreshTask?.cancel(true)
        refreshTask = null
    }

    override fun getComponent(): JComponent = label

    override fun getPresentation(): StatusBarWidget.WidgetPresentation? = object : StatusBarWidget.TextPresentation {
        override fun getText(): String = label.text

        override fun getAlignment(): Float = 0.5f

        override fun getTooltipText(): String = label.text

        override fun getClickConsumer(): Consumer<MouseEvent>? = null
    }

    private fun startRefresh() {
        refreshTask?.cancel(true)
        refreshTask = AppExecutorUtil.getAppScheduledExecutorService().scheduleAtFixedRate(
            { refreshNow() },
            0,
            10,
            TimeUnit.SECONDS
        )
    }

    private fun refreshNow() {
        val servers = InfrahubSettingsState.getInstance().servers
        if (servers.isEmpty()) {
            updateText("Infrahub: No server set")
            return
        }

        val firstServer = servers.first()
        val client = InfrahubClientManager.getInstance().getClient(firstServer.name)
        if (client == null) {
            updateText("Infrahub: No server set")
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val version = client.getVersion()
                updateText("Infrahub: v$version (${firstServer.name})")
            } catch (_: Exception) {
                updateText("Infrahub: Server unreachable (${firstServer.name})")
            }
        }
    }

    private fun updateText(text: String) {
        ApplicationManager.getApplication().invokeLater {
            label.text = text
            statusBar?.updateWidget(ID())
        }
    }
}
