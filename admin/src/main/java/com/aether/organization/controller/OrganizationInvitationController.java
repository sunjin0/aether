package com.aether.organization.controller;

import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.organization.entity.Invitation;
import com.aether.organization.entity.OrganizationMember;
import com.aether.organization.service.InvitationService;
import com.aether.organization.service.OrganizationMemberService;
import com.aether.organization.service.OrganizationService;
import com.aether.organization.service.TeamService;
import com.aether.organization.service.TeamMemberService;
import com.aether.organization.entity.Team;
import com.aether.organization.entity.TeamMember;
import com.aether.permission.Permission;
import com.aether.permission.entity.DepartmentMember;
import com.aether.permission.entity.Department;
import com.aether.permission.service.DepartmentMemberService;
import com.aether.permission.service.DepartmentService;
import com.aether.sys.entity.User;
import com.aether.sys.entity.Role;
import com.aether.sys.service.RoleService;
import com.aether.sys.service.UserService;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;

/** 组织邀请只保存 token 摘要，明文 token 仅在创建响应中返回。 */
@RestController
@RequestMapping("/api/platform/invitations")
public class OrganizationInvitationController {
    private final InvitationService invitations;
    private final OrganizationService organizations;
    private final OrganizationMemberService members;
    private final UserService users;
    private final TeamService teams;
    private final TeamMemberService teamMembers;
    private final DepartmentService departments;
    private final DepartmentMemberService departmentMembers;
    private final RoleService roles;

    public OrganizationInvitationController(InvitationService invitations, OrganizationService organizations,
                                             OrganizationMemberService members, UserService users, TeamService teams,
                                             TeamMemberService teamMembers, DepartmentService departments,
                                             DepartmentMemberService departmentMembers, RoleService roles) {
        this.invitations = invitations;
        this.organizations = organizations;
        this.members = members;
        this.users = users;
        this.teams = teams;
        this.teamMembers = teamMembers;
        this.departments = departments;
        this.departmentMembers = departmentMembers;
        this.roles = roles;
    }

    @GetMapping
    @Permission(path = "/sys/organization")
    public WebResponse<List<Invitation>> list(@RequestParam String organizationId) {
        requirePlatformAdmin();
        verifyOrganization(organizationId);
        return WebResponse.OK(invitations.lambdaQuery().eq(Invitation::getOrganizationId, organizationId)
                .eq(Invitation::getDeleted, false).orderByDesc(Invitation::getCreatedAt).list());
    }

    @PostMapping
    @Permission(path = "/sys/organization", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<Invitation> create(@RequestBody InvitationRequest request) {
        requirePlatformAdmin();
        if (request == null || StringUtils.isBlank(request.organizationId) || StringUtils.isBlank(request.email))
            throw new IllegalArgumentException(I18nUtils.getMessage("organization.input.invalid"));
        verifyOrganization(request.organizationId);
        if (StringUtils.isNotBlank(request.teamId)) {
            if (!teams.lambdaQuery().eq(Team::getId, request.teamId).eq(Team::getOrganizationId, request.organizationId)
                    .eq(Team::getState, 0).eq(Team::getDeleted, false).exists())
                throw new ServerException(403, I18nUtils.getMessage("organization.team.cross-scope"));
            if (!java.util.Set.of("DEPARTMENT_ADMIN", "MEMBER", "READ_ONLY").contains(StringUtils.defaultIfBlank(request.roleCode, "MEMBER")))
                throw new IllegalArgumentException(I18nUtils.getMessage("organization.role.invalid"));
        } else if (StringUtils.isNotBlank(request.roleCode) && !"MEMBER".equals(request.roleCode)) {
            throw new IllegalArgumentException(I18nUtils.getMessage("organization.role.invalid"));
        }
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        Invitation invitation = new Invitation();
        invitation.setOrganizationId(request.organizationId);
        invitation.setTeamId(StringUtils.trimToNull(request.teamId));
        invitation.setEmail(StringUtils.lowerCase(StringUtils.trim(request.email)));
        invitation.setRoleCode(StringUtils.defaultIfBlank(request.roleCode, "MEMBER"));
        invitation.setTokenHash(hash(token));
        invitation.setExpiresAt(System.currentTimeMillis() + 7 * 24 * 3600 * 1000L);
        invitation.setStatus("PENDING");
        invitation.setInviterId(currentUserId());
        invitations.save(invitation);
        invitation.setToken(token);
        return WebResponse.OK(invitation);
    }

    @PostMapping("/{id}/revoke")
    @Permission(path = "/sys/organization", type = Permission.Type.Write)
    public WebResponse<Boolean> revoke(@PathVariable String id) {
        requirePlatformAdmin();
        Invitation invitation = invitations.lambdaQuery().eq(Invitation::getId, id)
                .eq(Invitation::getDeleted, false).eq(Invitation::getStatus, "PENDING").one();
        if (invitation == null) throw new IllegalArgumentException(I18nUtils.getMessage("organization.not-found"));
        invitation.setStatus("REVOKED");
        boolean revoked = invitations.updateById(invitation);
        return WebResponse.OK(revoked);
    }

    @PostMapping("/{id}/resend")
    @Permission(path = "/sys/organization", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<Invitation> resend(@PathVariable String id) {
        requirePlatformAdmin();
        Invitation invitation = invitations.lambdaQuery().eq(Invitation::getId, id)
                .eq(Invitation::getDeleted, false).eq(Invitation::getStatus, "PENDING").one();
        if (invitation == null) throw new IllegalArgumentException(I18nUtils.getMessage("organization.invitation.invalid"));
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        invitation.setTokenHash(hash(token));
        invitation.setExpiresAt(System.currentTimeMillis() + 7 * 24 * 3600 * 1000L);
        invitations.updateById(invitation);
        invitation.setToken(token);
        return WebResponse.OK(invitation);
    }

    @PostMapping("/accept")
    @Permission(required = false)
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<Boolean> accept(@RequestBody AcceptRequest request) {
        if (request == null || StringUtils.isBlank(request.token))
            throw new IllegalArgumentException(I18nUtils.getMessage("organization.input.invalid"));
        String userId = currentUserId();
        User user = users.getById(userId);
        Invitation invitation = invitations.lambdaQuery().eq(Invitation::getTokenHash, hash(request.token))
                .eq(Invitation::getStatus, "PENDING").eq(Invitation::getDeleted, false).one();
        if (invitation == null || invitation.getExpiresAt() == null || invitation.getExpiresAt() <= System.currentTimeMillis()
                || user == null || !StringUtils.equalsIgnoreCase(user.getEmail(), invitation.getEmail()))
            throw new IllegalArgumentException(I18nUtils.getMessage("organization.invitation.invalid"));
        boolean claimed = invitations.lambdaUpdate().eq(Invitation::getId, invitation.getId())
                .eq(Invitation::getStatus, "PENDING").set(Invitation::getStatus, "ACCEPTED").update();
        if (!claimed) throw new IllegalArgumentException(I18nUtils.getMessage("organization.invitation.invalid"));
        boolean exists = members.lambdaQuery().eq(OrganizationMember::getOrganizationId, invitation.getOrganizationId())
                .eq(OrganizationMember::getUserId, userId).eq(OrganizationMember::getDeleted, false).exists();
        if (!exists) {
            OrganizationMember member = new OrganizationMember();
            member.setOrganizationId(invitation.getOrganizationId());
            member.setUserId(userId);
            member.setRoleCode("MEMBER");
            member.setSource("INVITATION");
            members.save(member);
        }
        if (StringUtils.isNotBlank(invitation.getTeamId())) {
            TeamMember teamMember = teamMembers.lambdaQuery().eq(TeamMember::getTeamId, invitation.getTeamId())
                    .eq(TeamMember::getUserId, userId).eq(TeamMember::getDeleted, false).one();
            if (teamMember == null) {
                teamMember = new TeamMember(); teamMember.setOrganizationId(invitation.getOrganizationId());
                teamMember.setTeamId(invitation.getTeamId()); teamMember.setUserId(userId);
                teamMember.setRoleCode(invitation.getRoleCode()); teamMembers.save(teamMember);
            }
            Department department = departments.lambdaQuery().eq(Department::getId, invitation.getTeamId())
                    .eq(Department::getOrganizationId, invitation.getOrganizationId()).eq(Department::getDeleted, false).one();
            if (department != null) {
                DepartmentMember departmentMember = departmentMembers.lambdaQuery()
                        .eq(DepartmentMember::getDepartmentId, department.getId())
                        .eq(DepartmentMember::getUserId, userId).eq(DepartmentMember::getDeleted, false).one();
                if (departmentMember == null) {
                    departmentMember = new DepartmentMember();
                    departmentMember.setOrganizationId(invitation.getOrganizationId()); departmentMember.setDepartmentId(department.getId());
                    departmentMember.setUserId(userId); departmentMember.setIdentityCode(invitation.getRoleCode());
                    departmentMembers.save(departmentMember);
                } else {
                    departmentMember.setIdentityCode(invitation.getRoleCode());
                    departmentMembers.updateById(departmentMember);
                }
            }
        }
        return WebResponse.OK(true);
    }

    private void requirePlatformAdmin() {
        String userId = currentUserId();
        List<String> roleIds = users.getRoleIdsByUserId(userId);
        if (roleIds == null || !roles.lambdaQuery().in(Role::getId, roleIds)
                .eq(Role::getScope, "PLATFORM").eq(Role::getDeleted, false)
                .in(Role::getName, "root", "SUPER_ADMIN").exists())
            throw new ServerException(403, I18nUtils.getMessage("organization.platform-admin.only"));
    }

    private void verifyOrganization(String id) {
        if (organizations.lambdaQuery().eq(com.aether.organization.entity.Organization::getId, id)
                .eq(com.aether.organization.entity.Organization::getDeleted, false)
                .eq(com.aether.organization.entity.Organization::getState, 0).one() == null)
            throw new ServerException(404, I18nUtils.getMessage("organization.not-found"));
    }
    private String currentUserId() {
        if (CurrentUser.getUser() == null || StringUtils.isBlank(CurrentUser.getUser().get("userId")))
            throw new ServerException(401, I18nUtils.getMessage("auth.session.required"));
        return CurrentUser.getUser().get("userId");
    }
    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Data public static class InvitationRequest { private String organizationId; private String teamId; private String email; private String roleCode; }
    @Data public static class AcceptRequest { private String token; }
}
