package com.aether.organization.controller;

import com.aether.entity.WebResponse;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.organization.entity.Organization;
import com.aether.organization.entity.OrganizationMember;
import com.aether.organization.entity.Team;
import com.aether.organization.entity.TeamMember;
import com.aether.organization.service.OrganizationMemberService;
import com.aether.organization.service.OrganizationService;
import com.aether.organization.service.TeamMemberService;
import com.aether.organization.service.TeamService;
import com.aether.sys.entity.Role;
import com.aether.sys.service.RoleService;
import com.aether.sys.service.UserService;
import lombok.Data;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** 超级管理员的组织架构编制入口。普通组织管理员不经过此控制器。 */
@RestController
@RequestMapping("/api/sys/organization")
public class PlatformOrganizationController {
    private final OrganizationService organizations;
    private final OrganizationMemberService organizationMembers;
    private final TeamService teams;
    private final TeamMemberService teamMembers;
    private final UserService users;
    private final RoleService roles;

    public PlatformOrganizationController(OrganizationService organizations,
                                          OrganizationMemberService organizationMembers,
                                          TeamService teams,
                                          TeamMemberService teamMembers,
                                          UserService users,
                                          RoleService roles) {
        this.organizations = organizations;
        this.organizationMembers = organizationMembers;
        this.teams = teams;
        this.teamMembers = teamMembers;
        this.users = users;
        this.roles = roles;
    }

    @GetMapping("/list")
    public WebResponse<List<Organization>> list() {
        requirePlatformAdmin();
        return WebResponse.OK(organizations.lambdaQuery().eq(Organization::getDeleted, false).list());
    }

    @PostMapping("/create")
    public WebResponse<Organization> create(@RequestBody OrganizationRequest request) {
        requirePlatformAdmin();
        validateOrganizationRequest(request);
        String code = normalized(request.getCode());
        if (organizations.lambdaQuery().eq(Organization::getCode, code).eq(Organization::getDeleted, false).exists())
            throw new IllegalStateException(I18nUtils.getMessage("organization.code.duplicate"));
        Organization organization = new Organization();
        organization.setCode(code);
        organization.setName(normalized(request.getName()));
        organization.setOwnerId(userId());
        organizations.save(organization);
        return WebResponse.OK(organization);
    }

    @PostMapping("/update")
    public WebResponse<Organization> update(@RequestBody OrganizationRequest request) {
        requirePlatformAdmin();
        validateOrganizationRequest(request);
        if (!StringUtils.hasText(request.getId()) || !organizations.lambdaQuery().eq(Organization::getId, request.getId())
                .eq(Organization::getDeleted, false).exists())
            throw new IllegalStateException(I18nUtils.getMessage("organization.not-found"));
        String code = normalized(request.getCode());
        if (organizations.lambdaQuery().eq(Organization::getCode, code).ne(Organization::getId, request.getId())
                .eq(Organization::getDeleted, false).exists())
            throw new IllegalStateException(I18nUtils.getMessage("organization.code.duplicate"));
        Organization organization = new Organization();
        organization.setId(request.getId());
        organization.setCode(code);
        organization.setName(normalized(request.getName()));
        organizations.updateById(organization);
        return WebResponse.OK(organizations.getById(request.getId()));
    }

    @PostMapping("/delete")
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<Boolean> delete(@RequestParam String organizationId) {
        requirePlatformAdmin();
        verifyOrganization(organizationId);
        if (!organizationMembers.lambdaQuery().eq(OrganizationMember::getOrganizationId, organizationId)
                .eq(OrganizationMember::getDeleted, false).exists()
                && !teamMembers.lambdaQuery().eq(TeamMember::getOrganizationId, organizationId)
                .eq(TeamMember::getDeleted, false).exists()) {
            Organization organization = organizations.getById(organizationId);
            if (organization == null || Boolean.TRUE.equals(organization.getDeleted()))
                throw new IllegalStateException(I18nUtils.getMessage("organization.not-found"));
            teams.lambdaUpdate().eq(Team::getOrganizationId, organizationId)
                    .eq(Team::getDeleted, false).set(Team::getDeleted, true).update();
            organization.setDeleted(true);
            return WebResponse.OK(organizations.updateById(organization));
        }
        throw new IllegalStateException(I18nUtils.getMessage("organization.delete.members-required"));
    }

    @PostMapping("/member/assign")
    public WebResponse<Boolean> assignOrganizationMember(@RequestParam String organizationId,
                                                         @RequestParam String userId) {
        requirePlatformAdmin();
        verifyOrganization(organizationId);
        if (users.getById(userId) == null) throw new IllegalStateException(I18nUtils.getMessage("organization.user.not-found"));
        return WebResponse.OK(saveOrganizationMember(organizationId, userId));
    }

    @GetMapping("/member/list")
    public WebResponse<List<OrganizationMember>> organizationMemberList(@RequestParam String organizationId) {
        requirePlatformAdmin();
        verifyOrganization(organizationId);
        return WebResponse.OK(organizationMembers.lambdaQuery().eq(OrganizationMember::getOrganizationId, organizationId)
                .eq(OrganizationMember::getDeleted, false).eq(OrganizationMember::getState, 0).list());
    }

    @PostMapping("/member/remove")
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<Boolean> removeOrganizationMember(@RequestParam String organizationId, @RequestParam String userId) {
        requirePlatformAdmin();
        verifyOrganization(organizationId);
        OrganizationMember member = organizationMembers.lambdaQuery().eq(OrganizationMember::getOrganizationId, organizationId)
                .eq(OrganizationMember::getUserId, userId).eq(OrganizationMember::getDeleted, false).one();
        if (member == null) throw new IllegalStateException(I18nUtils.getMessage("organization.member.not-found"));
        member.setDeleted(true);
        boolean updated = organizationMembers.updateById(member);
        // 移出组织时同步清理其部门归属，避免架构图和部门成员列表残留。
        teamMembers.lambdaUpdate().eq(TeamMember::getOrganizationId, organizationId)
                .eq(TeamMember::getUserId, userId).eq(TeamMember::getDeleted, false)
                .set(TeamMember::getDeleted, true).update();
        return WebResponse.OK(updated);
    }

    @PostMapping("/team/member/assign")
    public WebResponse<Boolean> assignTeamMember(@RequestParam String organizationId,
                                                 @RequestParam String teamId,
                                                 @RequestParam String userId,
                                                 @RequestParam(defaultValue = "MEMBER") String roleCode) {
        requirePlatformAdmin();
        if (!teams.lambdaQuery().eq(Team::getId, teamId).eq(Team::getOrganizationId, organizationId)
                .eq(Team::getDeleted, false).exists()) throw new IllegalStateException(I18nUtils.getMessage("organization.team.cross-scope"));
        if (!organizationMembers.lambdaQuery().eq(OrganizationMember::getOrganizationId, organizationId)
                .eq(OrganizationMember::getUserId, userId).eq(OrganizationMember::getState, 0)
                .eq(OrganizationMember::getDeleted, false).exists()) throw new IllegalStateException(I18nUtils.getMessage("organization.not.member"));
        if (!java.util.Set.of("DEPARTMENT_ADMIN", "MEMBER", "READ_ONLY").contains(roleCode))
            throw new IllegalArgumentException(I18nUtils.getMessage("organization.role.invalid"));
        TeamMember member = teamMembers.lambdaQuery().eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId).eq(TeamMember::getDeleted, false).one();
        if (member == null) {
            member = new TeamMember();
            member.setOrganizationId(organizationId);
            member.setTeamId(teamId);
            member.setUserId(userId);
            member.setRoleCode(roleCode);
            return WebResponse.OK(teamMembers.save(member));
        }
        member.setRoleCode(roleCode);
        member.setState(0);
        member.setDeleted(false);
        return WebResponse.OK(teamMembers.updateById(member));
    }

    @GetMapping("/team/list")
    public WebResponse<List<Team>> teamList(@RequestParam String organizationId) {
        requirePlatformAdmin();
        verifyOrganization(organizationId);
        return WebResponse.OK(teams.lambdaQuery().eq(Team::getOrganizationId, organizationId)
                .eq(Team::getDeleted, false).list());
    }

    @GetMapping("/team/member/list")
    public WebResponse<List<TeamMember>> teamMemberList(@RequestParam String organizationId, @RequestParam String teamId) {
        requirePlatformAdmin();
        verifyTeam(organizationId, teamId);
        return WebResponse.OK(teamMembers.lambdaQuery().eq(TeamMember::getOrganizationId, organizationId)
                .eq(TeamMember::getTeamId, teamId).eq(TeamMember::getDeleted, false)
                .eq(TeamMember::getState, 0).list());
    }

    @PostMapping("/team/member/remove")
    public WebResponse<Boolean> removeTeamMember(@RequestParam String organizationId, @RequestParam String teamId,
                                                 @RequestParam String userId) {
        requirePlatformAdmin();
        verifyTeam(organizationId, teamId);
        TeamMember member = teamMembers.lambdaQuery().eq(TeamMember::getOrganizationId, organizationId)
                .eq(TeamMember::getTeamId, teamId).eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getDeleted, false).one();
        if (member == null) throw new IllegalStateException(I18nUtils.getMessage("organization.team.member.not-found"));
        member.setDeleted(true);
        return WebResponse.OK(teamMembers.updateById(member));
    }

    @PostMapping("/team/create")
    public WebResponse<Team> createTeam(@RequestBody TeamRequest request) {
        requirePlatformAdmin();
        validateTeamRequest(request);
        verifyOrganization(request.getOrganizationId());
        String code = normalized(request.getCode());
        if (teams.lambdaQuery().eq(Team::getOrganizationId, request.getOrganizationId()).eq(Team::getCode, code)
                .eq(Team::getDeleted, false).exists()) throw new IllegalStateException(I18nUtils.getMessage("organization.team.code.duplicate"));
        Team team = new Team();
        team.setOrganizationId(request.getOrganizationId());
        team.setCode(code);
        team.setName(normalized(request.getName()));
        teams.save(team);
        return WebResponse.OK(team);
    }

    @PostMapping("/team/update")
    public WebResponse<Team> updateTeam(@RequestBody TeamRequest request) {
        requirePlatformAdmin();
        validateTeamRequest(request);
        verifyTeam(request.getOrganizationId(), request.getId());
        String code = normalized(request.getCode());
        if (teams.lambdaQuery().eq(Team::getOrganizationId, request.getOrganizationId()).eq(Team::getCode, code)
                .ne(Team::getId, request.getId()).eq(Team::getDeleted, false).exists())
            throw new IllegalStateException(I18nUtils.getMessage("organization.team.code.duplicate"));
        Team team = new Team();
        team.setId(request.getId());
        team.setOrganizationId(request.getOrganizationId());
        team.setCode(code);
        team.setName(normalized(request.getName()));
        teams.updateById(team);
        return WebResponse.OK(teams.getById(request.getId()));
    }

    @PostMapping("/team/delete")
    public WebResponse<Boolean> deleteTeam(@RequestParam String organizationId, @RequestParam String teamId) {
        requirePlatformAdmin();
        verifyTeam(organizationId, teamId);
        if (teamMembers.lambdaQuery().eq(TeamMember::getOrganizationId, organizationId)
                .eq(TeamMember::getTeamId, teamId).eq(TeamMember::getDeleted, false).exists())
            throw new IllegalStateException(I18nUtils.getMessage("organization.team.delete.members-required"));
        Team team = teams.getById(teamId);
        team.setDeleted(true);
        return WebResponse.OK(teams.updateById(team));
    }

    private boolean saveOrganizationMember(String organizationId, String userId) {
        OrganizationMember member = organizationMembers.lambdaQuery().eq(OrganizationMember::getOrganizationId, organizationId)
                .eq(OrganizationMember::getUserId, userId).eq(OrganizationMember::getDeleted, false).one();
        if (member != null) return true;
        // 软删除记录不做“复活”更新：逻辑删除插件会限制 deleted=FALSE，
        // 新建一条有效关联可保证移出后能够重新分配。
        member = new OrganizationMember();
        member.setOrganizationId(organizationId);
        member.setUserId(userId);
        member.setRoleCode("MEMBER");
        member.setSource("PLATFORM");
        member.setState(0);
        return organizationMembers.save(member);
    }

    private void verifyOrganization(String organizationId) {
        if (!organizations.lambdaQuery().eq(Organization::getId, organizationId)
                .eq(Organization::getDeleted, false).exists())
            throw new IllegalStateException(I18nUtils.getMessage("organization.not-found"));
    }

    private void validateOrganizationRequest(OrganizationRequest request) {
        if (request == null || !StringUtils.hasText(request.getName()) || !StringUtils.hasText(request.getCode()))
            throw new IllegalArgumentException(I18nUtils.getMessage("organization.input.invalid"));
    }

    private void validateTeamRequest(TeamRequest request) {
        if (request == null || !StringUtils.hasText(request.getOrganizationId()) || !StringUtils.hasText(request.getName())
                || !StringUtils.hasText(request.getCode()))
            throw new IllegalArgumentException(I18nUtils.getMessage("organization.input.invalid"));
    }

    private String normalized(String value) {
        return value == null ? null : value.trim();
    }

    @Data
    public static class OrganizationRequest {
        private String id;
        private String code;
        private String name;
    }

    @Data
    public static class TeamRequest {
        private String id;
        private String organizationId;
        private String code;
        private String name;
    }

    private void verifyTeam(String organizationId, String teamId) {
        if (!teams.lambdaQuery().eq(Team::getId, teamId).eq(Team::getOrganizationId, organizationId)
                .eq(Team::getDeleted, false).exists()) throw new IllegalStateException(I18nUtils.getMessage("organization.team.cross-scope"));
    }

    private String userId() {
        if (CurrentUser.getUser() == null || CurrentUser.getUser().get("userId") == null)
            throw new IllegalStateException(I18nUtils.getMessage("auth.session.required"));
        return CurrentUser.getUser().get("userId");
    }

    private void requirePlatformAdmin() {
        String userId = userId();
        List<String> roleIds = users.getRoleIdsByUserId(userId);
        if (roleIds == null || !roles.lambdaQuery().in(Role::getId, roleIds).eq(Role::getName, "root")
                .eq(Role::getScope, "PLATFORM").eq(Role::getDeleted, false).exists())
            throw new IllegalStateException(I18nUtils.getMessage("organization.platform-admin.only"));
    }
}
