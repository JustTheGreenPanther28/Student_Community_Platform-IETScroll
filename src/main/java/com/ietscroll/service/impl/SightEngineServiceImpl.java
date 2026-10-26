package com.ietscroll.service.impl;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.ietscroll.dto.SightEngineResponse;
import com.ietscroll.exception.InappropriateImageException;
import com.ietscroll.service.SightEngineService;
import com.ietscroll.util.CustomByteArrayResource;
import com.ietscroll.util.ModerationEvaluator;

@Service
public class SightEngineServiceImpl implements SightEngineService {

	private static final Logger log = LoggerFactory.getLogger(SightEngineServiceImpl.class);

	@Value("${sightengine.api-user}")
	private String apiUser;

	@Value("${sightengine.api-secret}")
	private String apiSecret;

	@Value("${sightengine.api-url}")
	private String apiUrl;

	private final RestTemplate restTemplate;
	private final ModerationEvaluator evaluator;

	public SightEngineServiceImpl(ModerationEvaluator evaluator) {
		this.evaluator = evaluator;
		org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(5000);
		factory.setReadTimeout(10000);
		this.restTemplate = new RestTemplate(factory);
	}

	@Override
	public void checkImage(MultipartFile file) throws IOException {
		// SightEngine double-verification (if available)
		try {
			LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
			CustomByteArrayResource resource = new CustomByteArrayResource(file.getBytes(), file.getName());
			body.add("media", resource);
			body.add("models", "nudity-2.1,weapon,alcohol,recreational_drug,gore-2.0,violence,self-harm,offensive-2.0");
			body.add("api_user", apiUser);
			body.add("api_secret", apiSecret);

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.MULTIPART_FORM_DATA);

			SightEngineResponse response = restTemplate
					.postForEntity(apiUrl, new HttpEntity<>(body, headers), SightEngineResponse.class).getBody();

			if (response != null) {
				evaluator.evaluate(response);
			}
		} catch (InappropriateImageException e) {
			// SightEngine detected inappropriate content -> propagate rejection
			throw e;
		} catch (Exception e) {
			// SightEngine is free tier and can fail / run out of quota.
			log.warn("SightEngine double-verification unavailable or quota exceeded ({}). NVIDIA Vision approval accepted.", e.getMessage());
		}
	}
}
