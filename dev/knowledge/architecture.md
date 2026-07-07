# Plugin Architecture

## Overview

The Infrahub JetBrains Plugin provides development tools for Infrahub, an infrastructure automation platform. It connects to Infrahub servers and provides YAML schema support, GraphQL query execution, branch management, and schema visualization. This is a port of the [Infrahub VSCode Extension](https://github.com/opsmill/infrahub-vscode) to the IntelliJ Platform using Kotlin and the IntelliJ Platform SDK.

## Entry Point

`src/main/kotlin/app/opsmill/infrahub/toolwindow/InfrahubToolWindowFactory.kt` is the main activation point. It:
1. Creates three tool window tabs (Servers, Schema, YAML)
2. Wires up `ServerTreePanel` with auto-refresh (10-second interval)
3. Wires up `SchemaTreePanel` to parse and display schema files from the configured schema directory
4. Wires up `YamlTreePanel` to parse and display sections from `.infrahub.yml` or `.infrahub.yaml`

## Activation

The plugin activates on IDE startup. The tool window is always visible once the plugin is installed.

## Component Map

```
src/main/kotlin/app/opsmill/infrahub/
├── toolwindow/
│   ├── InfrahubToolWindowFactory.kt      # Entry point, tab wiring
│   ├── schema/
│   │   ├── SchemaTreeModel.kt            # Tree model and node types for schema files
│   │   └── SchemaTreePanel.kt            # Schema panel, YAML parsing, file navigation
│   ├── server/
│   │   ├── ServerTreeModel.kt            # Tree model for servers and branches
│   │   └── ServerTreePanel.kt            # Servers panel with auto-refresh
│   └── yaml/
│       ├── YamlTreeModel.kt              # Tree model and node types for .infrahub.yml sections
│       └── YamlTreePanel.kt              # YAML panel, parsing, file navigation
├── actions/
│   ├── NewBranchAction.kt               # Create branch (Phase 6)
│   └── DeleteBranchAction.kt            # Delete branch (Phase 6)
├── settings/
│   ├── InfrahubSettingsState.kt         # Persistent settings (servers, schema dir)
│   ├── InfrahubSettingsConfigurable.kt  # Settings UI panel
│   └── EnvVarResolver.kt               # ${env:VAR} substitution
├── graphql/
│   └── GraphQLSupport.kt               # Variable parsing, prompt dialog, result dialog
└── api/
    ├── InfrahubClient.kt               # HTTP client (OkHttp + kotlinx-serialization)
    ├── InfrahubClientManager.kt        # Client cache, keyed by server name
    └── models.kt                       # BranchInfo, SchemaNode, etc.
```

## Migration Status

| Phase | What | Status |
|-------|------|--------|
| 1 | Gradle project bootstrap | Done |
| 2 | Settings / Configuration Service | Done |
| 3 | API layer + Server tree panel | Done |
| 6 | Branch management actions | Done |
| 4 | Schema tree panel | Done |
| 5 | YAML tree panel | Done |
| 7 | GraphQL query execution | Done |
| 8 | Go-to-definition + document outline | TODO |
| 9 | Schema visualizer (JCEF) | TODO |
| 10 | Status bar | TODO |
| 11 | infrahubctl integration | TODO |

## Key Patterns

### Action Registration
Actions are registered in `plugin.xml` under `<actions>` and added to menu groups. Each action extends `AnAction` and implements `actionPerformed(e: AnActionEvent)`.

### Tree View Data Flow
`ServerTreeModel` implements `javax.swing.tree.TreeModel`. It holds `DefaultMutableTreeNode` instances for servers and branches. `ServerTreePanel` drives a 10-second `javax.swing.Timer` to refresh.

`SchemaTreePanel` uses SnakeYAML to parse `schemas/**/*.yml` and `schemas/**/*.yaml`, then builds a `DefaultTreeModel` with file nodes plus schema entry nodes for nodes, generics, attributes, and relationships.

`YamlTreePanel` parses `.infrahub.yml` or `.infrahub.yaml`, renders sections like queries, transforms, artifact definitions, generators, and checks, and resolves linked file paths where present.

GraphQL query execution is launched from YAML query items. The flow parses variables from the query file, prompts for values, prompts for server and branch, executes against the selected branch, and shows formatted JSON results in a dialog.

### Dialogs
Server and branch selection uses `Messages.showChooseDialog` (radio button list). Input and confirmation use `Messages.showInputDialog` and `Messages.showYesNoDialog`.

### Language Features
Go-to-definition and document outline are planned for Phase 8 via `PsiReferenceProvider` and `StructureViewExtension`.

### Webview Panels
Schema visualizer (Phase 9) will use JCEF (`JBCefBrowser`) to load the `infrahub-schema-visualizer` JS package.

## Configuration

Settings in **Settings -> Infrahub**:
- `servers` - List of server configs: `{name, address, apiToken, tlsInsecure}`
- `schemaDirectory` - Path to schema files (default: `schemas`)
- `infrahubctlPath` - Custom path to `infrahubctl` executable

API tokens support `${env:VAR_NAME}` syntax resolved by `EnvVarResolver`.

## Dependencies

| Package | Purpose |
|---------|---------|
| `okhttp3` | HTTP client for API calls |
| `kotlinx-serialization-json` | JSON parsing matching `@Serializable` models |
| `org.yaml:snakeyaml` | YAML parsing for schema and YAML panels |
| IntelliJ Platform SDK | UI, actions, tool windows, JCEF |

## Build

Kotlin + Gradle with IntelliJ Platform Gradle Plugin.
- Source: `src/main/kotlin/`
- Tests: `src/test/kotlin/`
- Build: `./gradlew buildPlugin` -> `build/distributions/infrahub-jetbrains-*.zip`
