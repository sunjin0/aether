package com.aether.sys.controller;

import com.aether.sys.entity.Role;
import com.aether.sys.entity.User;
import com.aether.sys.entity.UserRole;
import com.aether.sys.service.RoleService;
import com.aether.sys.service.ScimBearerTokenValidator;
import com.aether.sys.service.UserRoleService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Maps tenant-scoped roles to read-only SCIM Groups. */
@RestController
@RequestMapping("${aether.identity.scim.base-path:/scim/v2}/Groups")
public class ScimGroupController {
    private final ScimBearerTokenValidator validator;
    private final RoleService roleService;
    private final UserRoleService userRoleService;
    private final com.aether.sys.service.UserService userService;

    public ScimGroupController(ScimBearerTokenValidator validator, RoleService roleService, UserRoleService userRoleService) {
        this(validator, roleService, userRoleService, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ScimGroupController(ScimBearerTokenValidator validator, RoleService roleService, UserRoleService userRoleService,
                               com.aether.sys.service.UserService userService) {
        this.validator = validator;
        this.roleService = roleService;
        this.userRoleService = userRoleService;
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestHeader(value = "Authorization", required = false) String authorization,
                                  @RequestParam String tenantId,
                                  @RequestParam(defaultValue = "1") long startIndex,
                                  @RequestParam(defaultValue = "100") long count,
                                  @RequestParam(required = false) String filter) {
        if (!validator.isValid(authorization)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!validator.isTenantAllowed(tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        String displayName = exactDisplayName(filter);
        if (filter != null && !filter.trim().isEmpty() && displayName == null)
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Unsupported SCIM filter");
        long pageSize = Math.max(1L, Math.min(count, 200L));
        long safeStart = Math.max(1L, startIndex);
        long pageNo = ((safeStart - 1L) / pageSize) + 1L;
        Page<Role> page = roleService.page(new Page<Role>(pageNo, pageSize), Wrappers.lambdaQuery(Role.class)
                .eq(Role::getTenantId, tenantId).eq(displayName != null, Role::getName, displayName)
                .eq(Role::getDeleted, false).orderByAsc(Role::getId));
        List<Map<String, Object>> resources = new ArrayList<Map<String, Object>>();
        for (Role role : page.getRecords()) {
            Map<String, Object> group = new LinkedHashMap<String, Object>();
            group.put("schemas", new String[]{"urn:ietf:params:scim:schemas:core:2.0:Group"});
            group.put("id", role.getId());
            group.put("displayName", role.getName());
            group.put("active", role.getState() != null && role.getState() == 1);
            List<Map<String, String>> members = new ArrayList<Map<String, String>>();
            for (UserRole relation : userRoleService.list(Wrappers.lambdaQuery(UserRole.class)
                    .eq(UserRole::getTenantId, tenantId).eq(UserRole::getRoleId, role.getId()).eq(UserRole::getDeleted, false))) {
                Map<String, String> member = new LinkedHashMap<String, String>();
                member.put("value", relation.getUserId());
                members.add(member);
            }
            group.put("members", members);
            resources.add(group);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("schemas", new String[]{"urn:ietf:params:scim:api:messages:2.0:ListResponse"});
        result.put("totalResults", page.getTotal());
        result.put("startIndex", safeStart);
        result.put("itemsPerPage", resources.size());
        result.put("Resources", resources);
        return ResponseEntity.ok(result);
    }

    private String exactDisplayName(String filter) {
        if (filter == null || filter.trim().isEmpty()) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "^\\s*displayName\\s+eq\\s+\\\"([^\\\"]{1,128})\\\"\\s*$",
                java.util.regex.Pattern.CASE_INSENSITIVE).matcher(filter);
        return matcher.matches() ? matcher.group(1) : null;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@RequestHeader(value = "Authorization", required = false) String authorization,
                                 @RequestParam String tenantId, @PathVariable String id) {
        if (!validator.isValid(authorization)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!validator.isTenantAllowed(tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        Role role = roleService.getOne(Wrappers.<Role>query().eq("id", id).eq("tenant_id", tenantId)
                .eq("deleted", false), false);
        if (role == null) return ResponseEntity.notFound().build();
        Map<String, Object> group = new LinkedHashMap<String, Object>();
        group.put("schemas", new String[]{"urn:ietf:params:scim:schemas:core:2.0:Group"});
        group.put("id", role.getId());
        group.put("displayName", role.getName());
        group.put("active", role.getState() != null && role.getState() == 1);
        List<Map<String, String>> members = new ArrayList<Map<String, String>>();
        for (UserRole relation : userRoleService.list(Wrappers.lambdaQuery(UserRole.class)
                .eq(UserRole::getTenantId, tenantId).eq(UserRole::getRoleId, role.getId()).eq(UserRole::getDeleted, false))) {
            Map<String, String> member = new LinkedHashMap<String, String>();
            member.put("value", relation.getUserId());
            members.add(member);
        }
        group.put("members", members);
        return ResponseEntity.ok(group);
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    @PostMapping
    public ResponseEntity<?> create(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestParam String tenantId, @RequestBody Map<String, Object> body) {
        if (!validator.isValid(authorization)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!validator.isTenantAllowed(tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        String name = body == null || body.get("displayName") == null ? null : String.valueOf(body.get("displayName"));
        if (name == null || name.trim().isEmpty() || name.length() > 128)
            return ResponseEntity.badRequest().body("displayName is required");
        Role existing = roleService.getOne(Wrappers.<Role>query().eq("tenant_id", tenantId)
                .eq("name", name).eq("deleted", false), false);
        if (existing != null) return ResponseEntity.status(HttpStatus.CONFLICT).body("Group already exists");
        Role role = new Role(); role.setTenantId(tenantId); role.setName(name); role.setDescription("SCIM"); role.setState(1);
        roleService.save(role);
        replaceMembers(role.getId(), tenantId, body == null ? null : body.get("members"));
        return ResponseEntity.status(HttpStatus.CREATED).body(groupResource(role, tenantId));
    }

    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    @PatchMapping("/{id}")
    public ResponseEntity<?> patch(@RequestHeader(value = "Authorization", required = false) String authorization,
                                   @RequestParam String tenantId, @PathVariable String id,
                                   @RequestBody Map<String, Object> body) {
        if (!validator.isValid(authorization)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!validator.isTenantAllowed(tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        body = normalizePatch(body);
        Role role = roleService.getOne(Wrappers.<Role>query().eq("id", id).eq("tenant_id", tenantId).eq("deleted", false), false);
        if (role == null) return ResponseEntity.notFound().build();
        if (body != null && body.get("displayName") != null) {
            String name = String.valueOf(body.get("displayName"));
            if (name.trim().isEmpty() || name.length() > 128) return ResponseEntity.badRequest().body("displayName is invalid");
            Role duplicate = roleService.getOne(Wrappers.<Role>query().eq("tenant_id", tenantId)
                    .eq("name", name).eq("deleted", false), false);
            if (duplicate != null && !id.equals(duplicate.getId())) return ResponseEntity.status(HttpStatus.CONFLICT).body("Group already exists");
            role.setName(name);
        }
        if (body != null && body.get("active") != null) role.setState(Boolean.valueOf(String.valueOf(body.get("active"))) ? 1 : 0);
        roleService.updateById(role);
        if (body != null && body.containsKey("members")) replaceMembers(role.getId(), tenantId, body.get("members"));
        return ResponseEntity.ok(groupResource(role, tenantId));
    }

    /** SCIM full resource replacement; the same tenant and member validation as PATCH applies. */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    @PutMapping("/{id}")
    public ResponseEntity<?> put(@RequestHeader(value = "Authorization", required = false) String authorization,
                                 @RequestParam String tenantId, @PathVariable String id,
                                 @RequestBody Map<String, Object> body) {
        if (!validator.isValid(authorization)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!validator.isTenantAllowed(tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        String name = body == null || body.get("displayName") == null ? null : String.valueOf(body.get("displayName"));
        if (name == null || name.trim().isEmpty() || name.length() > 128)
            return ResponseEntity.badRequest().body("displayName is required");
        Role role = roleService.getOne(Wrappers.<Role>query().eq("id", id).eq("tenant_id", tenantId).eq("deleted", false), false);
        if (role == null) return ResponseEntity.notFound().build();
        Role duplicate = roleService.getOne(Wrappers.<Role>query().eq("tenant_id", tenantId)
                .eq("name", name).eq("deleted", false), false);
        if (duplicate != null && !id.equals(duplicate.getId())) return ResponseEntity.status(HttpStatus.CONFLICT).body("Group already exists");
        role.setName(name);
        if (body.containsKey("active")) role.setState(Boolean.valueOf(String.valueOf(body.get("active"))) ? 1 : 0);
        roleService.updateById(role);
        replaceMembers(role.getId(), tenantId, body.get("members"));
        return ResponseEntity.ok(groupResource(role, tenantId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestParam String tenantId, @PathVariable String id) {
        if (!validator.isValid(authorization)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!validator.isTenantAllowed(tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        Role role = roleService.getOne(Wrappers.<Role>query().eq("id", id).eq("tenant_id", tenantId).eq("deleted", false), false);
        if (role == null) return ResponseEntity.notFound().build();
        role.setState(0); roleService.updateById(role);
        return ResponseEntity.noContent().build();
    }

    @SuppressWarnings("unchecked")
    private void replaceMembers(String roleId, String tenantId, Object rawMembers) {
        if (userService == null) return;
        if (rawMembers != null && !(rawMembers instanceof List)) throw new com.aether.exception.ServerException(400, "members must be an array");
        List<?> members = rawMembers == null ? Collections.emptyList() : (List<?>) rawMembers;
        List<String> userIds = new ArrayList<>();
        for (Object raw : members) {
            if (!(raw instanceof Map)) throw new com.aether.exception.ServerException(400, "invalid group member");
            Object value = ((Map<?, ?>) raw).get("value");
            if (value == null || userService.getOne(Wrappers.<User>query().eq("id", String.valueOf(value))
                    .eq("tenant_id", tenantId).eq("deleted", false), false) == null)
                throw new com.aether.exception.ServerException(422, "SCIM 成员不存在或不属于当前租户");
            if (!userIds.contains(String.valueOf(value))) userIds.add(String.valueOf(value));
        }
        userRoleService.remove(Wrappers.<UserRole>query().eq("tenant_id", tenantId).eq("role_id", roleId));
        for (String userId : userIds) {
            UserRole relation = new UserRole(); relation.setTenantId(tenantId); relation.setRoleId(roleId); relation.setUserId(userId);
            userRoleService.save(relation);
        }
    }

    private Map<String, Object> groupResource(Role role, String tenantId) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("schemas", new String[]{"urn:ietf:params:scim:schemas:core:2.0:Group"});
        group.put("id", role.getId()); group.put("displayName", role.getName());
        group.put("active", role.getState() != null && role.getState() == 1);
        List<Map<String, String>> members = new ArrayList<>();
        for (UserRole relation : userRoleService.list(Wrappers.lambdaQuery(UserRole.class)
                .eq(UserRole::getTenantId, tenantId).eq(UserRole::getRoleId, role.getId()).eq(UserRole::getDeleted, false))) {
            Map<String, String> member = new LinkedHashMap<>();
            member.put("value", relation.getUserId());
            members.add(member);
        }
        group.put("members", members);
        return group;
    }

    private Map<String, Object> normalizePatch(Map<String, Object> body) {
        if (body == null || !(body.get("Operations") instanceof List)) return body;
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Object raw : (List<?>) body.get("Operations")) {
            if (!(raw instanceof Map)) continue;
            Map<?, ?> operation = (Map<?, ?>) raw;
            String op = operation.get("op") == null ? "" : String.valueOf(operation.get("op"));
            String path = operation.get("path") == null ? "" : String.valueOf(operation.get("path"));
            if (!("add".equalsIgnoreCase(op) || "replace".equalsIgnoreCase(op) || "remove".equalsIgnoreCase(op))) continue;
            String key = "displayName".equalsIgnoreCase(path) ? "displayName" :
                    ("active".equalsIgnoreCase(path) ? "active" :
                            ("members".equalsIgnoreCase(path) ? "members" : null));
            if (key == null) continue;
            normalized.put(key, "remove".equalsIgnoreCase(op) ? ("active".equals(key) ? false : Collections.emptyList()) : operation.get("value"));
        }
        return normalized;
    }
}
