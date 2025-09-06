package com.zenith.discord;

import com.zenith.util.Color;
import lombok.Data;
import lombok.experimental.Accessors;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.DISCORD_LOG;

/**
 * Mutable data class for discord embeds
 * Basically shadows EmbedCreateSpec while adding mutability
 */
@Data
@Accessors(chain = true, fluent = true)
public class Embed {
    @Nullable String title;
    @Nullable String description;
    @Nullable String url;
    @Nullable Instant timestamp;
    @Nullable Color color;
    @Nullable String image;
    @Nullable String thumbnail;
    @Nullable Footer footer;
    @Nullable Author author;
    @NonNull List<Field> fields = new ArrayList<>();
    @Nullable FileAttachment fileAttachment;

    public record Footer(
        String text,
        String iconUrl
    ) { }

    public record Author(
        String name,
        String url,
        String iconUrl
    ) { }

    public record Field(
        String name,
        String value,
        boolean inline
    ) { }

    public record FileAttachment(
        String name,
        byte[] data
    ) { }

    public boolean isTitlePresent() {
        return title != null;
    }

    public boolean isColorPresent() {
        return color != null;
    }

    public boolean isDescriptionPresent() {
        return description != null;
    }

    public boolean isUrlPresent() {
        return url != null;
    }

    public Embed addField(String name, String value, boolean inline) {
        fields.add(new Field(name, value, inline));
        return this;
    }

    public Embed addField(String name, Object value, boolean inline) {
        fields.add(new Field(name, String.valueOf(value), inline));
        return this;
    }

    // inline defaulted to false
    public Embed addField(String name, String value) {
        return addField(name, value, false);
    }

    // inline defaulted to false
    public Embed addField(String name, Object value) {
        return addField(name, value, false);
    }

    public Embed footer(String text, String iconUrl) {
        footer = new Footer(text, iconUrl);
        return this;
    }

    public Embed primaryColor() {
        color = CONFIG.theme.primary.color();
        return this;
    }

    public Embed errorColor() {
        color = CONFIG.theme.error.color();
        return this;
    }

    public Embed successColor() {
        color = CONFIG.theme.success.color();
        return this;
    }

    public Embed inQueueColor() {
        color = CONFIG.theme.inQueue.color();
        return this;
    }

    public Embed color(net.dv8tion.jda.api.utils.Color color) {
        this.color = Color.fromInt(color.getRGB());
        return this;
    }

    public Embed color(Color color) {
        this.color = color;
        return this;
    }

    public MessageEmbed toJDAEmbed() {
        var builder = new EmbedBuilder();
        if (!truncateEmbed(this)) {
            return builder.build();
        }
        builder
            .setTitle(title)
            .setDescription(description)
            .setUrl(url)
            .setTimestamp(timestamp)
            .setColor(color != null ? color.getRGB() : 0)
            .setImage(image)
            .setThumbnail(thumbnail)
            .setFooter(footer != null ? footer.text() : null, footer != null ? footer.iconUrl() : null)
            .setAuthor(author != null ? author.name() : null, author != null ? author.url() : null, author != null ? author.iconUrl() : null);
        for (var field : fields) {
            builder.addField(field.name(), field.value(), field.inline());
        }
        return builder.build();

    }

    public static Embed builder() {
        return new Embed();
    }

    public static boolean validateEmbed(Embed embed) {
        int charCount = 0;
        if (embed.isTitlePresent()) {
            charCount += embed.title().length();
            if (embed.title().length() > 256) {
                DISCORD_LOG.error("Embed title exceeds 256 characters: {}", embed.title());
                return false;
            }
        }
        if (embed.isDescriptionPresent()) {
            charCount += embed.description().length();
            if (embed.description().length() > 4096) {
                DISCORD_LOG.error("Embed description exceeds 4096 characters: {}", embed.description());
                return false;
            }
        }
        if (embed.fields().size() > 25) {
            DISCORD_LOG.error("Embed contains more than 25 fields");
            return false;
        }
        for (int i = 0; i < embed.fields().size(); i++) {
            var field = embed.fields().get(i);
            if (field.name().length() > 256) {
                DISCORD_LOG.error("Embed field name exceeds 256 characters: {}", field.name());
                return false;
            }
            if (field.value().length() > 1024) {
                DISCORD_LOG.error("Embed field value exceeds 1024 characters: {}", field.value());
                return false;
            }
            charCount += field.name().length() + field.value().length();
        }
        if (embed.footer() != null) {
            if (embed.footer().text().length() > 2048) {
                DISCORD_LOG.error("Embed footer text exceeds 2048 characters: {}", embed.footer().text());
                return false;
            }
            charCount += embed.footer().text().length();
        }
        if (embed.author() != null) {
            if (embed.author().name().length() > 256) {
                DISCORD_LOG.error("Embed author name exceeds 256 characters: {}", embed.author().name());
                return false;
            }
            charCount += embed.author().name().length();
        }
        if (charCount > 6000) {
            DISCORD_LOG.error("Embed character count exceeds 6000 characters");
            return false;
        }
        return true;
    }

    public static boolean truncateEmbed(Embed embed) {
        int charCount = 0;
        if (embed.isTitlePresent()) {
            charCount += embed.title().length();
            if (embed.title().length() > 256) {
                embed.title(embed.title().substring(0, 256));
            }
        }
        if (embed.isDescriptionPresent()) {
            charCount += embed.description().length();
            if (embed.description().length() > 4096) {
                embed.description(embed.description().substring(0, 4096));
            }
        }
        if (embed.fields().size() > 25) {
            embed.fields(new ArrayList<>(embed.fields().subList(0, 25)));
        }
        for (int i = 0; i < embed.fields().size(); i++) {
            var field = embed.fields().get(i);
            var fieldName = field.name();
            var fieldValue = field.value();
            if (fieldName.length() > 256) {
                fieldName = fieldName.substring(0, 256);
            }
            if (field.value().length() > 1024) {
                fieldValue = field.value().substring(0, 1024);
            }
            if (!fieldName.equals(field.name()) || !fieldValue.equals(field.value())) {
                embed.fields().remove(i);
                embed.fields().add(i, new Field(fieldName, fieldValue, field.inline()));
            }
            charCount += fieldName.length() + fieldValue.length();
        }
        if (embed.footer() != null) {
            if (embed.footer().text().length() > 2048) {
                var newFooter = new Footer(embed.footer().text().substring(0, 2048), embed.footer().iconUrl());
                embed.footer(newFooter);
            }
            charCount += embed.footer().text().length();
        }
        if (embed.author() != null) {
            if (embed.author().name().length() > 256) {
                embed.author(new Author(embed.author().name().substring(0, 256), embed.author().url(), embed.author().iconUrl()));
            }
            charCount += embed.author().name().length();
        }
        if (charCount > 6000) {
            DISCORD_LOG.error("Embed character count exceeds 6000 characters");
            return false;
        }
        return true;
    }
}
