package com.ietscroll.service.impl;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ietscroll.dto.FoundItemDTO;
import com.ietscroll.dto.PagedResponseDTO;
import com.ietscroll.entity.FoundItemEntity;
import com.ietscroll.exception.BadRequestException;
import com.ietscroll.exception.LimitExceededException;
import com.ietscroll.general.enums.FoundItemStatus;
import com.ietscroll.repository.FoundItemRepository;
import com.ietscroll.response.FoundItemResponse;
import com.ietscroll.response.Result;
import com.ietscroll.service.CloudinaryService;
import com.ietscroll.service.FoundItemService;
import com.ietscroll.service.NvidiaVisionService;
import com.ietscroll.service.SightEngineService;
import com.ietscroll.service.SpamProtectionService;

@Service
public class FoundItemServiceImpl implements FoundItemService {

	private final FoundItemRepository foundItemRepo;
	private final CloudinaryService cloudinaryService;
	private final SightEngineService sightEngineService;
	private final SpamProtectionService spamProtectionService;
	private final NvidiaVisionService nvidiaVisionService;
	private static final List<String> ALLOWED_TYPES = List.of("image/jpeg", "image/png", "image/webp", "image/gif");

	public FoundItemServiceImpl(FoundItemRepository foundItemRepo, CloudinaryService cloudinaryService,
			SightEngineService sightEngineService, SpamProtectionService spamProtectionService,
			NvidiaVisionService nvidiaVisionService) {
		this.foundItemRepo = foundItemRepo;
		this.cloudinaryService = cloudinaryService;
		this.sightEngineService = sightEngineService;
		this.spamProtectionService = spamProtectionService;
		this.nvidiaVisionService = nvidiaVisionService;
	}

	@Override
	public List<FoundItemDTO> getMyFoundItems(String email) {
		List<FoundItemEntity> foundItems = foundItemRepo.findActiveRequestByEmail(email);
		List<FoundItemDTO> foundItemDTOs = new ArrayList<>();

		for (FoundItemEntity foundItem : foundItems) {
			FoundItemDTO lostItemDTO = new FoundItemDTO();
			lostItemDTO.setDescription(foundItem.getDescription());
			lostItemDTO.setImageURL(foundItem.getImageURL());
			lostItemDTO.setPredictedLocation(foundItem.getPredictedLocation());
			lostItemDTO.setFoundItemName(foundItem.getFoundItemName());
			lostItemDTO.setPublicIdOfFoundItem(foundItem.getPublicIdOfFoundItem());
			lostItemDTO.setCreatedAt(foundItem.getCreatedAt());
			lostItemDTO.setContactTo(email);

			foundItemDTOs.add(lostItemDTO);
		}

		return foundItemDTOs;
	}

	@Override
	@Transactional
	public Result uploadFoundItem(String email, FoundItemDTO foundItemDTo, MultipartFile image) throws IOException {
		if (foundItemDTo == null) {
			throw new BadRequestException("Please give valid input");
		}

		String itemName = foundItemDTo.getFoundItemName() != null ? foundItemDTo.getFoundItemName().trim() : "";
		String description = foundItemDTo.getDescription() != null ? foundItemDTo.getDescription().trim() : "";

		if (itemName.length() < 3) {
			throw new BadRequestException("Please provide a valid item name (at least 3 characters).");
		}

		if (description.length() < 20) {
			throw new BadRequestException("Please provide a detailed description (at least 20 characters) describing the item's appearance, color, brand, or condition.");
		}

		if (image == null || image.isEmpty()) {
			throw new BadRequestException("Add Image of the found product!");
		}

		if (!ALLOWED_TYPES.contains(image.getContentType())) {
			throw new BadRequestException("Kindly add valid image type!");
		}

		if (foundItemRepo.countByUser(email) >= 3) {
			throw new LimitExceededException(
					"Maximum found-item request of a user can have is three, kindly close other request to create a new request.");
		}

		// ── STEP 1 & 2: Run Text and Vision verification concurrently for maximum speed ──
		CompletableFuture<Void> textFuture = CompletableFuture.runAsync(() ->
				spamProtectionService.checkItemSubmission(itemName, description, foundItemDTo.getPredictedLocation())
		);

		CompletableFuture<Void> visionFuture = CompletableFuture.runAsync(() -> {
			try {
				nvidiaVisionService.checkImageRelevance(image, itemName, description);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});

		try {
			CompletableFuture.allOf(textFuture, visionFuture).join();
		} catch (CompletionException ce) {
			Throwable cause = ce.getCause();
			if (cause instanceof RuntimeException re) throw re;
			if (cause instanceof IOException io) throw io;
			throw new RuntimeException(cause);
		}

		// ── STEP 3: Image safety & SightEngine double-verification ────────────
		sightEngineService.checkImage(image);

		// Map has details about uploaded image
		Map<?, ?> uploadedDetail = cloudinaryService.upload(image);
		// Getting url from it
		String url = (String) uploadedDetail.get("secure_url");

		FoundItemEntity foundItem = new FoundItemEntity();
		foundItem.setImageURL(url);
		foundItem.setDescription(description);
		foundItem.setFoundItemName(itemName);
		foundItem.setContactTo(email);
		foundItem.setPredictedLocation(foundItemDTo.getPredictedLocation());
		foundItem.setCreatedAt(LocalDateTime.now());

		foundItemRepo.save(foundItem);

		return Result.SUCCESS;
	}

	@Override
	public PagedResponseDTO<FoundItemResponse> getAllFoundItems(int page, int size) {

		Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
		Page<FoundItemResponse> mapped = foundItemRepo.findByStatus(FoundItemStatus.PENDING, pageable)
				.map(foundItem -> {
					FoundItemResponse response = new FoundItemResponse();
					response.setDescription(foundItem.getDescription());
					response.setImageURL(foundItem.getImageURL());
					response.setPredictedLocation(foundItem.getPredictedLocation());
					response.setFoundItemName(foundItem.getFoundItemName());
					response.setPublicIdOfFoundItem(foundItem.getPublicIdOfFoundItem());
					response.setCreatedAt(foundItem.getCreatedAt());
					response.setContactTo(foundItem.getContactTo());
					return response;
				});
		return PagedResponseDTO.from(mapped);
	}

	@Override
	@Transactional
	public Result closeFoundItemRequest(String email, String publicId) {
		if (email == null || publicId == null) {
			throw new BadRequestException("Invalid credentials");
		}

		// Converting String -> UUID
		UUID publicUUID = UUID.fromString(publicId);

		int rowsChanged = foundItemRepo.closeRequest(email, publicUUID);
		if (rowsChanged == 0) {
			return Result.FAILED;
		}
		return Result.SUCCESS;
	}
}
