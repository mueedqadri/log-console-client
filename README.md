# Java 8 Log Console

Interactive log status, parallel combined-log downloads, and single-source log streaming over HTTP byte ranges.

## Build and run

From the workspace root, install a Java 8 (or later) JDK and run:

```sh
node setup-client.js
```

The setup script is self-contained: copying just `setup-client.js` to another computer recreates this entire client project. It does not require npm dependencies and does not build the project. When ready, build manually with the Maven Wrapper; this requires JDK 8 or later and network access for Maven unless its artifacts are already cached.

To run the build manually from this directory instead:

```sh
./mvnw verify
./bin/log-console.sh
```

On Windows:

```bat
mvnw.cmd verify
bin\log-console.bat
```

The Maven wrapper downloads Maven when it is not already installed. Set `LOG_CONSOLE_PASSWORD=demo` for the bundled test server, or leave it unset and enter `demo` at the secure prompt.

## Configuration

`config/log-console.json` contains reusable connections, environments, locations, parsers, and applications. Application sources are generated as the Cartesian product of `locations` and arbitrary `dimensions`.

Supported placeholders include `{application}`, `{application.id}`, `{environment}`, `{environment.name}`, `{location.id}`, `{location.name}`, arbitrary `{location.<property>}`, arbitrary `{dimension.<name>}`, and `{date}` for archives and output names. `archiveDatePattern` and `outputDatePattern` control their respective date formatting. Unknown or malformed placeholders fail startup.

Passwords are resolved from `passwordEnvVar`, then prompted. Only the last username is persisted. Plain HTTP is limited to loopback unless `allowInsecureHttp` is enabled explicitly.

## Main controls

- Menus: arrows, numbers, Enter, Esc
- Stream: arrows, Page Up/Down, Home/End, `1`–`4` level toggles, `/` text filter, `c` compact/raw, `r` fetch/refresh, `f` follow, Esc/`q` back. The viewer does not poll in the background.
- Downloads: choose an application and the client downloads current sources in parallel with normal HTTP GET requests. Each source has its own persistent progress row; successful temporary parts are combined in source order with a source header between files.

The terminal UI uses JLine capabilities on Windows Terminal, PowerShell, classic `cmd.exe`, macOS, and Linux. If cursor addressing or Unicode blocks are unavailable, progress automatically falls back to safe ASCII, append-only output. `--no-color` disables dashboard and log highlighting without disabling interactive cursor handling.
