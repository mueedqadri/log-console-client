package com.example.logconsole;

import java.nio.file.Path;
import java.nio.file.Paths;

public final class CliOptions {
    public Path config = Paths.get("config", "log-console.json");
    public String username;
    public boolean noColor;
    public boolean debug;
    public boolean help;

    public static CliOptions parse(String[] args) {
        CliOptions options = new CliOptions();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--config".equals(arg)) options.config = Paths.get(requireValue(args, ++i, arg));
            else if ("--username".equals(arg)) options.username = requireValue(args, ++i, arg);
            else if ("--no-color".equals(arg)) options.noColor = true;
            else if ("--debug".equals(arg)) options.debug = true;
            else if ("--help".equals(arg) || "-h".equals(arg)) options.help = true;
            else throw new IllegalArgumentException("Unknown option: " + arg);
        }
        return options;
    }

    private static String requireValue(String[] args, int index, String option) {
        if (index >= args.length) throw new IllegalArgumentException(option + " requires a value");
        return args[index];
    }

    public static String usage() {
        return "Usage: log-console [--config <path>] [--username <name>] [--no-color] [--debug] [--help]";
    }
}
