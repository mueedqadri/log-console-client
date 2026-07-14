# Java 8 Log Console

Interactive log status, download, chronological merge, and single-source live streaming over HTTP byte ranges.

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
- Stream: arrows, Page Up/Down, Home/End, `1`–`4` level toggles, `/` text filter, `c` compact/raw, `f` follow, Esc/`q` back
- Downloads: all expanded sources are selected by default; filters are optional and outputs are staged resumably before chronological merge
