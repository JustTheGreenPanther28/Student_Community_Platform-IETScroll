package com.ietscroll.service.impl;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ietscroll.entity.OTPEntity;
import com.ietscroll.entity.UserEntity;
import com.ietscroll.exception.BadRequestException;
import com.ietscroll.exception.ResourceNotFoundException;
import com.ietscroll.repository.OTPRepository;
import com.ietscroll.repository.UserRepository;
import com.ietscroll.response.Result;
import com.ietscroll.service.EmailService;
import com.ietscroll.service.OTPService;

@Service
public class OTPServiceImpl implements OTPService {

	private final  UserRepository userRepo;
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
		SecureRandom secureRandom = new SecureRandom();
		int otp = secureRandom.nextInt(100000, 999999);

		OTPEntity otpEntity = new OTPEntity();
		otpEntity.setExpirationTime(LocalDateTime.now().plusMinutes(10));
		otpEntity.setEmail(email);
		otpEntity.setOtp(otp);

		emailService.sendEmail(email, String.valueOf(otp));

		otpRepo.save(otpEntity);

	}

	@Override
	public Result verifyOTP(int otpGivenByUser, String email) {
		otpRepo.deleteOldOTPs();
		List<OTPEntity> otps = otpRepo.findByEmail(email);
		UserEntity exist = userRepo.findByEmail(email);

		if(exist==null) {
			throw new ResourceNotFoundException("User doesn't exist");
		}
		
		if (otps==null || otps.isEmpty()) {
			throw new BadRequestException("Incorrect email or OTP expired!");
		}

		OTPEntity otp = otps.get(otps.size() - 1);
		if (otp.getExpirationTime().isBefore(LocalDateTime.now())) {
		    throw new BadRequestException("OTP expired");
		}

		if (otpGivenByUser == otp.getOtp()) {
			UserEntity user = userRepo.findByEmail(email);
			user.setVerified(true);
			userRepo.save(user);
			return Result.SUCCESS;
		}

		return Result.FAILED;
	}

}
