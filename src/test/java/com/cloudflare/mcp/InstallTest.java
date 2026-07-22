package com.cloudflare.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class InstallTest {

    private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    private PrintStream originalOut;
    private PrintStream originalErr;

    @BeforeEach
    void captureOutput() {
        originalOut = System.out;
        originalErr = System.err;
        System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreOutput() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    void missing_token_prints_setup_instructions() {
        Install.run("claude", null, "/tmp/app.jar");
        String out = stdout.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("CLOUDFLARE_API_TOKEN is not set"));
        assertTrue(out.contains("dash.cloudflare.com/profile/api-tokens"));
    }

    @Test
    void blank_token_prints_setup_instructions() {
        Install.run("claude", "   ", "/tmp/app.jar");
        assertTrue(stdout.toString(StandardCharsets.UTF_8)
                .contains("CLOUDFLARE_API_TOKEN is not set"));
    }

    @Test
    void missing_jar_path_prints_manual_instructions() {
        Install.run("claude", "token-123", null);
        assertTrue(stderr.toString(StandardCharsets.UTF_8)
                .contains("Could not determine JAR path"));
        assertTrue(stdout.toString(StandardCharsets.UTF_8)
                .contains("Manual registration"));
    }

    @Test
    void successful_registration_prints_examples() {
        Install.run("true", "token-123", "/tmp/app.jar");
        String out = stdout.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("Done! Restart Claude Code"));
        assertTrue(out.contains("Example prompts"));
    }

    @Test
    void failed_registration_prints_manual_instructions() {
        Install.run("false", "token-123", "/tmp/app.jar");
        assertTrue(stderr.toString(StandardCharsets.UTF_8)
                .contains("Claude registration failed"));
        assertTrue(stdout.toString(StandardCharsets.UTF_8)
                .contains("Manual registration"));
    }

    @Test
    void unlaunchable_binary_prints_manual_instructions() {
        Install.run("/nonexistent/binary-xyz", "token-123", "/tmp/app.jar");
        assertTrue(stderr.toString(StandardCharsets.UTF_8)
                .contains("Failed to run claude command"));
        assertTrue(stdout.toString(StandardCharsets.UTF_8)
                .contains("Manual registration"));
    }

    @Test
    void env_entrypoint_runs_without_jar_context() {
        // In tests the code source is a classes directory, so findJarPath() yields null;
        // with no token this prints setup instructions, with a token the manual path.
        Install.run("claude");
        String out = stdout.toString(StandardCharsets.UTF_8);
        assertTrue(out.contains("Cloudflare MCP Server"));
    }
}
