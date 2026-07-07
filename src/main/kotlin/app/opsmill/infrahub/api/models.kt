package app.opsmill.infrahub.api

import kotlinx.serialization.Serializable

@Serializable
data class BranchInfo(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val origin_branch: String = "",
    val branched_from: String = "",
    val is_default: Boolean = false,
    val sync_with_git: Boolean = false,
    val has_schema_changes: Boolean = false
)

@Serializable
data class BranchCreateInput(
    val name: String,
    val description: String? = null,
    val is_default: Boolean? = null,
    val sync_with_git: Boolean? = null
)

@Serializable
data class SchemaResponse(
    val nodes: List<SchemaNode> = emptyList(),
    val generics: List<SchemaGeneric> = emptyList()
)

@Serializable
data class SchemaNode(
    val name: String = "",
    val namespace: String = "",
    val attributes: List<SchemaAttribute> = emptyList()
)

@Serializable
data class SchemaGeneric(
    val name: String = "",
    val namespace: String = "",
    val attributes: List<SchemaAttribute> = emptyList()
)

@Serializable
data class SchemaAttribute(
    val name: String = "",
    val kind: String = "",
    val type: String = ""
)
