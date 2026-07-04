package com.ietscroll.service.impl;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.ietscroll.exception.BadRequestException;
import com.ietscroll.response.QualityOfResume;
import com.ietscroll.service.ResumeCheckerService;

@Service
public class ResumeCheckerServiceImpl implements ResumeCheckerService {

	private final ChatClient resumeChatClient;
	private static final List<String> DOCUMENT_TYPES = List.of("application/pdf", "application/msword",
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document");

	public ResumeCheckerServiceImpl(@Qualifier("llamaChatClient") ChatClient chatClient) {
		this.resumeChatClient = chatClient;
	}

	@Override
	public QualityOfResume getQuality(MultipartFile file, String role, int experience) {
		if (!DOCUMENT_TYPES.contains(file.getContentType())) {
			throw new BadRequestException("Kindly upload your resume in form of PDF/DOCS ");
		}
		return resumeChatClient
				.prompt()
				.user(extractTextFromFile(file))
				.system(sys -> sys.params(Map.of("role", role, "experience", experience))).call()
				.responseEntity(QualityOfResume.class)
				.entity();
	}

	private static String extractTextFromFile(MultipartFile file) {

		if (file == null || file.isEmpty()) {
			throw new BadRequestException("File is empty or null");
		}
		try {
			TikaDocumentReader reader = new TikaDocumentReader(new InputStreamResource(file.getInputStream()));
			List<Document> documents = reader.get();
			if (documents == null || documents.isEmpty()) {
				return "";
			}
			StringBuilder content = new StringBuilder();

			for (Document doc : documents) {
				if (doc.getText() != null) {
					content.append(doc.getText()).append("\n");
				}
			}
			return content.toString().trim();

		} catch (IOException e) {
			throw new BadRequestException("Failed to read file. Please make sure it's a valid, uncorrupted PDF or DOCX.");
		}
	}

}
