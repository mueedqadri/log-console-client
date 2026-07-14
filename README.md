# Java 8 Log Console

Interactive log status, download, chronological merge, and single-source live streaming over HTTP byte ranges.

## Build and run

Install a Java 8 JDK, then run:

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
