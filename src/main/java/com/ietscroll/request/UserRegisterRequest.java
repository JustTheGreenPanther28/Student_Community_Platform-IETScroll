package com.ietscroll.request;

import com.ietscroll.general.enums.Branch;
import com.ietscroll.general.enums.Course;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserRegisterRequest(
		@NotBlank String username,
		String fullName,
		@Email @NotNull @NotBlank String email,
		@NotNull @Min(2000) @Max(2100) Integer yearOfPassout,
		@NotNull Course course,
		@NotNull Branch branch,
		@NotNull @NotBlank String password) {

}
