package com.ietscroll.service.impl;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.ietscroll.dto.FoundItemDTO;
import com.ietscroll.exception.BadRequestException;
import com.ietscroll.exception.LimitExceededException;
import com.ietscroll.dto.PagedResponseDTO;
import com.ietscroll.entity.FoundItemEntity;
import com.ietscroll.general.enums.FoundItemStatus;
import com.ietscroll.repository.FoundItemRepository;
import com.ietscroll.response.FoundItemResponse;
import com.ietscroll.response.Result;
import com.ietscroll.service.CloudinaryService;
import com.ietscroll.service.FoundItemService;
import com.ietscroll.service.SightEngineService;

@Service
public class FoundItemServiceImpl implements FoundItemService {

	private final FoundItemRepository foundItemRepo;
	private final CloudinaryService cloudinaryService;
	private final SightEngineService sightEngineService;
	private static final List<String> ALLOWED_TYPES = List.of("image/jpeg", "image/png", "image/webp", "image/gif");

	public FoundItemServiceImpl(FoundItemRepository foundItemRepo, CloudinaryService cloudinaryService,
			SightEngineService sightEngineService) {
		this.foundItemRepo = foundItemRepo;
		this.cloudinaryService = cloudinaryService;
		this.sightEngineService = sightEngineService;
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

		if (image == null || image.isEmpty()) {
			throw new BadRequestException("Add Image of the lost product!");
		}

		if (!ALLOWED_TYPES.contains(image.getContentType())) {
			throw new BadRequestException("Kindly add valid image type!");
		}

		if (foundItemRepo.countByUser(email) >= 3) {
			throw new LimitExceededException(
					"Maximum found-item request of a user can have is three, kindly close other request to create a new request.");
		}

		sightEngineService.checkImage(image);

		// Map has details about uploaded image
		Map<?,?> uploadedDetail = cloudinaryService.upload(image);
		// Getting url from it
		String url = (String) uploadedDetail.get("secure_url");

		FoundItemEntity foundItem = new FoundItemEntity();
		foundItem.setImageURL(url);
		foundItem.setDescription(foundItemDTo.getDescription());
		foundItem.setFoundItemName(foundItemDTo.getFoundItemName());
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

		// Converting String -> UUID -> Byte Array (Because MYSQL store UUID in form of
		UUID publicUUID = UUID.fromString(publicId);

		int rowsChanged = foundItemRepo.closeRequest(email, publicUUID);
		if (rowsChanged == 0) {
			return Result.FAILED;
		}
		return Result.SUCCESS;
	}

}
