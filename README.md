# Infrahub JetBrains Plugin

A JetBrains IDE plugin that brings [Infrahub](https://github.com/opsmill/infrahub) development tooling to IntelliJ IDEA and other JetBrains IDEs. This is a port of the [Infrahub VSCode Extension](https://github.com/opsmill/infrahub-vscode) to the IntelliJ Platform.

## Overview

The Infrahub JetBrains Plugin provides the same core workflow as the VSCode extension — server connectivity, schema validation, GraphQL query execution, branch management, and schema visualization — built natively for the IntelliJ Platform using Kotlin and the IntelliJ Platform SDK.

## Features

### 🔗 Server Management

- Connect to multiple Infrahub servers with API token authentication
- Real-time server status in the status bar (refreshes every 10 seconds)
- Environment variable substitution in token fields (`${env:VAR_NAME}`)
- Optional TLS certificate verification bypass for development environments

### 🌳 Tool Window Panels

- **Servers**: Browse connected servers and their branches with online/offline indicators
- **Schema**: Navigate schema YAML files in your project's `schemas/` directory
- **YAML**: Explore `.infrahub.yml` structure — queries, transforms, artifact definitions, generators

### 📝 YAML Intelligence

- Go-to-definition for schema node/generic references
- Structure view (outline) for schema YAML files showing nodes, generics, attributes, and relationships
- JSON schema validation for `schemas/` and `models/` directories against the Infrahub schema

### 🚀 GraphQL Integration

- Execute GraphQL queries directly from the YAML tree view
- Interactive variable prompting (required and optional)
- Branch-aware query execution
- Results displayed in a formatted panel

### 🔀 Branch Management

- Create new branches with name, description, and Git sync options
- Delete branches with confirmation (default branch is protected)
- Branch list visible under each connected server

### ⚙️ Schema Operations

- Check and load schema files via `infrahubctl` CLI
- Run Jinja2 and Python transforms
- Context menu actions on schema files in the project tree

### 📊 Schema Visualizer

- Interactive graph visualization of schema nodes and relationships
- Powered by JCEF (JetBrains Chromium Embedded Framework)
- Accessible per server/branch from the Servers panel

## Requirements

- IntelliJ IDEA 2026.1 or later (Ultimate or Community)
- Active Infrahub server instance
- Valid API token for authentication
- `infrahubctl` CLI for schema check/load and transform operations

## Building from Source

```bash
git clone https://github.com/opsmill/infrahub-jetbrains.git
cd infrahub-jetbrains

# Build the plugin zip
./gradlew buildPlugin

# The plugin zip will be at build/distributions/infrahub-jetbrains-*.zip
```

### Prerequisites

- JetBrains Runtime (JCEF) 25.x — download via IntelliJ's SDK manager
- Gradle 9.x (provided via the wrapper, no separate install needed)

## Installing the Plugin

**From disk (development builds):**

1. Open IntelliJ IDEA
2. Go to **Settings → Plugins → ⚙️ → Install Plugin from Disk**
3. Select the `build/distributions/infrahub-jetbrains-*.zip`
4. Restart the IDE

## Configuration

After installing, go to **Settings → Infrahub** to configure:

- **Servers**: Add one or more Infrahub servers (name, address, API token, TLS settings)
- **Schema Directory**: Path to your schema files (default: `schemas`)
- **infrahubctl Path**: Custom path to the `infrahubctl` executable (optional)

API tokens support environment variable substitution:
```
${env:INFRAHUB_API_TOKEN}
```

## Contributing

Contributions are welcome. Please open issues and pull requests on [GitHub](https://github.com/opsmill/infrahub-jetbrains).

## License

See the [LICENSE](LICENSE) file for details.
