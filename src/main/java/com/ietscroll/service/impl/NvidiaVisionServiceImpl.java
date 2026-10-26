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
            You are a strict campus security and Anti-Ragging officer for a university Lost & Found platform.
            Check this image carefully. Is it SAFE to post publicly?

            STRICT ANTI-RAGGING, PRIVACY & SAFETY RULES:
            1. NO HUMAN FACES OR PERSON PHOTOS: The image MUST NOT show any human faces, real persons, student selfies, group pictures, or portraits. To eliminate campus ragging, harassment, and privacy violations, only inanimate physical objects are permitted.
            2. NO CARICATURES / ANIME / TARGETING: The image MUST NOT contain anime characters, cartoon caricatures, memes, drawings, or graphics pointing towards, mocking, or ridiculing any person.
            3. NO HACKING / THREATS: No exploit scripts, terminal screenshots, passwords, API keys, or cyber threats.
            4. NO SCAM QR CODES: No fake payment QR codes, crypto addresses, or phishing links.
            5. NO WEAPONS, VIOLENCE, GORE, BLOOD, SELF-HARM, DRUGS, ALCOHOL, OR ADULT/SEXUAL CONTENT.

            Lost items like notebooks, water bottles, keys, ID cards, phones, bags, headphones, calculators, clothes are SAFE.

            Respond ONLY in this exact format:
            SAFE
            or
            UNSAFE: <brief reason>
            """;

    @Override
    public void checkImage(MultipartFile file) throws IOException {
        String content = callVisionModel(file, SAFETY_PROMPT, 60);
        if (content == null || content.isBlank()) {
            return;
        }

        log.info("NVIDIA Vision safety evaluation: {}", content);
        String upper = content.toUpperCase();
        if (upper.startsWith("UNSAFE") || (upper.contains("UNSAFE:") && !upper.startsWith("SAFE"))) {
            String reason = content.replaceFirst("(?i)UNSAFE:?", "").trim();
            if (reason.isBlank()) {
                reason = "Inappropriate content, human face, or targeting material detected by Anti-Ragging Vision Guard";
            }
            throw new InappropriateImageException(reason);
        }
    }

    @Override
    public void checkImageRelevance(MultipartFile file, String itemName, String description) throws IOException {
        String relevancePrompt = """
                You are a content authenticity and Anti-Ragging validator for a university campus Lost & Found platform.
                Item Name: "%s"
                Item Description: "%s"

                Verify the uploaded image against the item claim:
                1. NO HUMAN FACES OR PERSONS: Reject any image depicting human faces, people, student portraits, or selfies (STRICT ANTI-RAGGING RULE: lost & found items must be physical inanimate objects only).
                2. NO TARGETING OR CARICATURES: Reject anime characters, cartoon caricatures, memes, or graphics pointing towards, mocking, or ridiculing individuals.
                3. REJECT software architecture diagrams, system flowcharts, code screenshots, hacking scripts, terminal commands, random text documents, or abstract graphics.
                4. Does the image reasonably match or show the claimed item "%s"?
                5. Does the text contain low-effort spam/gibberish (e.g. "lost lost", "span lost", "asdf")?

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
                reason = "The uploaded image does not match the item, contains a human face/caricature, or failed campus anti-ragging checks.";
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
