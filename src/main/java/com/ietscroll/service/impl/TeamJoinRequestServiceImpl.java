package com.ietscroll.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ietscroll.entity.Team;
import com.ietscroll.entity.TeamJoinRequest;
import com.ietscroll.entity.UserEntity;
import com.ietscroll.general.enums.TeamRequestStatus;
import com.ietscroll.general.enums.TeamStatus;
import com.ietscroll.repository.TeamJoinRequestRepository;
import com.ietscroll.repository.TeamRepository;
import com.ietscroll.repository.UserRepository;
import com.ietscroll.response.ApplicationResponse;
import com.ietscroll.response.Result;
import com.ietscroll.response.TeamJoinResponse;
import com.ietscroll.service.TeamRequestService;

@Service
public class TeamJoinRequestServiceImpl implements TeamRequestService {

	private TeamJoinRequestRepository teamJoinRequestRepo;
	private TeamRepository teamRepo;
	private UserRepository userRepo;

	public TeamJoinRequestServiceImpl(TeamJoinRequestRepository teamRequestRepo, TeamRepository teamRepo,
			UserRepository userRepo) {
		this.teamJoinRequestRepo = teamRequestRepo;
		this.teamRepo = teamRepo;
		this.userRepo = userRepo;
	}

	@Override
	@Transactional
	public Result requestToJoinTeam(String joinerEmail, UUID teamId, String message) {
		if (teamId == null || teamId.toString().isBlank()) {
			throw new RuntimeException("Team id is wrong");
		}
		if (teamJoinRequestRepo.existsByEmailAndTeamPublicId(joinerEmail, teamId) > 0) {
			throw new RuntimeException("You already applied here");
		}

		Team appliedTo = teamRepo.findByPublicId(teamId);
		if (appliedTo == null || appliedTo.getStatus() == TeamStatus.CLOSED) {
			throw new RuntimeException("Incorrect Team ID!");
		}

		TeamJoinRequest teamJoinRequest = new TeamJoinRequest();

		UserEntity user = userRepo.findByEmail(joinerEmail);

		teamJoinRequest.setApplicant(user);
		teamJoinRequest.setMessage(message);
		teamJoinRequest.setRequestedTeam(appliedTo);

		TeamJoinRequest requested = teamJoinRequestRepo.save(teamJoinRequest);

		return requested == null ? Result.FAILED : Result.SUCCESS;
	}

	@Override
	public List<TeamJoinResponse> getTeamRequests(String ownerEmail, TeamRequestStatus teamRequestStatus) {
		if (ownerEmail == null || ownerEmail.isEmpty()) {
			throw new RuntimeException("Invalid");
		}

		List<TeamJoinRequest> joinRequests = teamJoinRequestRepo
				.findByRequestedTeam_CreatedBy_EmailAndStatus(ownerEmail, teamRequestStatus);

		if (joinRequests == null || joinRequests.isEmpty()) {
			return new ArrayList<TeamJoinResponse>();
		}

		List<TeamJoinResponse> responses = new ArrayList<>();

		for (TeamJoinRequest teamJoinRequest : joinRequests) {
			TeamJoinResponse response = new TeamJoinResponse();
			response.setApplicantEmail(teamJoinRequest.getApplicant().getEmail());
			response.setApplicantUsername(teamJoinRequest.getApplicant().getUsername());
			response.setApplicantFullName(teamJoinRequest.getApplicant().getFullName());
			response.setApplicantCouse(teamJoinRequest.getApplicant().getCourse());
			response.setApplicantCourse(teamJoinRequest.getApplicant().getBranch());
			response.setYearOfPassout(teamJoinRequest.getApplicant().getYearOfPassout());
			response.setTeamId(teamJoinRequest.getRequestedTeam().getPublicId().toString());
			response.setStatus(teamJoinRequest.getStatus());
			response.setMessage(teamJoinRequest.getMessage());
			response.setRequestedAt(teamJoinRequest.getRequestedAt());
			responses.add(response);
		}

		return responses;
	}

	@Override
	@Transactional
	public Result acceptMember(String ownerEmail, String joinerEmail) {

		if (ownerEmail == null || joinerEmail == null || ownerEmail.isBlank() || joinerEmail.isBlank()) {
			System.out.println("hereeeeeeeeeee");
			throw new RuntimeException("Invalid request!");
		}

		if (ownerEmail.equals(joinerEmail)) {
			System.out.println("");
			throw new RuntimeException("Invalid request!");
		}

		Team ownerTeam = teamRepo.findByStatusAndCreatedBy_Email(TeamStatus.OPEN, ownerEmail);

		if (ownerTeam == null) {
			throw new RuntimeException("Team doesn't exist or closed");
		}

		if (ownerTeam.getCurrentMember() >= ownerTeam.getMaxMember()) {
			throw new RuntimeException(
					"Maximum team size is " + ownerTeam.getMaxMember() + ". You can't add more members!");
		}

		int rowsChanged = teamJoinRequestRepo.changeStatusOfApplicant(TeamRequestStatus.ACCEPTED.toString(),
				joinerEmail, ownerTeam.getPublicId());

		if (rowsChanged == 1) {
			ownerTeam.setCurrentMember(ownerTeam.getCurrentMember() + 1);
			teamRepo.save(ownerTeam);
			return Result.SUCCESS;
		}

		return Result.FAILED;
	}

	@Override
	@Transactional
	public Result rejectMember(String ownerEmail, String joinerEmail) {

		if (ownerEmail == null || joinerEmail == null || ownerEmail.isBlank() || joinerEmail.isBlank()) {
			throw new RuntimeException("Invalid request!");
		}

		if (ownerEmail.equals(joinerEmail)) {
			throw new RuntimeException("Invalid request!");
		}

		Team ownerTeam = teamRepo.findByStatusAndCreatedBy_Email(TeamStatus.OPEN, ownerEmail);

		if (ownerTeam == null) {
			throw new RuntimeException("Team doesn't exist or closed");
		}

		int rowsChanged = teamJoinRequestRepo.changeStatusOfApplicant(TeamRequestStatus.REJECTED.toString(),
				joinerEmail, ownerTeam.getPublicId());

		if (rowsChanged == 1) {
			return Result.SUCCESS;
		}
		return Result.FAILED;

	}

	@Override
	@Transactional
	public Result kickMember(String ownerEmail, String memberEmail) {

		if (ownerEmail == null || memberEmail == null || ownerEmail.isBlank() || memberEmail.isBlank()) {
			throw new RuntimeException("Invalid request!");
		}

		if (ownerEmail.equals(memberEmail)) {
			throw new RuntimeException("Invalid request!");
		}

		Team ownerTeam = teamRepo.findByStatusAndCreatedBy_Email(TeamStatus.OPEN, ownerEmail);

		if (ownerTeam == null) {
			throw new RuntimeException("Team doesn't exist or closed");
		}

		int rowsChanged = teamJoinRequestRepo.kickApplicant(memberEmail, ownerTeam.getPublicId());

		if (rowsChanged == 1) {
			ownerTeam.setCurrentMember(ownerTeam.getCurrentMember() - 1);
			teamRepo.save(ownerTeam);
			return Result.SUCCESS;
		}

		return Result.FAILED;
	}

	@Override
	public List<ApplicationResponse> getMyApplications(String joinerEmail) {

		if (joinerEmail == null || joinerEmail.isBlank()) {
			throw new RuntimeException("Invalid Request!");
		}

		List<TeamJoinRequest> joinRequests = teamJoinRequestRepo.findByApplicant_Email(joinerEmail);

		List<ApplicationResponse> applications = new ArrayList<>();

		for (TeamJoinRequest joinRequest : joinRequests) {
			if ("OWNER".equals(joinRequest.getMessage())) {
				continue;
			}
			ApplicationResponse application = new ApplicationResponse();
			application.setRequestedAt(joinRequest.getRequestedAt());
			application.setStatus(joinRequest.getStatus());
			application.setTeamId(joinRequest.getRequestedTeam().getPublicId().toString());
			application.setYourMessage(joinRequest.getMessage());
			applications.add(application);
		}

		return applications;
	}
}
