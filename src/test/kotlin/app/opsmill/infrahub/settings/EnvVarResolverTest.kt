package app.opsmill.infrahub.settings

import org.junit.Assert.*
import org.junit.Test

class EnvVarResolverTest {

    @Test
    fun `resolveEnvVars returns same string when no placeholders`() {
        assertEquals("hello world", resolveEnvVars("hello world"))
    }

    @Test
    fun `resolveEnvVars replaces missing env var with empty string`() {
        assertEquals("token-", resolveEnvVars("token-\${env:VAR_NONEXISTENT_XYZ_12345}"))
    }

    @Test
    fun `resolveEnvVars handles empty input`() {
        assertEquals("", resolveEnvVars(""))
    }

    @Test
    fun `resolveEnvVars preserves text around placeholders`() {
        assertEquals("Bearer ", resolveEnvVars("Bearer \${env:MISSING_TOKEN_XYZ}"))
    }

    @Test
    fun `resolveEnvVars with complex pattern`() {
        assertEquals(
            "http://host/api?key=&user=admin&tls=false",
            resolveEnvVars("http://host/api?key=\${env:MISSING_API_KEY}&user=admin&tls=false")
        )
    }
}
