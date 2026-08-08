package com.ietscroll.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.ietscroll.entity.OTPEntity;

@Repository
public interface OTPRepository extends JpaRepository<OTPEntity,Integer>{
	List<OTPEntity> findByEmail(String email);
	
	@Modifying
	@Transactional
	@Query(value = "delete from otp where expiration_time < CURRENT_TIMESTAMP", nativeQuery=true)
	void deleteOldOTPs();

	@Modifying
	@Transactional
	void deleteByEmail(String email);
}