package com.ietscroll.configuration;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;

@Configuration
public class AIConfiguration {

	private final List<String> badWords = List.of("badwords");

	// ── Primary chat client: used for team-purpose content check ─────────────
	@Bean
	@Primary
	ChatClient mistralChatClient(@Qualifier("openAiChatModel") ChatModel chatModel, ChatMemory chatMemory,
			@Value("classpath:/prompts/ContentDefaultSystemPrompt.st") Resource systemPromptResource) {

		return ChatClient.builder(chatModel)
				.defaultAdvisors(new SafeGuardAdvisor(badWords))
				.defaultSystem(systemPromptResource).build();
	}

	// ── Resume-rating model: nvidia/nemotron-3.5-bolt-e4b ───────────────────
	@Bean
	ChatModel llamaNemotronChatModel(
	        @Value("${spring.ai.openai.api-llama-key}") String apiKey) {

	    OpenAiApi llamaApi = OpenAiApi
	            .builder()
	            .apiKey(apiKey)
	            .baseUrl("https://integrate.api.nvidia.com")
	            .build();

	    return OpenAiChatModel.builder()
	            .openAiApi(llamaApi)
	            .defaultOptions(OpenAiChatOptions.builder()
	                    .model("nvidia/nemotron-3.5-bolt-e4b")
	                    .build())
	            .build();
	}

	@Bean
	ChatClient llamaChatClient(
	        @Qualifier("llamaNemotronChatModel") ChatModel chatModel,
	        ChatMemory chatMemory,
	        @Value("classpath:/prompts/ResumeDefaultSystemPrompt.st") Resource resumeRatingPrompt) {

	    return ChatClient.builder(chatModel)
	            .defaultAdvisors(new SafeGuardAdvisor(badWords))
	            .defaultSystem(resumeRatingPrompt)
	            .build();
	}

	// ── Strict content-safety model: nvidia/nemotron-3.5-content-safety ─────
	// Returns "User Safety: safe" or "User Safety: unsafe ..." lines.
	@Bean
	ChatModel contentSafetyChatModel(
	        @Value("${spring.ai.openai.api-safety-key}") String apiKey) {

	    OpenAiApi safetyApi = OpenAiApi
	            .builder()
	            .apiKey(apiKey)
	            .baseUrl("https://integrate.api.nvidia.com")
	            .build();

	    return OpenAiChatModel.builder()
	            .openAiApi(safetyApi)
	            .defaultOptions(OpenAiChatOptions.builder()
	                    .model("nvidia/nemotron-3.5-content-safety")
	                    .build())
	            .build();
	}

	@Bean
	ChatClient contentSafetyChatClient(
	        @Qualifier("contentSafetyChatModel") ChatModel chatModel) {

	    return ChatClient.builder(chatModel).build();
	}

	@Bean
	ChatMemory chatMemory() {
		return MessageWindowChatMemory.builder().build(); // keeps last 20 messages by default
	}
}
