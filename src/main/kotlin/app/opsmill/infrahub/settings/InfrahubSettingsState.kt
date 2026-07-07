package app.opsmill.infrahub.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import kotlinx.serialization.Serializable

/**
 * Application-level service that persists Infrahub server configuration.
 * Equivalent to VSCode's `infrahub-vscode.servers` settings.
 */
@Service(Service.Level.APP)
@State(
    name = "infrahubSettings",
    storages = [Storage("infrahub.xml")]
)
class InfrahubSettingsState : PersistentStateComponent<InfrahubSettingsState> {

    @Serializable
    data class ServerConfig(
        var name: String = "",
        var address: String = "",
        var apiToken: String = "",   // supports ${env:VAR_NAME}
        var tlsInsecure: Boolean = false
    )

    var servers: MutableList<ServerConfig> = mutableListOf()
    var schemaDirectory: String = "schemas"
    var showInfrahubctlWarnings: Boolean = true
    var infrahubctlPath: String = ""

    override fun getState(): InfrahubSettingsState = this

    override fun loadState(state: InfrahubSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): InfrahubSettingsState =
            ApplicationManager.getApplication().getService(InfrahubSettingsState::class.java)
    }
}
