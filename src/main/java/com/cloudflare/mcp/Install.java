package com.cloudflare.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;

final class Install {

    private static final Logger log = LoggerFactory.getLogger(Install.class);

    private Install() {}

    static void run(String claudeBinary) {
        System.out.println("Cloudflare MCP Server — Installation");
        System.out.println("====================================");
        System.out.println();

        String token = System.getenv("CLOUDFLARE_API_TOKEN");
        if (token == null || token.isBlank()) {
            System.out.println("CLOUDFLARE_API_TOKEN is not set.");
            System.out.println();
            System.out.println("To create an API token:");
            System.out.println("  1. Go to https://dash.cloudflare.com/profile/api-tokens");
            System.out.println("  2. Click 'Create Token'");
            System.out.println("  3. Select permissions for the APIs you need");
            System.out.println("  4. Copy the token and set it:");
            System.out.println("     export CLOUDFLARE_API_TOKEN='your-token-here'");
            System.out.println();
            String v = Install.class.getPackage().getImplementationVersion();
            String jar = "cloudflare-mcp-" + (v != null ? v : "1.0.1") + ".jar";
            System.out.println("Then re-run: java -jar " + jar + " --install");
            return;
        }

        System.out.println("[ok] CLOUDFLARE_API_TOKEN is set");

        String jarPath = findJarPath();
        if (jarPath == null) {
            System.err.println("Could not determine JAR path. Register manually:");
            printManualInstructions();
            return;
        }

        System.out.println("[ok] JAR located at: " + jarPath);
        System.out.println();
        System.out.println("Registering with Claude Code...");

        var command = new ArrayList<String>();
        command.add(claudeBinary);
        command.add("mcp");
        command.add("add");
        command.add("--scope");
        command.add("user");
        command.add("--transport");
        command.add("stdio");
        command.add("cloudflare");
        command.add("--");
        command.add("java");
        command.add("-jar");
        command.add(jarPath);

        try {
            var process = new ProcessBuilder(command)
                    .inheritIO()
                    .start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println();
                System.out.println("Done! Restart Claude Code to use the Cloudflare MCP server.");
                System.out.println();
                System.out.println("Example prompts:");
                System.out.println("  > List my Cloudflare zones");
                System.out.println("  > Show DNS records for example.com");
                System.out.println("  > List my Workers scripts");
                System.out.println();
                System.out.println("To filter tools by category, use --include-tags:");
                System.out.println("  java -jar " + jarPath + " --include-tags \"DNS Records,Zones\"");
            } else {
                System.err.println("Claude registration failed (exit code " + exitCode + ").");
                printManualInstructions();
            }
        } catch (Exception e) {
            System.err.println("Failed to run claude command: " + e.getMessage());
            printManualInstructions();
        }
    }

    private static String findJarPath() {
        try {
            var source = Install.class.getProtectionDomain().getCodeSource();
            if (source != null && source.getLocation() != null) {
                Path path = Path.of(source.getLocation().toURI());
                File file = path.toFile();
                if (file.isFile() && file.getName().endsWith(".jar")) {
                    return file.getAbsolutePath();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void printManualInstructions() {
        System.out.println();
        System.out.println("Manual registration:");
        System.out.println("  claude mcp add --scope user --transport stdio cloudflare -- \\");
        String v = Install.class.getPackage().getImplementationVersion();
        String jar = "cloudflare-mcp-" + (v != null ? v : "1.0.1") + ".jar";
        System.out.println("    java -jar /path/to/" + jar);
    }
}
