package com.cobbleraids.worldboss;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.server.players.GameProfileCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class WorldBossProfileCachePrimingTest {
    @TempDir Path root;
    @Test void nativePrimingCreatesAnEmptyCacheWithoutProfileLookups()throws Exception{
        var path=root.resolve("usercache.json");var cache=new GameProfileCache((names,callback)->fail("Priming must not resolve a profile"),path.toFile());
        assertEquals(2,WorldBossLoginWarmup.primeProfileCache(cache));assertEquals(0,JsonParser.parseString(Files.readString(path)).getAsJsonArray().size());
    }
    @Test void nativePrimingRetainsExistingNameAndUuidWithoutAddingSyntheticProfiles()throws Exception{
        var path=root.resolve("usercache.json");var uuid=UUID.fromString("12345678-1234-5678-9012-123456789012");
        Files.writeString(path,"[{\"name\":\"SavedPlayer\",\"uuid\":\""+uuid+"\",\"expiresOn\":\"2099-01-01 00:00:00 +0000\"}]");
        var cache=new GameProfileCache((names,callback)->fail("Priming must not resolve a profile"),path.toFile());
        assertEquals(2,WorldBossLoginWarmup.primeProfileCache(cache));assertEquals("SavedPlayer",cache.get(uuid).orElseThrow().getName());
        var entries=JsonParser.parseString(Files.readString(path)).getAsJsonArray();assertEquals(1,entries.size());
        assertEquals(uuid.toString(),entries.get(0).getAsJsonObject().get("uuid").getAsString());
    }
    @Test void customCacheImplementationsAndAbsentCachesAreLeftToTheirOwner(){
        class Custom extends GameProfileCache {
            int saves;
            Custom(){super((names,callback)->fail("Unexpected lookup"),root.resolve("custom.json").toFile());}
            @Override public void save(){saves++;}
        }
        var cache=new Custom();assertEquals(0,WorldBossLoginWarmup.primeProfileCache(cache));assertEquals(0,cache.saves);
        assertEquals(0,WorldBossLoginWarmup.primeProfileCache(null));assertFalse(Files.exists(root.resolve("custom.json")));
    }
}
