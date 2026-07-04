package com.ietscroll.controller;

import java.util.List;
import java.util.UUID;

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

import com.ietscroll.general.enums.TeamRequestStatus;
import com.ietscroll.request.TeamJoiningRequest;
import com.ietscroll.response.ApplicationResponse;
import com.ietscroll.response.Result;
import com.ietscroll.response.TeamJoinResponse;
import com.ietscroll.service.TeamRequestService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/request-team")
@Tag(name = "Team Join Requests", description = "Manage team join requests including applying, accepting, rejecting, and viewing applications.")
public class TeamJoinRequestController {

	private final TeamRequestService teamRequestService;

	public TeamJoinRequestController(TeamRequestService teamRequestService) {
		this.teamRequestService = teamRequestService;
	}

	@Operation(summary = "Request to join a team", description = "Submit a request to join a team using team ID and message.")
	@PostMapping

	public ResponseEntity<?> requestToJoin(Authentication authentication,
			@RequestBody TeamJoiningRequest teamJoiningRequest) {
		Result result = teamRequestService.requestToJoinTeam(authentication.getName(),
				UUID.fromString(teamJoiningRequest.teamId()), teamJoiningRequest.message());
		if (result == Result.SUCCESS) {
			return ResponseEntity.status(HttpStatus.CREATED).build();
		}
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
	}

	@Operation(summary = "Get pending join requests", description = "Fetch all pending join requests for the team owner.")
	@GetMapping("/requests")
	public List<TeamJoinResponse> getRequests(Authentication authentication) {

		return teamRequestService.getTeamRequests(authentication.getName(), TeamRequestStatus.WAIT);
	}

	@Operation(summary = "Get accepted team members", description = "Retrieve all accepted members of the team.")
	@GetMapping("/team-members")
	public List<TeamJoinResponse> getTeamMember(Authentication authentication) {

		return teamRequestService.getTeamRequests(authentication.getName(), TeamRequestStatus.ACCEPTED);
	}

	@Operation(summary = "Accept join request", description = "Accept a user's request to join the team.")
	@PatchMapping("/accept/{applicantEmail}")
	public Result acceptRequest(Authentication authentication, @PathVariable String applicantEmail) {
		return teamRequestService.acceptMember(authentication.getName(), applicantEmail);
	}

	@Operation(summary = "Reject join request", description = "Reject a user's request to join the team.")
	@PatchMapping("/reject/{applicantEmail}")
	public Result rejectRequest(Authentication authentication, @PathVariable String applicantEmail) {
		return teamRequestService.rejectMember(authentication.getName(), applicantEmail);
	}

	@Operation(summary = "Remove team member", description = "Remove an existing member from the team.")
	@PatchMapping("/remove/{applicantEmail}")
	public Result removeMember(Authentication authentication, @PathVariable String applicantEmail) {
		return teamRequestService.kickMember(authentication.getName(), applicantEmail);
	}

	@Operation(summary = "Get my applications", description = "Retrieve all team join requests submitted by the current user.")

	@GetMapping("/my-application")
	public List<ApplicationResponse> getMyApplication(Authentication authentication) {
		return teamRequestService.getMyApplications(authentication.getName());
	}

}
