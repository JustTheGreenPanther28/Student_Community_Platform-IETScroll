package com.ietscroll.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LostItemRequest(
		@NotNull @NotBlank String lostItemname,
		String predictedLocation,
		@NotNull @NotBlank String description,
		Integer prize) {

}
