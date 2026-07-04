package com.ietscroll.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ietscroll.dto.TeamDTO;
import com.ietscroll.request.TeamCreationRequest;
import com.ietscroll.response.Result;
import com.ietscroll.response.TeamResponse;
import com.ietscroll.service.TeamService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/api/v1/team")
@Tag(name = "Team Management", description = "APIs for creating, managing, and exploring teams with AI-based content moderation.")
public class TeamController {

	private TeamService teamService;

	public TeamController(TeamService teamService) {
		this.teamService = teamService;
	}

	@GetMapping
	@Operation(summary = "Get all active public teams", description = "Returns paginated list of active public teams.")
	public Page<TeamResponse> getTeams(Authentication authetication, @RequestParam int page, @RequestParam int size) {
		return teamService.getActiveTeams(page, size);
	}

	@GetMapping("/me")
	@Operation(summary = "Get my team details", description = "Fetch details of the authenticated user's active team.")

	public TeamResponse getMyTeam(Authentication authentication) {
		return teamService.getMyTeamDetails(authentication.getName());
	}

	@Operation(summary = "Create a team", description = "Creates a new team. The purpose is validated using AI moderation before saving.")
	@PostMapping

	public ResponseEntity<TeamResponse> createTeam(Authentication authentication,
			@RequestBody TeamCreationRequest teamCreationRequest) {
		TeamDTO teamDTO = new TeamDTO();
		teamDTO.setPurpose(teamCreationRequest.purpose());
		teamDTO.setMaxMember(teamCreationRequest.teamSize());
		teamDTO.setSkillIds(teamCreationRequest.skillIds());
		teamDTO.setPrivacy(teamCreationRequest.privacy());

		return ResponseEntity.status(HttpStatus.CREATED).body(teamService.createTeam(authentication.getName(), teamDTO));
	}

	@Operation(summary = "Close team", description = "Closes the currently active team created by the user.")
	@PatchMapping("/close")
	public Result closeTeam(Authentication authentication) {
		return teamService.closeTeam(authentication.getName());
	}

	@Operation(summary = "Update team size", description = "Modify the maximum number of members allowed in the team.")
	@PatchMapping("/team-size")
	public Result changeTeamSize(Authentication authentication, @RequestParam @Min(3) int teamSize) {
		return teamService.changeTeamSize(authentication.getName(), teamSize);
	}

}
