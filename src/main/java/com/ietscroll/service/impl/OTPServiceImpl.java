package com.ietscroll.service.impl;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ietscroll.entity.OTPEntity;
import com.ietscroll.entity.UserEntity;
import com.ietscroll.exception.BadRequestException;
import com.ietscroll.exception.LimitExceededException;
import com.ietscroll.exception.ResourceNotFoundException;
import com.ietscroll.repository.OTPRepository;
import com.ietscroll.repository.UserRepository;
import com.ietscroll.response.Result;
import com.ietscroll.service.EmailService;
import com.ietscroll.service.OTPService;

@Service
public class OTPServiceImpl implements OTPService {

	// Minimum time a user must wait between two OTP requests for the same email.
	private static final long RESEND_COOLDOWN_SECONDS = 60;
	// OTP validity window (kept in sync with expirationTime below).
	private static final long OTP_VALIDITY_MINUTES = 10;

	private final UserRepository userRepo;
	private final OTPRepository otpRepo;
	private final EmailService emailService;

	public OTPServiceImpl(EmailService emailService, UserRepository userRepo, OTPRepository otpRepo) {
		this.otpRepo = otpRepo;
		this.userRepo = userRepo;
		this.emailService = emailService;
	}

	@Override
	@Transactional
	public void GenerateOTP(String email) {
		otpRepo.deleteOldOTPs();

		List<OTPEntity> existing = otpRepo.findByEmail(email);
		if (existing != null && !existing.isEmpty()) {
			OTPEntity latest = existing.get(existing.size() - 1);
			LocalDateTime issuedAt = latest.getExpirationTime().minusMinutes(OTP_VALIDITY_MINUTES);
			LocalDateTime cooldownEndsAt = issuedAt.plusSeconds(RESEND_COOLDOWN_SECONDS);
			if (cooldownEndsAt.isAfter(LocalDateTime.now())) {
				throw new LimitExceededException("Please wait a bit before requesting another OTP.");
			}
		}

		// Invalidate any previously issued, still-unexpired OTPs for this email so
		// only the newest one is ever valid.
		otpRepo.deleteByEmail(email);

		SecureRandom secureRandom = new SecureRandom();
		int otp = secureRandom.nextInt(100000, 999999);

		OTPEntity otpEntity = new OTPEntity();
		otpEntity.setExpirationTime(LocalDateTime.now().plusMinutes(OTP_VALIDITY_MINUTES));
		otpEntity.setEmail(email);
		otpEntity.setOtp(otp);

		emailService.sendEmail(email, String.valueOf(otp));

		otpRepo.save(otpEntity);

	}

	@Override
	@Transactional
	public Result verifyOTP(int otpGivenByUser, String email) {
		otpRepo.deleteOldOTPs();
		List<OTPEntity> otps = otpRepo.findByEmail(email);
		UserEntity exist = userRepo.findByEmail(email);

		if (exist == null) {
			throw new ResourceNotFoundException("User doesn't exist");
		}

		if (otps == null || otps.isEmpty()) {
			throw new BadRequestException("Incorrect email or OTP expired!");
		}

		OTPEntity otp = otps.get(otps.size() - 1);
		if (otp.getExpirationTime().isBefore(LocalDateTime.now())) {
			throw new BadRequestException("OTP expired");
		}

		// Check if maximum failed attempts reached (max 5 attempts per OTP)
		if (otp.getAttemptCount() >= 5) {
			otpRepo.deleteByEmail(email); // Lock out by deleting the OTP
			throw new LimitExceededException("Too many failed OTP verification attempts. Please request a new OTP.");
		}

		if (otpGivenByUser == otp.getOtp()) {
			UserEntity user = userRepo.findByEmail(email);
			user.setVerified(true);
			userRepo.save(user);
			otpRepo.deleteByEmail(email); // one-time use: can't be replayed
			return Result.SUCCESS;
		}

		// Increment failed attempt counter
		otp.setAttemptCount(otp.getAttemptCount() + 1);
		otpRepo.save(otp);

		// Remaining attempts left
		int remainingAttempts = 5 - otp.getAttemptCount();
		throw new BadRequestException("Incorrect OTP. You have " + remainingAttempts + " attempt(s) remaining.");
	}

}