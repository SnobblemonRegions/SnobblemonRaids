package com.cobbleraids;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every service that defines a per-server {@code tick(MinecraftServer)} must be registered to run in {@code CobbleRaids}.
 *
 * <p>A service whose tick is never registered compiles, passes its unit tests and does nothing. That is exactly how the Raid Radar went dead: a merge
 * conflict kept one side's tick line and dropped the radar's, and the radar showed a blank map in game with no error anywhere.
 */
class TickRegistrationTest {

    private static final Pattern TICK = Pattern.compile("public\\s+static\\s+void\\s+tick\\(\\s*MinecraftServer\\s+\\w+\\s*\\)");

    @Test
    void everyServiceTickIsRegistered() throws IOException {
        Path sources = Path.of("src/main/java");
        String registry = Files.readString(sources.resolve("com/cobbleraids/CobbleRaids.java"));
        List<String> unregistered = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sources)) {
            for (Path file : (Iterable<Path>) files.filter(path -> path.toString().endsWith(".java"))::iterator) {
                if (!TICK.matcher(Files.readString(file)).find()) continue;
                String type = file.getFileName().toString().replace(".java", "");
                if (!registry.contains(type + "::tick")) unregistered.add(type);
            }
        }
        assertTrue(unregistered.isEmpty(), "These define tick(MinecraftServer) but CobbleRaids never registers them, so they never run: " + unregistered);
    }
}
