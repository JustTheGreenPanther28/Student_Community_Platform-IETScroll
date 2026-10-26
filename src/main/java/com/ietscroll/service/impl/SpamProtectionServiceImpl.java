package com.ietscroll.service.impl;

import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.ietscroll.exception.ContentModerationException;
import com.ietscroll.service.SpamProtectionService;

/**
 * Strict content moderation service enforcing:
 * 1. Zero emojis policy (clean professional text only).
 * 2. Strict anti-ragging & campus equality policy:
 *    - No "senior-junior" dynamic / forced introductions / supremacy
 *    - No branch superiority / branch wars (CS vs Mech/Civil/etc.)
 *    - No batch hierarchy (e.g. senior batch dominance over junior batches)
 *    - No harassment, bullying, or targeting of students
 * 3. General spam & harm protection via NVIDIA Nemotron Safety.
 */
@Service
public class SpamProtectionServiceImpl implements SpamProtectionService {

    private static final Logger log = LoggerFactory.getLogger(SpamProtectionServiceImpl.class);

    // Regex matching all common emoji ranges across Unicode blocks
    private static final Pattern EMOJI_PATTERN = Pattern.compile(
            "[\\p{So}\\p{Cn}\\x{1F300}-\\x{1F9FF}\\x{2600}-\\x{26FF}\\x{2700}-\\x{27BF}\\x{1F600}-\\x{1F64F}\\x{1F680}-\\x{1F6FF}\\x{1F1E0}-\\x{1F1FF}]"
    );

    private final ChatClient contentSafetyChatClient;
    private final ChatClient mistralChatClient;

    private static final String ANTI_RAGGING_PROMPT = """
            You are the Chief Anti-Ragging and Campus Harmony Officer at IET DAVV university.
            Evaluate this student-submitted text strictly against university regulations:

            STRICT CAMPUS RULES:
            1. NO SENIOR-JUNIOR DYNAMIC: Demanding respect, commanding juniors/freshers, asking for "intro" sessions, imposing authority, or any ragging undertones.
            2. NO BRANCH SUPERIORITY: Claiming any branch (e.g. CS/IT) is superior to other branches (e.g. Mechanical, Civil, ETC, EI), branch wars, or branch-based insults.
            3. NO BATCH HIERARCHY: Asserting year/batch dominance or subordination (e.g. 2nd years over 1st years).
            4. NO HARASSMENT: Mocking, bullying, threatening, doxxing, calling out individual students, or personal attacks.

            Reply ONLY in this format:
            STATUS: [ALLOWED or VIOLATION]
            REASON: [Brief explanation if violation, else NONE]
            """;

    public SpamProtectionServiceImpl(
            @Qualifier("contentSafetyChatClient") ChatClient contentSafetyChatClient,
            @Qualifier("mistralChatClient") ChatClient mistralChatClient) {
        this.contentSafetyChatClient = contentSafetyChatClient;
        this.mistralChatClient = mistralChatClient;
    }

    @Override
    public void checkText(String text) {
        if (text == null || text.isBlank()) {
            return;
        }

        // ── 1. RULE: Avoid Emojis in text ───────────────────────────────────
        if (EMOJI_PATTERN.matcher(text).find()) {
            throw new ContentModerationException(
                    "Emojis are not permitted in submissions. Please write clear, professional descriptions without emojis.");
        }

        // ── 2. RULE: Anti-Ragging & Campus Hierarchy Guard ─────────────────
        checkAntiRaggingAndHierarchy(text);

        // ── 3. RULE: NVIDIA Nemotron Content Safety ────────────────────────
        checkNemotronSafety(text);
    }

    @Override
    public void checkItemSubmission(String name, String description, String location) {
        // Fast emoji checks across all fields
        if (name != null && EMOJI_PATTERN.matcher(name).find()) {
            throw new ContentModerationException("Emojis are not permitted in item names. Please write without emojis.");
        }
        if (description != null && EMOJI_PATTERN.matcher(description).find()) {
            throw new ContentModerationException("Emojis are not permitted in item descriptions. Please write without emojis.");
        }
        if (location != null && EMOJI_PATTERN.matcher(location).find()) {
            throw new ContentModerationException("Emojis are not permitted in locations. Please write without emojis.");
        }

        // Consolidate into a single context-aware text check to avoid redundant API roundtrips
        StringBuilder sb = new StringBuilder();
        if (name != null && !name.isBlank()) sb.append("Item Name: ").append(name.trim()).append("\n");
        if (description != null && !description.isBlank()) sb.append("Description: ").append(description.trim()).append("\n");
        if (location != null && !location.isBlank()) sb.append("Predicted Location: ").append(location.trim());

        String combined = sb.toString().trim();
        if (combined.isEmpty()) {
            return;
        }

        checkAntiRaggingAndHierarchy(combined);
        checkNemotronSafety(combined);
    }

    private void checkAntiRaggingAndHierarchy(String text) {
        try {
            String evaluation = mistralChatClient
                    .prompt()
                    .system(ANTI_RAGGING_PROMPT)
                    .user("Evaluate this text:\n\"" + text + "\"")
                    .call()
                    .content();

            if (evaluation != null && !evaluation.isBlank()) {
                String upper = evaluation.toUpperCase();
                if (upper.contains("STATUS: VIOLATION") || (upper.startsWith("VIOLATION") && !upper.contains("STATUS: ALLOWED"))) {
                    String reason = evaluation.replaceFirst("(?i).*REASON:\\s*", "").trim();
                    if (reason.isBlank() || reason.equalsIgnoreCase("NONE")) {
                        reason = "Violates campus anti-ragging or equality guidelines (no senior-junior hierarchy, branch superiority, or harassment).";
                    }
                    log.warn("Anti-ragging violation detected: {}", evaluation);
                    throw new ContentModerationException("Policy violation: " + reason);
                }
            }
        } catch (ContentModerationException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Anti-ragging LLM check encountered an error: {}. Proceeding to safety guard.", e.getMessage());
        }
    }

    private void checkNemotronSafety(String text) {
        String safetyResponse;
        try {
            safetyResponse = contentSafetyChatClient
                    .prompt()
                    .user(text)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("Content safety check failed (API error), allowing content. Error: {}", e.getMessage());
            return;
        }

        if (safetyResponse == null || safetyResponse.isBlank()) {
            return;
        }

        log.debug("Content safety response: {}", safetyResponse);

        String lower = safetyResponse.toLowerCase();
        boolean isSafe = lower.contains("user safety: safe") && !lower.contains("user safety: unsafe");

        if (!isSafe) {
            log.warn("Content flagged as unsafe. Response: {}", safetyResponse);
            throw new ContentModerationException(
                    "Your content has been flagged as inappropriate. Please maintain decorum and avoid abusive language, threats, or harmful content.");
        }
    }
}
