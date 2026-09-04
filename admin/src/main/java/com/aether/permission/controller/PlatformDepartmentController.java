package com.aether.permission.controller;

import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.permission.Permission;
import com.aether.permission.entity.Department;
import com.aether.permission.entity.DepartmentMember;
import com.aether.permission.service.DepartmentMemberService;
import com.aether.permission.service.DepartmentService;
import com.aether.organization.entity.OrganizationMember;
import com.aether.organization.service.OrganizationMemberService;
import com.aether.organization.service.OrganizationService;
import com.aether.sys.entity.Role;
import com.aether.sys.service.RoleService;
import com.aether.sys.service.UserService;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 平台超级管理员维护正式组织架构。部门是唯一的组织树来源。 */
@RestController
@Permission(path = "/sys/organization")
@RequestMapping("/api/platform/departments")
public class PlatformDepartmentController {
    private final DepartmentService departments;
    private final DepartmentMemberService members;
    private final OrganizationService organizations;
    private final UserService users;
    private final RoleService roles;
    private final OrganizationMemberService organizationMembers;

    public PlatformDepartmentController(DepartmentService departments, DepartmentMemberService members,
                                        OrganizationService organizations, UserService users, RoleService roles,
                                        OrganizationMemberService organizationMembers) {
        this.departments = departments;
        this.members = members;
        this.organizations = organizations;
        this.users = users;
        this.roles = roles;
        this.organizationMembers = organizationMembers;
    }

    @GetMapping
    public WebResponse<List<Department>> list(@RequestParam String organizationId) {
        requirePlatformAdmin();
        verifyOrganization(organizationId);
        return WebResponse.OK(departments.lambdaQuery().eq(Department::getOrganizationId, organizationId)
                .eq(Department::getDeleted, false).eq(Department::getState, 0)
                .orderByAsc(Department::getPath).orderByAsc(Department::getSortNum).list());
    }

    @GetMapping("/{departmentId}/members")
    public WebResponse<List<DepartmentMember>> memberList(@PathVariable String departmentId,
                                                          @RequestParam String organizationId) {
        requirePlatformAdmin();
        Department department = verifyDepartment(organizationId, departmentId);
        return WebResponse.OK(members.lambdaQuery().eq(DepartmentMember::getOrganizationId, department.getOrganizationId())
                .eq(DepartmentMember::getDepartmentId, departmentId).eq(DepartmentMember::getDeleted, false)
                .eq(DepartmentMember::getState, 0).list());
    }

    @PostMapping("/{departmentId}/members")
    @Permission(path = "/sys/organization", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<Boolean> assignMember(@PathVariable String departmentId,
                                             @RequestParam String organizationId,
                                             @RequestParam String userId,
                                             @RequestParam(defaultValue = "MEMBER") String roleCode) {
        requirePlatformAdmin();
        Department department = verifyDepartment(organizationId, departmentId);
        if (users.getById(userId) == null || !organizationMembers.lambdaQuery()
                .eq(OrganizationMember::getOrganizationId, organizationId).eq(OrganizationMember::getUserId, userId)
                .eq(OrganizationMember::getState, 0).eq(OrganizationMember::getDeleted, false).exists())
            throw new IllegalArgumentException(I18nUtils.getMessage("organization.not.member"));
        if (!java.util.Set.of("DEPARTMENT_ADMIN", "MEMBER", "READ_ONLY").contains(roleCode))
            throw new IllegalArgumentException(I18nUtils.getMessage("organization.role.invalid"));
        DepartmentMember member = members.lambdaQuery().eq(DepartmentMember::getDepartmentId, departmentId)
                .eq(DepartmentMember::getUserId, userId).eq(DepartmentMember::getDeleted, false).one();
        if (member == null) {
            member = new DepartmentMember(); member.setOrganizationId(organizationId);
            member.setDepartmentId(departmentId); member.setUserId(userId); member.setIdentityCode(roleCode); members.save(member);
        } else {
            member.setIdentityCode(roleCode); members.updateById(member);
        }
        return WebResponse.OK(true);
    }

    @DeleteMapping("/{departmentId}/members/{userId}")
    @Permission(path = "/sys/organization", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<Boolean> removeMember(@PathVariable String departmentId, @PathVariable String userId,
                                             @RequestParam String organizationId) {
        requirePlatformAdmin();
        verifyDepartment(organizationId, departmentId);
        DepartmentMember member = members.lambdaQuery().eq(DepartmentMember::getDepartmentId, departmentId)
                .eq(DepartmentMember::getUserId, userId).eq(DepartmentMember::getDeleted, false).one();
        if (member == null) throw new IllegalArgumentException(I18nUtils.getMessage("organization.member.not-found"));
        member.setDeleted(true); boolean updated = members.updateById(member);
        return WebResponse.OK(updated);
    }

    @PostMapping
    @Permission(path = "/sys/organization", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<Department> create(@RequestBody DepartmentRequest request) {
        requirePlatformAdmin();
        requireInput(request);
        verifyOrganization(request.organizationId);
        if (departments.lambdaQuery().eq(Department::getOrganizationId, request.organizationId)
                .eq(Department::getCode, request.code).eq(Department::getDeleted, false).exists()) {
            throw new IllegalArgumentException(I18nUtils.getMessage("organization.team.code.duplicate"));
        }
        Department parent = null;
        if (StringUtils.isNotBlank(request.parentId)) parent = verifyDepartment(request.organizationId, request.parentId);
        Department department = new Department();
        department.setOrganizationId(request.organizationId);
        department.setParentId(StringUtils.trimToNull(request.parentId));
        department.setCode(StringUtils.trim(request.code));
        department.setName(StringUtils.trim(request.name));
        department.setManagerUserId(StringUtils.trimToNull(request.managerUserId));
        department.setLevel(parent == null ? 1 : parent.getLevel() + 1);
        if (department.getLevel() > 8) throw new IllegalArgumentException(I18nUtils.getMessage("organization.input.invalid"));
        departments.save(department);
        department.setPath(parent == null ? "/" + department.getId() : parent.getPath() + "/" + department.getId());
        departments.updateById(department);
        return WebResponse.OK(department);
    }

    @PostMapping("/{departmentId}")
    @Permission(path = "/sys/organization", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<Department> update(@PathVariable String departmentId, @RequestBody DepartmentRequest request) {
        requirePlatformAdmin();
        requireInput(request);
        Department current = verifyDepartment(request.organizationId, departmentId);
        if (departments.lambdaQuery().eq(Department::getOrganizationId, request.organizationId)
                .eq(Department::getCode, request.code).ne(Department::getId, departmentId)
                .eq(Department::getDeleted, false).exists()) {
            throw new IllegalArgumentException(I18nUtils.getMessage("organization.team.code.duplicate"));
        }
        Department parent = null;
        if (StringUtils.isNotBlank(request.parentId)) {
            parent = verifyDepartment(request.organizationId, request.parentId);
            if (departmentId.equals(request.parentId) || StringUtils.startsWith(parent.getPath(), current.getPath() + "/"))
                throw new IllegalArgumentException(I18nUtils.getMessage("organization.input.invalid"));
        }
        int level = parent == null ? 1 : parent.getLevel() + 1;
        if (level > 8) throw new IllegalArgumentException(I18nUtils.getMessage("organization.input.invalid"));
        String oldPath = current.getPath();
        String newPath = parent == null ? "/" + departmentId : parent.getPath() + "/" + departmentId;
        current.setCode(StringUtils.trim(request.code));
        current.setName(StringUtils.trim(request.name));
        current.setManagerUserId(StringUtils.trimToNull(request.managerUserId));
        current.setParentId(StringUtils.trimToNull(request.parentId));
        current.setLevel(level);
        current.setPath(newPath);
        departments.updateById(current);
        if (!oldPath.equals(newPath)) {
            List<Department> children = departments.lambdaQuery().eq(Department::getOrganizationId, request.organizationId)
                    .eq(Department::getDeleted, false).list();
            children.stream().filter(d -> d.getPath() != null && d.getPath().startsWith(oldPath + "/"))
                    .forEach(d -> { d.setPath(newPath + d.getPath().substring(oldPath.length()));
                        d.setLevel(d.getPath().split("/").length - 1); departments.updateById(d); });
        }
        return WebResponse.OK(current);
    }

    @DeleteMapping("/{departmentId}")
    @Permission(path = "/sys/organization", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<Boolean> delete(@PathVariable String departmentId, @RequestParam String organizationId) {
        requirePlatformAdmin();
        Department department = verifyDepartment(organizationId, departmentId);
        if (members.lambdaQuery().eq(DepartmentMember::getDepartmentId, departmentId)
                .eq(DepartmentMember::getDeleted, false).exists()
                || departments.lambdaQuery().eq(Department::getParentId, departmentId)
                .eq(Department::getDeleted, false).exists()) {
            throw new IllegalStateException(I18nUtils.getMessage("organization.team.delete.members-required"));
        }
        department.setDeleted(true);
        boolean deleted = departments.updateById(department);
        return WebResponse.OK(deleted);
    }

    private Department verifyDepartment(String organizationId, String departmentId) {
        Department department = departments.lambdaQuery().eq(Department::getId, departmentId)
                .eq(Department::getOrganizationId, organizationId).eq(Department::getDeleted, false)
                .eq(Department::getState, 0).one();
        if (department == null) throw new ServerException(403, I18nUtils.getMessage("organization.team.cross-scope"));
        return department;
    }

    private void verifyOrganization(String organizationId) {
        if (!organizations.lambdaQuery().eq(com.aether.organization.entity.Organization::getId, organizationId)
                .eq(com.aether.organization.entity.Organization::getDeleted, false)
                .eq(com.aether.organization.entity.Organization::getState, 0).exists())
            throw new IllegalArgumentException(I18nUtils.getMessage("organization.not-found"));
    }

    private void requirePlatformAdmin() {
        String userId = currentUserId();
        List<String> roleIds = users.getRoleIdsByUserId(userId);
        if (roleIds == null || !roles.lambdaQuery().in(Role::getId, roleIds)
                .eq(Role::getScope, "PLATFORM").eq(Role::getDeleted, false)
                .in(Role::getName, "root", "SUPER_ADMIN").exists())
            throw new ServerException(403, I18nUtils.getMessage("organization.platform-admin.only"));
    }

    private String currentUserId() {
        if (com.aether.local.CurrentUser.getUser() == null || StringUtils.isBlank(com.aether.local.CurrentUser.getUser().get("userId")))
            throw new ServerException(401, I18nUtils.getMessage("auth.session.required"));
        return com.aether.local.CurrentUser.getUser().get("userId");
    }

    private void requireInput(DepartmentRequest request) {
        if (request == null || StringUtils.isBlank(request.organizationId) || StringUtils.isBlank(request.code)
                || StringUtils.isBlank(request.name)) throw new IllegalArgumentException(I18nUtils.getMessage("organization.input.invalid"));
    }

    @Data
    public static class DepartmentRequest {
        private String organizationId;
        private String parentId;
        private String code;
        private String name;
        private String managerUserId;
    }
}
