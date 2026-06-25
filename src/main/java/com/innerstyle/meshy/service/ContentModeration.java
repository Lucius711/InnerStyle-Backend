package com.innerstyle.meshy.service;

import com.innerstyle.common.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Lightweight text content policy: rejects prompts containing blocked terms (sexual content,
 * exploitation, extreme violence) in English or Vietnamese. Mirrors the client-side blocklist
 * and is enforced server-side as defence in depth. Image moderation is handled on the client
 * (nsfwjs) before upload.
 */
@Component
public class ContentModeration {

    private static final List<String> BLOCKED_TERMS = List.of(
        // English — sexual / nudity
        "nude", "naked", "nsfw", "porn", "pornographic", "sexual", "explicit nudity",
        "xxx", "hentai", "nipple", "genital", "penis", "vagina", "blowjob", "masturbat",
        "topless", "bottomless", "erotic", "fetish",
        // English — exploitation / extreme
        "rape", "incest", "bestiality", "lolicon", "shotacon", "child porn", "underage",
        "pedophil", "gore", "beheading", "decapitat",
        // Vietnamese
        "khiêu dâm", "khoả thân", "khỏa thân", "lõa thể", "loã thể", "ảnh sex", "phim sex",
        "cởi truồng", "trần truồng", "ấu dâm", "loạn luân", "hiếp dâm", "cưỡng hiếp",
        "bộ phận sinh dục", "đồi trụy", "dâm ô", "khiêu gợi tình dục"
    );

    /** Throw {@code validation.content.blocked} if any provided text contains a blocked term. */
    public void assertClean(String... texts) {
        if (texts == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String text : texts) {
            if (text != null && !text.isBlank()) {
                sb.append(' ').append(text.toLowerCase(Locale.ROOT));
            }
        }
        String haystack = sb.toString();
        if (haystack.isBlank()) {
            return;
        }
        for (String term : BLOCKED_TERMS) {
            if (haystack.contains(term)) {
                throw new BadRequestException("validation.content.blocked");
            }
        }
    }
}
