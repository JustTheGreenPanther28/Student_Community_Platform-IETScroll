package com.ietscroll.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ietscroll.dto.UserDTO;
import com.ietscroll.request.UserRegisterRequest;
import com.ietscroll.response.Result;
import com.ietscroll.response.UserResponse;
import com.ietscroll.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/user")
@Tag(name = "User Management", description = "Handles user registration, profile retrieval, and updates. Restricted to institute email.")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@Operation(summary = "Register user", description = "Registers a new user using institute email and sends OTP for verification.")
	@PostMapping("/register")
	public ResponseEntity<UserResponse> register(@Valid @RequestBody UserRegisterRequest userDetail) {

		UserDTO userDetailDTO = new UserDTO();
		userDetailDTO.setBranch(userDetail.branch());
		userDetailDTO.setCourse(userDetail.course());
		userDetailDTO.setEmail(userDetail.email());
		userDetailDTO.setFullName(userDetail.fullName());
		userDetailDTO.setPassword(userDetail.password());
		userDetailDTO.setUsername(userDetail.username());
		userDetailDTO.setYearOfPassout(userDetail.yearOfPassout());
		UserDTO createdUserDetailDTO = userService.register(userDetailDTO);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(new UserResponse(createdUserDetailDTO.getPublicUserId(), createdUserDetailDTO.getUsername(),
						createdUserDetailDTO.getEmail(), createdUserDetailDTO.getFullName(),
						createdUserDetailDTO.getYearOfPassout(), createdUserDetailDTO.getCourse(),
						createdUserDetailDTO.getBranch()));
	}

	@Operation(summary = "Get user profile", description = "Fetch authenticated user's profile details.")
	@GetMapping
	public UserResponse getUser(Authentication authentication) {
		UserDTO userDTO = userService.getUserByEmail(authentication.getName());
		return new UserResponse(userDTO.getPublicUserId(), userDTO.getUsername(), userDTO.getEmail(),
				userDTO.getFullName(), userDTO.getYearOfPassout(), userDTO.getCourse(), userDTO.getBranch());
	}

	@Operation(summary = "Update username", description = "Update the username of the authenticated user.")
	@PatchMapping("/username/{newUsername}")
	public Result updateUsername(Authentication authentication, @Valid @PathVariable String newUsername) {
		return userService.updateUsername(authentication.getName(), newUsername);
	}

	@Operation(summary = "Update full name", description = "Update the full name of the authenticated user.")
	@PatchMapping("/fullname/{fullname}")
	public Result updateFullname(Authentication authentication, @Valid @PathVariable String fullname) {
		return userService.updateFullName(authentication.getName(), fullname);
	}

}
