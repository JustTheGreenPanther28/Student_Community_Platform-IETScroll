package com.ietscroll.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ietscroll.request.AccountVerificationRequest;
import com.ietscroll.response.Result;
import com.ietscroll.service.OTPService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/otp")
@Tag(name = "OTP & Verification", description = "Handles email-based OTP verification for user authentication.")
public class OTPController {

	private final OTPService otpService;

	public OTPController(OTPService otpService) {
		this.otpService = otpService;
	}

	@PostMapping("/verify")
	@Operation(summary = "Verify OTP", description = "Validates the OTP provided by the user and activates the account if successful.")
	public Result verifyAccount(@RequestBody AccountVerificationRequest otpAndEmail) {
		return otpService.verifyOTP(otpAndEmail.otp(), otpAndEmail.email());
	}

}
