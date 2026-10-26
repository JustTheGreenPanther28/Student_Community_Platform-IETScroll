package com.ietscroll.service;

/**
 * Checks text content for spam / harmful content.
 * Throws ContentModerationException if content is unsafe.
 */
public interface SpamProtectionService {

    /**
     * Checks the given text using NVIDIA Nemotron Content Safety API.
     * Throws {@link com.ietscroll.exception.ContentModerationException}
     * if the text is flagged as unsafe.
     *
     * @param text the user-supplied text to check
     */
    void checkText(String text);

    /**
     * Consolidated single-batch check for item submissions (name, description, location).
     * Validates zero-emoji policy and combines content into a single AI evaluation to prevent
     * redundant network roundtrips and request timeouts.
     *
     * @param name item name
     * @param description item description
     * @param location item predicted location
     */
    void checkItemSubmission(String name, String description, String location);
}
