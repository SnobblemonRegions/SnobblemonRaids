package com.cobbleraids.showdown;

import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.regex.Pattern;

/** Pure adaptation of native raid startup commands. */
public final class RaidBootstrap {
    private static final Pattern TEAM = Pattern.compile(">p[1-9][0-9]* team [1-9][0-9]*(?:,[1-9][0-9]*)*");
    private RaidBootstrap() {}
    public static String[] withoutUnrequestedTeamChoices(String[] messages) {
        if (messages == null) return null;
        boolean choices = false;
        String start = null;
        for (String message : messages) {
            if (message == null) continue;
            if (message.startsWith(">start ")) { if (start != null) return messages; start = message.substring(7); }
            if (TEAM.matcher(message).matches()) choices = true;
        }
        if (!choices || start == null) return messages;
        try {
            var root = JsonParser.parseString(start);
            if (!root.isJsonObject()) return messages;
            var format = root.getAsJsonObject().get("format");
            if (format == null || !format.isJsonObject()) return messages;
            var type = format.getAsJsonObject().get("gameType");
            var rules = format.getAsJsonObject().get("ruleset");
            if (type == null || !type.isJsonPrimitive() || !"raid".equals(type.getAsString())
                    || rules == null || !rules.isJsonArray() || !rules.getAsJsonArray().isEmpty()) return messages;
        } catch (JsonParseException invalid) { return messages; }
        var kept = new ArrayList<String>(messages.length);
        for (String message : messages) if (message == null || !TEAM.matcher(message).matches()) kept.add(message);
        return kept.toArray(String[]::new);
    }
}
