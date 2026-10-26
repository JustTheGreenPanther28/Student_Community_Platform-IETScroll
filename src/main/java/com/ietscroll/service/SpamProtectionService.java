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
}
