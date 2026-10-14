package com.zenith.discord;

import com.zenith.util.config.Config;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplayNotificationTest {
    @Test void notificationsDefaultOff() {
        assertFalse(new Config.Client.Extra.ReplayMod().notifyRecordingStartStop);
    }

    @Test void quietUploadHasFileWithoutEmbed() {
        var embed = Embed.builder().fileAttachment(new Embed.FileAttachment("test.mcpr", new byte[]{1}));
        DiscordBot.addAccountFooter(embed, "test-account");
        try (var message = DiscordBot.notificationMessage(null, embed)) {
            assertTrue(message.getEmbeds().isEmpty());
            assertEquals(1, message.getFiles().size());
        }
    }

    @Test void enabledNoticeAndErrorsRemainVisible() {
        for (var embed : new Embed[]{
            Embed.builder().title("Replay Recording Stopped"),
            Embed.builder().title("Replay Recording").description("Upload failed")}) {
            try (var message = DiscordBot.notificationMessage(null, embed)) {
                assertEquals(1, message.getEmbeds().size());
            }
        }
    }
}
