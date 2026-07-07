package app.opsmill.infrahub.api

import app.opsmill.infrahub.settings.InfrahubSettingsState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.util.messages.Topic

/**
 * Application-level service that manages InfrahubClient instances.
 * Caches clients keyed by server name, rebuilds when settings change.
 * Equivalent to VSCode's client cache in InfrahubServerTreeViewProvider.
 */
@Service(Service.Level.APP)
class InfrahubClientManager {

    private val clients = mutableMapOf<String, InfrahubClient>()

    /**
     * Get or create a client for the given server name.
     */
    fun getClient(serverName: String): InfrahubClient? {
        val settings = InfrahubSettingsState.getInstance()
        val serverConfig = settings.servers.find { it.name == serverName } ?: return null

        return clients.getOrPut(serverName) {
            InfrahubClient(
                address = serverConfig.address,
                token = serverConfig.apiToken,
                tlsInsecure = serverConfig.tlsInsecure
            )
        }
    }

    /**
     * Get a client for the first configured server.
     */
    fun getFirstClient(): InfrahubClient? {
        val settings = InfrahubSettingsState.getInstance()
        val firstServer = settings.servers.firstOrNull() ?: return null
        return clients.getOrPut(firstServer.name) {
            InfrahubClient(
                address = firstServer.address,
                token = firstServer.apiToken,
                tlsInsecure = firstServer.tlsInsecure
            )
        }
    }

    /**
     * Invalidate and rebuild all clients (called when settings change).
     */
    fun invalidateAll() {
        clients.clear()
    }

    companion object {
        fun getInstance(): InfrahubClientManager =
            ApplicationManager.getApplication().getService(InfrahubClientManager::class.java)
    }
}
