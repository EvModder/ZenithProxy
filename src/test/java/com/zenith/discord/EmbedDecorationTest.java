package com.zenith.discord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmbedDecorationTest {
    @Test
    void labelsMessagesWithoutChangingTheirTitles() {
        var embed = Embed.builder().title("[AutoFish] Warning");
        DiscordBot.addAccountFooter(embed, "EvModder");
        assertEquals("Account: EvModder", embed.footer().text());
        assertEquals("[AutoFish] Warning", embed.title());
    }

    @Test
    void preservesExistingFooterAndDoesNotDuplicateLabels() {
        var embed = Embed.builder().footer("Executed by: Admin", "https://example.com/icon.png");
        DiscordBot.addAccountFooter(embed, "EvModder");
        DiscordBot.addAccountFooter(embed, "EvModder");
        assertEquals("Account: EvModder | Executed by: Admin", embed.footer().text());
        assertEquals("https://example.com/icon.png", embed.footer().iconUrl());
    }

    @Test
    void omitsLabelWhenTitleNamesTheAccount() {
        var embed = Embed.builder().title("EvMD disconnected");
        DiscordBot.addAccountFooter(embed, "EvMD");
        assertNull(embed.footer());
        var withFooter = Embed.builder().title("evmd connected").footer("Executed by: Admin", null);
        DiscordBot.addAccountFooter(withFooter, "EvMD");
        assertEquals("Executed by: Admin", withFooter.footer().text());
    }

    @Test
    void labelsWhenTitleOnlyContainsTheAccountAsPartOfAnotherName() {
        var embed = Embed.builder().title("EvModder disconnected");
        DiscordBot.addAccountFooter(embed, "EvMod");
        assertEquals("Account: EvMod", embed.footer().text());
    }

    @Test
    void missingAccountLeavesFooterUntouched() {
        var embed = Embed.builder();
        DiscordBot.addAccountFooter(embed, "");
        DiscordBot.addAccountFooter(embed, null);
        assertNull(embed.footer());
    }
}
