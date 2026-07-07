package app.opsmill.infrahub.infrahubctl

import app.opsmill.infrahub.settings.InfrahubSettingsState
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import java.io.File

data class InfrahubctlCheckResult(
    val isAvailable: Boolean,
    val path: String? = null,
    val errorMessage: String? = null
)

class InfrahubctlChecker {

    private var cachedResult: InfrahubctlCheckResult? = null
    private var lastCheckTime: Long = 0
    private val cacheDurationMillis = 30_000L

    fun checkAvailability(): InfrahubctlCheckResult {
        val now = System.currentTimeMillis()
        if (cachedResult != null && now - lastCheckTime < cacheDurationMillis) {
            return cachedResult!!
        }

        val result = try {
            val resolvedPath = getInfrahubctlPath()
            if (resolvedPath != null && fileExists(resolvedPath)) {
                InfrahubctlCheckResult(isAvailable = true, path = resolvedPath)
            } else {
                InfrahubctlCheckResult(
                    isAvailable = false,
                    errorMessage = "infrahubctl not found"
                )
            }
        } catch (e: Exception) {
            InfrahubctlCheckResult(
                isAvailable = false,
                errorMessage = e.message ?: "Failed to check infrahubctl"
            )
        }

        cachedResult = result
        lastCheckTime = now
        return result
    }

    fun getInfrahubctlPath(): String? {
        val customPath = InfrahubSettingsState.getInstance().infrahubctlPath.trim()
        if (customPath.isNotBlank()) {
            return customPath
        }

        val fromPath = PathEnvironmentVariableUtil.findInPath("infrahubctl")
        if (fromPath != null) {
            return fromPath.absolutePath
        }

        return null
    }

    fun getInstallationGuidance(): String =
        "infrahubctl is required for schema validation and transform operations. " +
            "Install it with uv add \"infrahub-sdk[all]\" or see " +
            "https://docs.infrahub.app/python-sdk/guides/installation"

    fun invalidateCache() {
        cachedResult = null
        lastCheckTime = 0
    }

    private fun fileExists(path: String): Boolean {
        val file = File(path)
        if (file.exists()) {
            return true
        }

        if (System.getProperty("os.name").lowercase().contains("win") && !path.endsWith(".exe")) {
            return File("$path.exe").exists()
        }

        return false
    }
}
