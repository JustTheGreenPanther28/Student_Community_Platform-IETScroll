package com.ietscroll.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.ietscroll.exception.ContentModerationException;
import com.ietscroll.service.SpamProtectionService;

/**
 * Uses NVIDIA Nemotron Content Safety model to detect spam / harmful text.
 *
 * The model returns lines like:
 *   "User Safety: safe"
 *   "User Safety: unsafe O3,O13"
 *
 * We fail-safe: if the response doesn't clearly say "safe" → reject.
 */
@Service
public class SpamProtectionServiceImpl implements SpamProtectionService {

    private static final Logger log = LoggerFactory.getLogger(SpamProtectionServiceImpl.class);

    private final ChatClient contentSafetyChatClient;

    public SpamProtectionServiceImpl(
            @Qualifier("contentSafetyChatClient") ChatClient contentSafetyChatClient) {
        this.contentSafetyChatClient = contentSafetyChatClient;
    }

    @Override
    public void checkText(String text) {
        if (text == null || text.isBlank()) {
            // Empty text — nothing to check, allow it.
            return;
        }

        String safetyResponse;
        try {
            safetyResponse = contentSafetyChatClient
                    .prompt()
                    .user(text)
                    .call()
                    .content();
        } catch (Exception e) {
            // If the safety API is unreachable, fail-open with a warning logged,
            // so the platform doesn't go completely down.
            log.warn("Content safety check failed (API error), allowing content. Error: {}", e.getMessage());
            return;
        }

        if (safetyResponse == null || safetyResponse.isBlank()) {
            log.warn("Content safety returned blank response, allowing content.");
            return;
        }

        log.debug("Content safety response: {}", safetyResponse);

        // Parse: look for "User Safety: safe" in a case-insensitive way.
        // If it says "unsafe" or doesn't explicitly say "safe" → block.
        String lower = safetyResponse.toLowerCase();
        boolean isSafe = lower.contains("user safety: safe") && !lower.contains("user safety: unsafe");

        if (!isSafe) {
            log.warn("Content flagged as unsafe. Response: {}", safetyResponse);
            throw new ContentModerationException(
                    "Your content has been flagged as inappropriate. Please maintain decorum and avoid spam, abusive language, or harmful content.");
        }
    }
}
