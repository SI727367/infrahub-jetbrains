package app.opsmill.infrahub.settings

/**
 * Resolves environment variable references in a string.
 * Supports ${env:VAR_NAME} syntax, equivalent to VSCode's substituteVariables().
 *
 * Example: "token-${env:API_TOKEN}" with API_TOKEN="abc123" → "token-abc123"
 */
fun resolveEnvVars(value: String): String {
    return value.replace(Regex("\\$\\{env:([A-Za-z0-9_]+)\\}")) { matchResult ->
        val envVar = matchResult.groupValues[1]
        System.getenv(envVar) ?: ""
    }
}
