package dev.rivet;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.pointer.Pointers;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ChatIgnoreTest {
    @Test
    public void loadsIgnoreListsAndSkipsMalformedEntries() {
        UUID owner = UUID.randomUUID();
        UUID ignored = UUID.randomUUID();
        YamlConfiguration data = new YamlConfiguration();
        data.set("ignored." + owner, List.of(ignored.toString(), "not-a-uuid"));
        data.set("ignored.not-a-uuid", List.of(ignored.toString()));

        Map<UUID, Set<UUID>> lists = ChatModule.ignoreLists(data);

        assertEquals(Map.of(owner, Set.of(ignored)), lists);
        assertTrue(ChatModule.ignores(lists, owner, ignored));
        assertFalse(ChatModule.ignores(lists, ignored, owner));
        assertTrue(ChatModule.ignoreLists(new YamlConfiguration()).isEmpty());
    }

    @Test
    public void removesOnlyViewersWhoIgnoreTheSender() {
        UUID sender = UUID.randomUUID();
        UUID ignoring = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Audience ignoringViewer = player(ignoring);
        Audience otherViewer = player(other);
        Audience console = new Audience() {
        };
        Audience senderViewer = player(sender);
        Set<Audience> viewers = new HashSet<>(Set.of(ignoringViewer, otherViewer, console, senderViewer));

        ChatModule.removeIgnoringViewers(viewers, Map.of(ignoring, Set.of(sender), other, Set.of(UUID.randomUUID())),
            sender);

        assertEquals(Set.of(otherViewer, console, senderViewer), viewers);
    }

    private static Audience player(UUID uuid) {
        Pointers pointers = Pointers.builder().withStatic(Identity.UUID, uuid).build();
        return new Audience() {
            @Override
            public @NotNull Pointers pointers() {
                return pointers;
            }
        };
    }
}
