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
│   ├── DeleteBranchAction.kt            # Delete branch (Phase 6)
│   ├── VisualizeSchemaAction.kt         # Fetch branch schema and open visualizer
│   ├── CheckSchemaFileAction.kt         # Validate one schema file with infrahubctl
│   ├── LoadSchemaFileAction.kt          # Load one schema file with infrahubctl
│   ├── CheckAllSchemaFilesAction.kt     # Validate configured schema directory with infrahubctl
│   ├── LoadAllSchemaFilesAction.kt      # Load configured schema directory with infrahubctl
│   ├── RunTransformAction.kt            # Run infrahubctl transform command
│   └── ShowInfrahubctlGuidanceAction.kt # Open infrahubctl installation guidance
├── common/
│   ├── ProjectTaskRunner.kt             # Background task helper for UI-safe async flows
│   └── SelectionDialogs.kt              # Reusable non-deprecated list selection dialogs
├── settings/
│   ├── InfrahubSettingsState.kt         # Persistent settings (servers, schema dir)
│   ├── InfrahubSettingsConfigurable.kt  # Settings UI panel
│   └── EnvVarResolver.kt               # ${env:VAR} substitution
├── graphql/
│   └── GraphQLSupport.kt               # Variable parsing, prompt dialog, result dialog
├── statusbar/
│   └── InfrahubStatusBarWidgetFactory.kt # Status bar widget, first-server version refresh
├── yaml/
│   ├── InfrahubGotoDeclarationHandler.kt # Go-to-definition for schema references
│   └── InfrahubStructureViewFactory.kt   # Structure/outline for schema YAML files
├── visualizer/
│   └── SchemaVisualizerPanel.kt         # JCEF-based schema viewer panel
├── infrahubctl/
│   ├── InfrahubctlChecker.kt            # infrahubctl path discovery and availability checks
│   └── InfrahubctlRunner.kt             # infrahubctl command execution and prompts
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
| 8 | Go-to-definition + document outline | Done |
| 9 | Schema visualizer (JCEF) | Done |
| 10 | Status bar | Done |
| 11 | infrahubctl integration | Done |

## Key Patterns

### Action Registration
Actions are registered in `plugin.xml` under `<actions>` and added to menu groups. Each action extends `AnAction` and implements `actionPerformed(e: AnActionEvent)`.

### Tree View Data Flow
`ServerTreeModel` implements `javax.swing.tree.TreeModel`. It holds `DefaultMutableTreeNode` instances for servers and branches. `ServerTreePanel` drives a 10-second `javax.swing.Timer` to refresh.

`SchemaTreePanel` uses SnakeYAML to parse `schemas/**/*.yml` and `schemas/**/*.yaml`, then builds a `DefaultTreeModel` with file nodes plus schema entry nodes for nodes, generics, attributes, and relationships. Its popup menu can also run infrahubctl schema check/load operations for the selected file or the whole configured schema directory.

`ServerTreePanel` renders configured servers and their branches, refreshes branch state on a timer, and now exposes context menu actions directly from the tree. Server nodes can refresh, create a branch, or open schema visualization for a selected branch. Branch nodes can delete non-default branches and then trigger a server tree refresh through `InfrahubProjectService`.

`YamlTreePanel` parses `.infrahub.yml` or `.infrahub.yaml`, renders sections like queries, transforms, artifact definitions, generators, and checks, and resolves linked file paths where present. Its popup menu can execute GraphQL queries and run infrahubctl transforms for selected transform items or artifact-linked transformations.

GraphQL query execution is launched from YAML query items. The flow parses variables from the query file, prompts for values, prompts for server and branch, executes against the selected branch, and shows formatted JSON results in a dialog. Long-running steps now use a small background-task helper instead of `GlobalScope`, keeping UI updates on the EDT.

The status bar widget polls the first configured server every 10 seconds and shows `Infrahub: v{version} ({serverName})`, `Server unreachable`, or `No server set`.

infrahubctl-backed actions prompt for server and then fetch real branches from the selected server before execution, resolve the executable from either the configured `infrahubctlPath` or the system `PATH`, and then run schema check, schema load, or transform commands with `INFRAHUB_ADDRESS` and `INFRAHUB_API_TOKEN` set in the process environment. A dedicated guidance action can open the installation documentation in the browser.

### Background Execution
`ProjectTaskRunner` wraps IntelliJ `Task.Backgroundable` and provides a small `onUiThread` helper. It is used for GraphQL branch loading, GraphQL execution, schema visualization fetches, and server tree context menu operations that mix background API calls with UI dialogs.

### Dialogs
Server and branch selection now uses `SelectionDialogs.chooseString`, a reusable `DialogWrapper`-based list picker that replaces deprecated `Messages.showChooseDialog` usage. Input and confirmation still use `Messages.showInputDialog` and `Messages.showYesNoDialog` where appropriate.

### Language Features
Schema YAML files now support go-to-definition and structure view integration. `InfrahubGotoDeclarationHandler` resolves combined `namespace + name` references across schema files, and `InfrahubStructureViewFactory` builds an outline for nodes, generics, attributes, and relationships.

### Webview Panels
Schema visualization uses `JBCefBrowser` and opens a new Infrahub tool window tab with schema content for the selected server and branch. A fallback message is shown if JCEF is unavailable.

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
