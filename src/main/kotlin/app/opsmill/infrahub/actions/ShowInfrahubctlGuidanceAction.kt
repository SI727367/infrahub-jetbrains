package app.opsmill.infrahub.actions

import app.opsmill.infrahub.infrahubctl.InfrahubctlChecker
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

class ShowInfrahubctlGuidanceAction : AnAction("Show infrahubctl Installation Guidance") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val checker = InfrahubctlChecker()
        val message = checker.getInstallationGuidance()
        val choice = Messages.showYesNoDialog(
            project,
            "$message\n\nOpen installation documentation?",
            "Infrahub",
            "Open Documentation",
            "Close",
            Messages.getInformationIcon()
        )

        if (choice == Messages.YES) {
            BrowserUtil.browse("https://docs.infrahub.app/python-sdk/guides/installation")
        }
    }
}
