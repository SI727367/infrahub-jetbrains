package app.opsmill.infrahub.api

import junit.framework.TestCase
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.TimeUnit

class InfrahubClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: InfrahubClient

    private fun setUp(address: String = "http://localhost", token: String? = null, tlsInsecure: Boolean = false) {
        server = MockWebServer()
        server.start()
        client = InfrahubClient(address, token, tlsInsecure)
    }

    private fun tearDown() {
        try {
            server.shutdown()
        } catch (_: Exception) {
            // ignore
        }
    }

    // Test: getVersion parses version correctly from /api/ response
    @Test
    fun `getVersion parses version correctly`() {
        setUp()
        val expectedVersion = "1.0.0"
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"api_info": {"version": "$expectedVersion", "version_min": "0.150200.0"}}""")
        )

        val version = client.getVersion()
        TestCase.assertEquals(expectedVersion, version)
        val recordedRequest = server.takeRequest()
        TestCase.assertEquals("GET", recordedRequest.method)
        TestCase.assertEquals("/api/", recordedRequest.path)
    }

    // Test: getVersion throws on malformed response
    @Test
    fun `getVersion throws on malformed JSON`() {
        setUp()
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("not json at all")
        )

        try {
            client.getVersion()
            TestCase.fail("Expected exception")
        } catch (e: Exception) {
            TestCase.assertTrue(e.message?.contains("Failed to parse version response") == true)
        }
    }

    // Test: getAllBranches returns expected list
    @Test
    fun `getAllBranches returns expected branch list`() {
        setUp()
        val branchResponse = """
        {
          "data": {
            "Branch": {
              "edges": [
                {
                  "node": {
                    "id": "branch-1",
                    "name": "main",
                    "description": "Default branch",
                    "origin_branch": null,
                    "branched_from": null,
                    "is_default": true,
                    "sync_with_git": false,
                    "has_schema_changes": false
                  }
                },
                {
                  "node": {
                    "id": "branch-2",
                    "name": "feature/new-schema",
                    "description": "New schema changes",
                    "origin_branch": "origin/main",
                    "branched_from": "main",
                    "is_default": false,
                    "sync_with_git": true,
                    "has_schema_changes": true
                  }
                }
              ]
            }
          }
        }
        """.trimIndent()

        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(branchResponse)
        )

        val branches = client.getAllBranches()
        TestCase.assertEquals(2, branches.size)
        TestCase.assertEquals("main", branches[0].name)
        TestCase.assertTrue(branches[0].is_default)
        TestCase.assertEquals("feature/new-schema", branches[1].name)
        TestCase.assertTrue(branches[1].sync_with_git)
        TestCase.assertTrue(branches[1].has_schema_changes)
    }

    // Test: getAllBranches returns empty list when no edges
    @Test
    fun `getAllBranches returns empty list when no branches`() {
        setUp()
        val emptyResponse = """{"data": {"Branch": {"edges": []}}}"""
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(emptyResponse)
        )

        val branches = client.getAllBranches()
        TestCase.assertEquals(0, branches.size)
    }

    // Test: Auth token is added as X-INFRAHUB-KEY header
    @Test
    fun `auth token is sent as X-INFRAHUB-KEY header`() {
        val testToken = "test-secret-token-12345"
        setUp(address = "http://localhost", token = testToken)
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"data": {"Branch": {"edges": []}}}""")
        )

        client.getAllBranches()
        val recordedRequest = server.takeRequest()
        TestCase.assertEquals(testToken, recordedRequest.getHeader("X-INFRAHUB-KEY"))
    }

    // Test: createBranch returns branch id on success
    @Test
    fun `createBranch returns branch id on success`() {
        setUp()
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                {
                  "data": {
                    "BranchCreate": {
                      "ok": true,
                      "errors": [],
                      "branch": {"id": "new-branch-id-abc123"}
                    }
                  }
                }
                """.trimIndent()
            )
        )

        val input = BranchCreateInput(name = "feature/test", description = "Test branch", is_default = false, sync_with_git = false)
        val resultId = client.createBranch(input)
        TestCase.assertEquals("new-branch-id-abc123", resultId)
    }

    // Test: createBranch throws on failure response
    @Test
    fun `createBranch throws on failure response`() {
        setUp()
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                {
                  "data": {
                    "BranchCreate": {
                      "ok": false,
                      "errors": ["Branch name already exists"]
                    }
                  }
                }
                """.trimIndent()
            )
        )

        try {
            client.createBranch(BranchCreateInput(name = "duplicate"))
            TestCase.fail("Expected exception")
        } catch (e: Exception) {
            TestCase.assertTrue(e.message?.contains("BranchCreate failed") == true)
        }
    }

    // Test: deleteBranch returns true on success
    @Test
    fun `deleteBranch returns true on success`() {
        setUp()
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                {
                  "data": {
                    "BranchDelete": {
                      "ok": true,
                      "errors": []
                    }
                  }
                }
                """.trimIndent()
            )
        )

        val result = client.deleteBranch("feature/to-delete")
        TestCase.assertTrue(result)
    }

    // Test: deleteBranch returns false on failure
    @Test
    fun `deleteBranch returns false on failure`() {
        setUp()
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                {
                  "data": {
                    "BranchDelete": {
                      "ok": false,
                      "errors": ["Cannot delete default branch"]
                    }
                  }
                }
                """.trimIndent()
            )
        )

        val result = client.deleteBranch("main")
        TestCase.assertFalse(result)
    }

    // Test: executeGraphQL returns parsed JSON
    @Test
    fun `executeGraphQL returns parsed JSON response`() {
        setUp()
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                {
                  "data": {
                    "NodeExample": {
                      "edges": [{"node": {"id": "1", "display_label": "Test"}}]
                    }
                  }
                }
                """.trimIndent()
            )
        )

        val query = "{ NodeExample { edges { node { id display_label } } } }"
        val result = client.executeGraphQL(query)
        TestCase.assertTrue(result.containsKey("data"))
    }

    // Test: getSchema returns SchemaResponse with nodes and generics
    @Test
    fun `getSchema returns SchemaResponse`() {
        setUp()
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                {
                  "nodes": [
                    {
                      "name": "Device",
                      "namespace": "Core",
                      "attributes": [
                        {"name": "name", "kind": "AttributeString", "type": "String"}
                      ]
                    }
                  ],
                  "generics": [
                    {
                      "name": "Tagged",
                      "namespace": "Core",
                      "attributes": [
                        {"name": "tags", "kind": "AttributeInteger", "type": "Int"}
                      ]
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val schema = client.getSchema("main")
        TestCase.assertEquals(1, schema.nodes.size)
        TestCase.assertEquals("Device", schema.nodes[0].name)
        TestCase.assertEquals("Core", schema.nodes[0].namespace)
        TestCase.assertEquals(1, schema.generics.size)
        TestCase.assertEquals("Tagged", schema.generics[0].name)
    }

    // Test: connection timeout throws exception
    @Test
    fun `getVersion throws on connection error`() {
        server = MockWebServer()
        server.start()
        val badClient = InfrahubClient("http://localhost:${server.port}", tlsInsecure = true)
        server.shutdown() // Shutdown server so connection fails

        try {
            badClient.getVersion()
            TestCase.fail("Expected exception")
        } catch (e: Exception) {
            // Connection error or empty response is expected
        }
    }
}
