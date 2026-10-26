package com.ietscroll.service;

import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

public interface NvidiaVisionService {
    /**
     * Inspects an uploaded image using NVIDIA Vision Language Models (e.g. LLaMA 3.2 11B Vision)
     * for threats, weapons, violence, adult/sexual content, drugs, hate symbols, and spam.
     *
     * @param file the uploaded image
     * @throws com.ietscroll.exception.InappropriateImageException if content policy is violated
     * @throws IOException if the file bytes cannot be read
     */
    void checkImage(MultipartFile file) throws IOException;

    /**
     * Verifies that the uploaded image is a genuine lost/found item photo and reasonably
     * corresponds to the claimed item name and description (rejects random diagrams, memes,
     * code screenshots, unrelated graphics, or gibberish claims).
     *
     * @param file the uploaded image
     * @param itemName the title/name of the item
     * @param description the detailed description of the item
     * @throws com.ietscroll.exception.ContentModerationException if the image does not match or is inappropriate
     * @throws IOException if the file bytes cannot be read
     */
    void checkImageRelevance(MultipartFile file, String itemName, String description) throws IOException;
}
