package com.ietscroll.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.modelmapper.ModelMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ietscroll.dto.TeamDTO;
import com.ietscroll.entity.Skills;
import com.ietscroll.entity.Team;
import com.ietscroll.entity.TeamFinderSkill;
import com.ietscroll.entity.TeamJoinRequest;
import com.ietscroll.entity.UserEntity;
import com.ietscroll.exception.BadRequestException;
import com.ietscroll.exception.ContentModerationException;
import com.ietscroll.exception.DuplicateResourceException;
import com.ietscroll.exception.ResourceNotFoundException;
import com.ietscroll.general.enums.Privacy;
import com.ietscroll.general.enums.TeamRequestStatus;
import com.ietscroll.general.enums.TeamStatus;
import com.ietscroll.repository.SkillRepository;
import com.ietscroll.repository.TeamJoinRequestRepository;
import com.ietscroll.repository.TeamRepository;
import com.ietscroll.repository.UserRepository;
import com.ietscroll.response.Result;
import com.ietscroll.response.TeamResponse;
import com.ietscroll.service.TeamService;

@Service
public class TeamServiceImpl implements TeamService {

	private TeamRepository teamRepo;
	private TeamJoinRequestRepository teamJoinRequestRepo;
	private SkillRepository skillRepository;
	private ChatClient mistralChatClient;
	private UserRepository userRepo;
	private ModelMapper modelMapper;

	public TeamServiceImpl(TeamRepository teamRepo, SkillRepository skillRepository,
			@Qualifier("mistralChatClient") ChatClient mistralChatClient, UserRepository userRepo,
			TeamJoinRequestRepository teamJoinRequestRepo,ModelMapper modelMapper) {
		this.teamRepo = teamRepo;
		this.skillRepository = skillRepository;
		this.mistralChatClient = mistralChatClient;
		this.userRepo = userRepo;
		this.teamJoinRequestRepo=teamJoinRequestRepo;
		this.modelMapper=modelMapper;
	}

	@Override
	@Transactional
	public TeamResponse createTeam(String ownerEmail, TeamDTO team) {

		if (ownerEmail == null || team == null) {
			throw new BadRequestException("Invalid details");
		}

		int teamsCreated = teamRepo.countByCreatedBy_EmailAndStatus(ownerEmail, TeamStatus.OPEN);
		if (teamsCreated >= 1) {
			throw new DuplicateResourceException("You can't create more than one team");
		}

		String isSafe = mistralChatClient.prompt().user(team.getPurpose()).call().content();

		if (!Boolean.parseBoolean(isSafe)) {
			throw new ContentModerationException("Kindly maintain decorum!");
		}

		UserEntity user = userRepo.findByEmail(ownerEmail);

		Team teamEntity = new Team();
		teamEntity.setCreatedBy(user);
		teamEntity.setMaxMember(team.getMaxMember());
		teamEntity.setPurpose(team.getPurpose());
		teamEntity.setPrivacy(team.getPrivacy());

		List<Skills> skills = new ArrayList<>();
		if (team.getSkillIds() != null && !team.getSkillIds().isEmpty()) {
			skills = skillRepository.findAllById(team.getSkillIds());
			if (skills.size() != team.getSkillIds().size()) {
				throw new ResourceNotFoundException("Some skills not found");
			}
		}

		List<TeamFinderSkill> neededSkills = new ArrayList<>();

		for (Skills skill : skills) {
			TeamFinderSkill tfs = new TeamFinderSkill();
			tfs.setSkill(skill);
			tfs.setTeam(teamEntity);
			neededSkills.add(tfs);
		}

		teamEntity.setNeededSkills(neededSkills);

		teamEntity = teamRepo.save(teamEntity);
		
		TeamJoinRequest teamJoinRequest = new TeamJoinRequest();
		teamJoinRequest.setApplicant(user);
		teamJoinRequest.setMessage("OWNER");
		teamJoinRequest.setRequestedTeam(teamEntity);
		teamJoinRequest.setStatus(TeamRequestStatus.ACCEPTED);
		
		teamJoinRequestRepo.save(teamJoinRequest);

		TeamResponse teamResponse = new TeamResponse();

		teamResponse.setCreatedAt(teamEntity.getCreatedAt());
		teamResponse.setCreatedBy(teamEntity.getCreatedBy().getEmail());
		teamResponse.setMaxMember(teamEntity.getMaxMember());
		teamResponse.setPublicId(teamEntity.getPublicId());
		teamResponse.setPurpose(teamEntity.getPurpose());
		teamResponse.setStatus(teamEntity.getStatus());
		teamResponse.setPrivacy(teamEntity.getPrivacy());
		return teamResponse;
	}

	@Override
	@Transactional
	public Result closeTeam(String ownerEmail) {

		int count = teamRepo.closeTeam(ownerEmail);

		return count == 1 ? Result.SUCCESS : Result.FAILED;
	}

	@Override
	@Transactional
	public Result changeTeamSize(String ownerEmail, int teamSize) {

		if (teamSize <= 0 || teamSize > 20) {
			throw new BadRequestException("Team size should not less than zero and higher than twenty");
		}
		int count = teamRepo.updateTeamSize(ownerEmail, teamSize);
		
		return count == 1 ? Result.SUCCESS : Result.FAILED;
	}

	@Override
	public TeamResponse getTeamById(UUID publicId) {
		if (publicId == null || publicId.toString().isBlank()) {
			throw new BadRequestException("Invalid Id");
		}

		Team team = teamRepo.findByPublicId(publicId);

		if (team == null) {
			throw new ResourceNotFoundException("No valid team found with given team Id");
		}

		TeamResponse response = new TeamResponse();

		response.setCreatedAt(team.getCreatedAt());
		response.setCreatedBy(team.getCreatedBy().getEmail());
		response.setMaxMember(team.getMaxMember());
		response.setPurpose(team.getPurpose());
		response.setCurrentMember(team.getCurrentMember());

		response.setPrivacy(team.getPrivacy());

		return response;
	}

	@Override
	public Page<TeamResponse> getActiveTeams(int page, int size) {
		Pageable pageable = PageRequest.of(page, size);

		Page<Team >teams = teamRepo.findByStatusAndPrivacy(TeamStatus.OPEN, Privacy.PUBLIC, pageable);
		
		if(teams==null) {
			throw new ResourceNotFoundException("No active teams rightnow");
		}
		return teams.map(
				team -> {
			TeamResponse teamResponse = new TeamResponse();
			teamResponse.setCreatedBy(team.getCreatedBy().getEmail());
			teamResponse.setCreatedAt(team.getCreatedAt());
			teamResponse.setMaxMember(team.getMaxMember());
			teamResponse.setCurrentMember(team.getCurrentMember());
			teamResponse.setPublicId(team.getPublicId());
			teamResponse.setPurpose(team.getPurpose());
			teamResponse.setStatus(team.getStatus());
			teamResponse.setPrivacy(team.getPrivacy());
			return teamResponse;
		});
	}

	@Override
	public TeamResponse getMyTeamDetails(String onwerEmail) {

		Team team = teamRepo.findByStatusAndCreatedBy_Email(TeamStatus.OPEN, onwerEmail);
		if(team==null) {
			throw new ResourceNotFoundException("Team doesn't exist");
		}
		TeamResponse teamResponse = modelMapper.map(team, TeamResponse.class);
		teamResponse.setCreatedBy(onwerEmail);
		return teamResponse;

	}

	@Override
	public List<TeamResponse> getMyTeamPosts(String ownerEmail) {
		return null;
	}

}
