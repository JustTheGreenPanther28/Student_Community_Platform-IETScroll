package com.ietscroll.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ietscroll.dto.LostItemDTO;
import com.ietscroll.dto.PagedResponseDTO;
import com.ietscroll.request.LostItemRequest;
import com.ietscroll.response.LostItemResponse;
import com.ietscroll.response.Result;
import com.ietscroll.service.LostItemService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/lost-item")
@Tag(name = "Lost Item Management", description = "APIs for reporting lost items, tracking user requests, and browsing available lost item posts.")
public class LostItemController {

	private LostItemService lostItemService;

	public LostItemController(LostItemService lostItemService) {
		this.lostItemService = lostItemService;
	}

	@Operation(summary = "Report a lost item", description = "Creates a lost item request with image upload, moderation check, and storage. Limited to 2 active requests per user.")
	@PostMapping(consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
	public ResponseEntity<Result> createLostItemPost(Authentication authentication,
			@RequestPart("data") String lostItemJSON, @RequestPart("image") MultipartFile image) throws IOException {

		ObjectMapper mapper = new ObjectMapper();
		LostItemRequest lostItemRequest = mapper.readValue(lostItemJSON, LostItemRequest.class);

		LostItemDTO lostItemDTO = new LostItemDTO();

		lostItemDTO.setDescription(lostItemRequest.description());
		lostItemDTO.setLostItemname(lostItemRequest.lostItemname());
		lostItemDTO.setPredictedLocation(lostItemRequest.predictedLocation());
		lostItemDTO.setOwnerEmail(authentication.getName());
		lostItemDTO.setPrize(lostItemRequest.prize());

		Result result = lostItemService.uploadLostItem(authentication.getName(), lostItemDTO, image);

		ResponseEntity<Result> finalResult = new ResponseEntity<>(result, HttpStatusCode.valueOf(201));
		return finalResult;
	}

	@GetMapping("/me")
	@Operation(summary = "Get current user's lost items", description = "Retrieve all active lost item requests created by the authenticated user.")
	public List<LostItemResponse> getUserLostItem(Authentication authentication) {
		List<LostItemDTO> lostItemDTOs = lostItemService.getMyLostItems(authentication.getName());

		List<LostItemResponse> lostItemResponses = new ArrayList<>();
		for (LostItemDTO lostItemDTO : lostItemDTOs) {
			LostItemResponse lostItemResponse = new LostItemResponse();
			lostItemResponse.setPublicIdOfLostRequest(lostItemDTO.getPublicIdOfLostRequest());
			lostItemResponse.setLostItemname(lostItemDTO.getLostItemname());
			lostItemResponse.setDescription(lostItemDTO.getDescription());
			lostItemResponse.setImageURLOfItem(lostItemDTO.getImageURL());
			lostItemResponse.setPredictedLocation(lostItemDTO.getPredictedLocation());
			lostItemResponse.setPrize(lostItemDTO.getPrize());

			lostItemResponses.add(lostItemResponse);
		}
		return lostItemResponses;
	}

	@PatchMapping("/close")
	@Operation(summary = "Close a lost item request", description = "Closes a lost item request using its public identifier.")
	public Result closeLostItemRequest(Authentication authentication, @RequestParam String lostItemId) {
		return lostItemService.closeLostItem(authentication.getName(), lostItemId);
	}

	@GetMapping
	@Operation(summary = "Get all lost items (paginated)", description = "Fetch all open lost items with pagination support.")
	public ResponseEntity<PagedResponseDTO<LostItemResponse>> getAllLostItems(
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
		return ResponseEntity.ok(lostItemService.getAllLostItems(page, size));
	}
}
