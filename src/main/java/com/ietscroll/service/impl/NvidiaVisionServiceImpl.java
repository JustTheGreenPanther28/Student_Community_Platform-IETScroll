package com.ietscroll.service.impl;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ietscroll.exception.ContentModerationException;
import com.ietscroll.exception.InappropriateImageException;
import com.ietscroll.service.NvidiaVisionService;

@Service
public class NvidiaVisionServiceImpl implements NvidiaVisionService {

    private static final Logger log = LoggerFactory.getLogger(NvidiaVisionServiceImpl.class);

    @Value("${spring.ai.openai.api-key:${NVIDIA_API_KEY:${NVIDIA_MISTRAL_KEY:${NVIDIA_LLAMA_KEY}}}}")
    private String apiKey;

    @Value("${nvidia.vision.model:meta/llama-3.2-11b-vision-instruct}")
    private String visionModel;

    @Value("${nvidia.vision.url:https://integrate.api.nvidia.com/v1/chat/completions}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SAFETY_PROMPT = """
            You are a strict security and campus content moderation guard for Lost and Found items.
            Check this image carefully. Is it SAFE to post publicly?
            Violations include:
            - Hacking, cyber threats, exploit code, script injection, terminal screenshots, credential dumps, passwords, API tokens
            - Scam QR codes, fake payment/crypto QR codes, phishing URLs, malicious links
            - Commercial spam, ads, promotions, gambling, illicit service banners
            - Weapons, firearms, knives, ammunition, explosives
            - Violence, blood, gore, dead bodies, self-harm, physical threats, harassment, extortion
            - Adult content, nudity, pornographic or sexually suggestive material
            - Alcohol, drugs, smoking, vaping paraphernalia
            - Hate symbols, Nazi/terrorist imagery, offensive gestures

            Lost items like notebooks, bottles, keys, ID cards, phones, bags, headphones, calculators, clothes are SAFE.

            Respond ONLY in this exact format:
            SAFE
            or
            UNSAFE: <brief reason>
            """;

    @Override
    public void checkImage(MultipartFile file) throws IOException {
        String content = callVisionModel(file, SAFETY_PROMPT, 50);
        if (content == null || content.isBlank()) {
            return;
        }

        log.info("NVIDIA Vision safety evaluation: {}", content);
        String upper = content.toUpperCase();
        if (upper.startsWith("UNSAFE") || (upper.contains("UNSAFE:") && !upper.startsWith("SAFE"))) {
            String reason = content.replaceFirst("(?i)UNSAFE:?", "").trim();
            if (reason.isBlank()) {
                reason = "Inappropriate, spam, or security threat detected in image by NVIDIA Vision Guard";
            }
            throw new InappropriateImageException(reason);
        }
    }

    @Override
    public void checkImageRelevance(MultipartFile file, String itemName, String description) throws IOException {
        String relevancePrompt = """
                You are a content authenticity and security validator for a university campus Lost & Found platform.
                Item Name: "%s"
                Item Description: "%s"

                Verify the uploaded image against the item claim:
                1. Is the image a genuine photo or depiction of a physical lost/found object (e.g. bag, bottle, keys, wallet, phone, laptop, ID card, clothing, charger, book, umbrella)?
                   - REJECT software architecture diagrams, system flowcharts, code screenshots, hacking scripts, terminal commands, random text documents, memes, or abstract graphics.
                2. Does the image reasonably match or show the claimed item "%s"?
                3. Does it contain spam, phishing, scam QR codes, cyber threats, or low-effort gibberish (e.g. "lost lost", "span lost", "asdf", "test test")?

                Respond ONLY in this exact format:
                VALID
                or
                INVALID: <brief explanation of the mismatch or issue>
                """.formatted(itemName, description, itemName);

        String content = callVisionModel(file, relevancePrompt, 80);
        if (content == null || content.isBlank()) {
            return;
        }

        log.info("NVIDIA Vision relevance evaluation: {}", content);
        String upper = content.toUpperCase();
        if (upper.startsWith("INVALID") || (upper.contains("INVALID:") && !upper.startsWith("VALID"))) {
            String reason = content.replaceFirst("(?i)INVALID:?", "").trim();
            if (reason.isBlank()) {
                reason = "The uploaded image does not match the item, is not a genuine lost/found item photo, or failed safety checks.";
            }
            throw new ContentModerationException(reason);
        }
    }

    private String callVisionModel(MultipartFile file, String prompt, int maxTokens) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        byte[] bytes = file.getBytes();
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "image/jpeg";
        }

        String base64Image = Base64.getEncoder().encodeToString(bytes);
        String dataUrl = "data:" + contentType + ";base64," + base64Image;

        Map<String, Object> textPart = Map.of("type", "text", "text", prompt);
        Map<String, Object> imagePart = Map.of("type", "image_url", "image_url", Map.of("url", dataUrl));
        Map<String, Object> userMessage = Map.of("role", "user", "content", List.of(textPart, imagePart));

        Map<String, Object> requestPayload = Map.of(
                "model", visionModel,
                "messages", List.of(userMessage),
                "temperature", 0.0,
                "max_tokens", maxTokens
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestPayload, headers);

        String responseBody;
        try {
            responseBody = restTemplate.postForObject(apiUrl, entity, String.class);
        } catch (Exception e) {
            log.warn("NVIDIA Vision API check encountered an error: {}. Proceeding to secondary checks.", e.getMessage());
            return null;
        }

        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                return choices.get(0).path("message").path("content").asText("").trim();
            }
        } catch (Exception e) {
            log.warn("Failed to parse NVIDIA Vision response: {}", e.getMessage());
        }
        return null;
    }
}
