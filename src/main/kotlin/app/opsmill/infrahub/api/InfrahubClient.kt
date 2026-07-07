package app.opsmill.infrahub.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Duration
import javax.net.ssl.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * HTTP client for Infrahub API, equivalent to infrahub-sdk's InfrahubClient.
 * Supports token authentication, TLS skipping, and timeout handling.
 * Uses kotlinx-serialization for JSON parsing (matching @Serializable models).
 */
class InfrahubClient(
    val address: String,
    token: String? = null,
    tlsInsecure: Boolean = false
) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val trustAllCerts: Array<TrustManager> = arrayOf(
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
    )

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder().also { builder ->
        // Token authentication — add X-INFRAHUB-KEY header
        token?.let { t ->
            builder.addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("X-INFRAHUB-KEY", t)
                    .build()
                chain.proceed(request)
            }
        }
        // TLS skipping
        if (tlsInsecure) {
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, trustAllCerts, SecureRandom())
            }
            builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
        }
        // Timeouts
        builder.connectTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofSeconds(30))
            .writeTimeout(Duration.ofSeconds(30))
    }.build()

    init {
        // Ensure address ends with slash for path concatenation
        if (address.endsWith("/")) {
            // ok
        } else {
            // address stored as-is, paths are concatenated with leading /
        }
    }

    private suspend fun makeJsonRequest(url: String, method: String = "GET", body: String? = null): JsonObject {
        return suspendCoroutine { continuation ->
            val requestBuilder = Request.Builder().url(url)
            if (method == "POST" && body != null) {
                val requestBody = body.toRequestBody("application/json".toMediaType())
                requestBuilder.post(requestBody)
            } else {
                requestBuilder.get()
            }

            okHttpClient.newCall(requestBuilder.build()).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string() ?: run {
                        continuation.resumeWithException(IOException("Empty response"))
                        return
                    }
                    try {
                        val jsonElement = json.parseToJsonElement(responseBody)
                        if (jsonElement is JsonObject) {
                            continuation.resume(jsonElement)
                        } else {
                            continuation.resumeWithException(
                                IOException("Unexpected response format: $responseBody")
                            )
                        }
                    } catch (e: Exception) {
                        continuation.resumeWithException(
                            IOException("Failed to parse JSON response: $responseBody", e)
                        )
                    }
                }
            })
        }
    }

    /**
     * Get Infrahub API version.
     * GET /api/ → parses api_info.version
     */
    suspend fun getVersion(): String = suspendCoroutine { continuation ->
        val request = Request.Builder()
            .url("$address/api/")
            .get()
            .build()

        okHttpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: run {
                    continuation.resumeWithException(IOException("Empty response"))
                    return
                }
                try {
                    val jsonElement = json.parseToJsonElement(body) as? JsonObject
                    val apiInfo = jsonElement?.get("api_info") as? JsonObject ?: run {
                        continuation.resumeWithException(
                            IOException("No api_info in response: $body")
                        )
                        return
                    }
                    val version = apiInfo["version"]?.jsonPrimitive?.content
                    if (version != null) {
                        continuation.resume(version)
                    } else {
                        continuation.resumeWithException(
                            IOException("No version in api_info: $body")
                        )
                    }
                } catch (e: Exception) {
                    continuation.resumeWithException(
                        IOException("Failed to parse version response: $body", e)
                    )
                }
            }
        })
    }

    /**
     * Get all branches.
     * POST /graphql → GraphQL query for Branch list
     */
    suspend fun getAllBranches(): List<BranchInfo> = suspendCoroutine { continuation ->
        val query = """
            {
              Branch {
                edges {
                  node {
                    id
                    name
                    description
                    origin_branch
                    branched_from
                    is_default
                    sync_with_git
                    has_schema_changes
                  }
                }
              }
            }
        """.trimIndent()

        val requestBody = FormBody.Builder()
            .add("query", query)
            .build()

        val request = Request.Builder()
            .url("$address/graphql")
            .post(requestBody)
            .build()

        okHttpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: run {
                    continuation.resumeWithException(IOException("Empty response"))
                    return
                }
                try {
                    val jsonElement = json.parseToJsonElement(body) as? JsonObject
                    val data = jsonElement?.get("data") as? JsonObject
                    val branchData = data?.get("Branch") as? JsonObject
                    val edges = branchData?.get("edges")?.let {
                        it as? JsonArray
                    } ?: emptyList<JsonElement>()

                    val result = edges.mapNotNull { edge ->
                        val edgeObj = edge as? JsonObject ?: return@mapNotNull null
                        val node = edgeObj["node"] as? JsonObject ?: return@mapNotNull null
                        try {
                            json.decodeFromString<BranchInfo>(node.toString())
                        } catch (e: Exception) {
                            null
                        }
                    }
                    continuation.resume(result)
                } catch (e: Exception) {
                    continuation.resumeWithException(
                        IOException("Failed to parse branches response: $body", e)
                    )
                }
            }
        })
    }

    /**
     * Create a new branch.
     * POST /graphql → GraphQL mutation BranchCreate
     */
    suspend fun createBranch(input: BranchCreateInput): String = suspendCoroutine { continuation ->
        val varDefs = mutableListOf<String>()
        val varValues = mutableListOf<String>()

        input.name.let {
            varDefs.add("\$name: String!")
            varValues.add("\"${it.replace("\"", "\\\"")}\"")
        }
        input.description?.let {
            varDefs.add("\$description: String")
            varValues.add("\"${it.replace("\"", "\\\"")}\"")
        } ?: varValues.add("null")
        input.is_default?.let {
            varDefs.add("\$is_default: Boolean")
            varValues.add(if (it) "true" else "false")
        } ?: varValues.add("null")
        input.sync_with_git?.let {
            varDefs.add("\$sync_with_git: Boolean")
            varValues.add(if (it) "true" else "false")
        } ?: varValues.add("null")

        val varDefsStr = varDefs.joinToString(", ")
        val varValuesStr = varValues.joinToString(", ")

        val query = """
            mutation CreateBranch($varDefsStr) {
              BranchCreate(input: {
                name: $varValuesStr
              }) {
                ok
                errors
                branch {
                  id
                }
              }
            }
        """.trimIndent()

        val requestBody = FormBody.Builder()
            .add("query", query)
            .build()

        val request = Request.Builder()
            .url("$address/graphql")
            .post(requestBody)
            .build()

        okHttpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: run {
                    continuation.resumeWithException(IOException("Empty response"))
                    return
                }
                try {
                    val jsonElement = json.parseToJsonElement(body) as? JsonObject
                    val data = jsonElement?.get("data") as? JsonObject
                    val branchCreate = data?.get("BranchCreate") as? JsonObject
                    val ok = branchCreate?.get("ok")?.jsonPrimitive?.boolean ?: false
                    val errors = branchCreate?.get("errors")?.let { it as? JsonArray } ?: emptyList()

                    if (ok) {
                        val branch = branchCreate["branch"] as? JsonObject
                        val id = branch?.get("id")?.jsonPrimitive?.content
                        if (id != null) {
                            continuation.resume(id)
                        } else {
                            continuation.resumeWithException(
                                IOException("BranchCreate returned ok=true but no id: $body")
                            )
                        }
                    } else {
                        continuation.resumeWithException(
                            IOException("BranchCreate failed: errors=$errors, body=$body")
                        )
                    }
                } catch (e: Exception) {
                    continuation.resumeWithException(
                        IOException("Failed to parse createBranch response: $body", e)
                    )
                }
            }
        })
    }

    /**
     * Delete a branch by name.
     * POST /graphql → GraphQL mutation BranchDelete
     */
    suspend fun deleteBranch(name: String): Boolean = suspendCoroutine { continuation ->
        val query = """
            mutation DeleteBranch(\$name: String!) {
              BranchDelete(input: { name: \$name }) {
                ok
                errors
              }
            }
        """.trimIndent()

        val requestBody = FormBody.Builder()
            .add("query", query)
            .build()

        val request = Request.Builder()
            .url("$address/graphql")
            .post(requestBody)
            .build()

        okHttpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: run {
                    continuation.resumeWithException(IOException("Empty response"))
                    return
                }
                try {
                    val jsonElement = json.parseToJsonElement(body) as? JsonObject
                    val data = jsonElement?.get("data") as? JsonObject
                    val branchDelete = data?.get("BranchDelete") as? JsonObject
                    val ok = branchDelete?.get("ok")?.jsonPrimitive?.boolean ?: false
                    continuation.resume(ok)
                } catch (e: Exception) {
                    continuation.resumeWithException(
                        IOException("Failed to parse deleteBranch response: $body", e)
                    )
                }
            }
        })
    }

    /**
     * Execute a custom GraphQL query.
     * POST /graphql → POST with query and variables
     */
    suspend fun executeGraphQL(
        query: String,
        variables: Map<String, Any?> = emptyMap(),
        branch: String? = null
    ): JsonObject = suspendCoroutine { continuation ->
        val requestBody = FormBody.Builder()
            .add("query", query)
            .apply {
                if (variables.isNotEmpty()) {
                    add("variables", json.encodeToString(variables))
                }
                branch?.let {
                    add("branch", it)
                }
            }
            .build()

        val request = Request.Builder()
            .url("$address/graphql")
            .post(requestBody)
            .build()

        okHttpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: run {
                    continuation.resumeWithException(IOException("Empty response"))
                    return
                }
                try {
                    val jsonElement = json.parseToJsonElement(body)
                    if (jsonElement is JsonObject) {
                        continuation.resume(jsonElement)
                    } else {
                        continuation.resumeWithException(
                            IOException("Unexpected response format: $body")
                        )
                    }
                } catch (e: Exception) {
                    continuation.resumeWithException(
                        IOException("Failed to parse GraphQL response: $body", e)
                    )
                }
            }
        })
    }

    /**
     * Get schema for a branch.
     * GET /api/schema?branch={name}
     */
    suspend fun getSchema(branch: String): SchemaResponse = suspendCoroutine { continuation ->
        val request = Request.Builder()
            .url("$address/api/schema?branch=$branch")
            .get()
            .build()

        okHttpClient.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: run {
                    continuation.resumeWithException(IOException("Empty response"))
                    return
                }
                try {
                    val jsonElement = json.parseToJsonElement(body) as? JsonObject
                    val nodesJson = jsonElement?.get("nodes") as? JsonArray ?: JsonArray(emptyList())
                    val genericsJson = jsonElement?.get("generics") as? JsonArray ?: JsonArray(emptyList())

                    val nodes = nodesJson.mapNotNull { nodeJson ->
                        try {
                            json.decodeFromString<SchemaNode>(nodeJson.toString())
                        } catch (e: Exception) {
                            null
                        }
                    }
                    val generics = genericsJson.mapNotNull { genJson ->
                        try {
                            json.decodeFromString<SchemaGeneric>(genJson.toString())
                        } catch (e: Exception) {
                            null
                        }
                    }
                    continuation.resume(SchemaResponse(nodes, generics))
                } catch (e: Exception) {
                    continuation.resumeWithException(
                        IOException("Failed to parse schema response: $body", e)
                    )
                }
            }
        })
    }
}
