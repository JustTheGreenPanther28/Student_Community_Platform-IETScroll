package com.ietscroll.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.ietscroll.entity.TeamJoinRequest;
import com.ietscroll.general.enums.TeamRequestStatus;

@Repository
public interface TeamJoinRequestRepository extends JpaRepository<TeamJoinRequest, Integer> {

	@Query(value = "SELECT COUNT(*) FROM team_join_requests WHERE applicant_email=?1 AND team_id=?2", nativeQuery = true)
	int existsByEmailAndTeamPublicId(String email, UUID publicId);

	List<TeamJoinRequest> findByRequestedTeam_CreatedBy_EmailAndStatus(String email, TeamRequestStatus status);

	List<TeamJoinRequest> findByApplicant_Email(String applicantEmail);

	@Transactional
	@Modifying
	@Query(value = "UPDATE team_join_requests SET status=?1 WHERE status='WAIT' AND applicant_email=?2 AND team_id=?3", nativeQuery = true)
	int changeStatusOfApplicant(String status, String applicantEmail, UUID teamId);

	@Transactional
	@Modifying
	@Query(value = "UPDATE team_join_requests SET status='REMOVED' WHERE status='ACCEPTED' AND applicant_email=?1 AND team_id=?2", nativeQuery = true)
	int kickApplicant(String applicantEmail, UUID teamId);

	@Transactional
	@Modifying
	@Query(value = "DELETE FROM team_join_requests WHERE team_id = ?1", nativeQuery = true)
	int deleteByTeamPublicId(UUID teamPublicId);
}