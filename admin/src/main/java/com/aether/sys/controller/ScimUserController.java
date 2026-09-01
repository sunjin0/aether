package com.aether.sys.controller;

import com.aether.sys.entity.User;
import com.aether.sys.service.OidcIdentityBindingService;
import com.aether.sys.service.ScimBearerTokenValidator;
import com.aether.sys.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/** SCIM User resource with tenant-scoped provisioning and lifecycle updates. */
@RestController
@RequestMapping("${aether.identity.scim.base-path:/scim/v2}/Users")
public class ScimUserController {
    private final ScimBearerTokenValidator validator;
    private final UserService userService;

    public ScimUserController(ScimBearerTokenValidator validator, UserService userService,
                              OidcIdentityBindingService ignoredBindingService) {
        this.validator = validator;
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
        long pageSize = Math.max(1L, Math.min(count, 200L));
        long safeStart = Math.max(1L, startIndex);
        long pageNo = ((safeStart - 1L) / pageSize) + 1L;
        String userName = exactUserName(filter);
        if (filter != null && !filter.trim().isEmpty() && userName == null)
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Unsupported SCIM filter");
        Page<User> page = userService.page(new Page<User>(pageNo, pageSize), Wrappers.lambdaQuery(User.class)
                .eq(User::getTenantId, tenantId).eq(User::getDeleted, false)
                .eq(userName != null, User::getUsername, userName).orderByAsc(User::getId));
        List<Map<String, Object>> resources = new ArrayList<Map<String, Object>>();
        for (User user : page.getRecords()) {
            Map<String, Object> resource = new LinkedHashMap<String, Object>();
            resource.put("schemas", new String[]{"urn:ietf:params:scim:schemas:core:2.0:User"});
            resource.put("id", user.getId());
            resource.put("userName", user.getUsername());
            resource.put("active", Integer.valueOf(1).equals(user.getState()));
            Map<String, Object> name = new LinkedHashMap<String, Object>();
            name.put("formatted", user.getUsername());
            resource.put("name", name);
            if (user.getEmail() != null) resource.put("emails", new Object[]{email(user.getEmail())});
            resources.add(resource);
        }
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("schemas", new String[]{"urn:ietf:params:scim:api:messages:2.0:ListResponse"});
        response.put("totalResults", page.getTotal());
        response.put("startIndex", safeStart);
        response.put("itemsPerPage", resources.size());
        response.put("Resources", resources);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestParam String tenantId, @RequestBody Map<String, Object> body) {
        if (!validator.isValid(authorization)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!validator.isTenantAllowed(tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        String username = body == null ? null : String.valueOf(body.get("userName"));
        if (StringUtils.isBlank(username) || "null".equals(username) || username.length() > 128)
            return ResponseEntity.badRequest().body("userName is required");
        String email = null;
        Object emails = body.get("emails");
        if (emails instanceof List && !((List<?>) emails).isEmpty() && ((List<?>) emails).get(0) instanceof Map)
            email = String.valueOf(((Map<?, ?>) ((List<?>) emails).get(0)).get("value"));
        boolean active = !Boolean.FALSE.equals(body.get("active"));
        User user = userService.provisionScim(tenantId, username, email, active);
        return ResponseEntity.status(HttpStatus.CREATED).body(resource(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@RequestHeader(value = "Authorization", required = false) String authorization,
                                 @RequestParam String tenantId, @org.springframework.web.bind.annotation.PathVariable String id) {
        if (!validator.isValid(authorization)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!validator.isTenantAllowed(tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        User user = userService.getOne(new QueryWrapper<User>().eq("id", id).eq("tenant_id", tenantId)
                .eq("deleted", false), false);
        if (user == null) return ResponseEntity.notFound().build();
        Map<String, Object> resource = new LinkedHashMap<String, Object>();
        resource.put("schemas", new String[]{"urn:ietf:params:scim:schemas:core:2.0:User"});
        resource.put("id", user.getId());
        resource.put("userName", user.getUsername());
        resource.put("active", Integer.valueOf(1).equals(user.getState()));
        Map<String, Object> name = new LinkedHashMap<String, Object>();
        name.put("formatted", user.getUsername());
        resource.put("name", name);
        if (user.getEmail() != null) resource.put("emails", new Object[]{email(user.getEmail())});
        return ResponseEntity.ok(resource);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> patch(@RequestHeader(value = "Authorization", required = false) String authorization,
                                   @RequestParam String tenantId, @org.springframework.web.bind.annotation.PathVariable String id,
                                   @RequestBody Map<String, Object> body) {
        if (!validator.isValid(authorization)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!validator.isTenantAllowed(tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        body = normalizePatch(body);
        String username = body == null || body.get("userName") == null ? null : String.valueOf(body.get("userName"));
        String email = body == null || body.get("email") == null ? null : String.valueOf(body.get("email"));
        Boolean active = body == null || body.get("active") == null ? null : Boolean.valueOf(String.valueOf(body.get("active")));
        User user = userService.updateScim(tenantId, id, username, email, active);
        return ResponseEntity.ok(resource(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> put(@RequestHeader(value = "Authorization", required = false) String authorization,
                                 @RequestParam String tenantId, @org.springframework.web.bind.annotation.PathVariable String id,
                                 @RequestBody Map<String, Object> body) {
        if (!validator.isValid(authorization)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!validator.isTenantAllowed(tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        String username = body == null || body.get("userName") == null ? null : String.valueOf(body.get("userName"));
        if (StringUtils.isBlank(username) || "null".equals(username) || username.length() > 128)
            return ResponseEntity.badRequest().body("userName is required");
        String email = emailFrom(body == null ? null : body.get("emails"));
        Boolean active = body != null && body.containsKey("active")
                ? Boolean.valueOf(String.valueOf(body.get("active"))) : Boolean.TRUE;
        User user = userService.updateScim(tenantId, id, username, email, active);
        return ResponseEntity.ok(resource(user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestParam String tenantId, @org.springframework.web.bind.annotation.PathVariable String id) {
        if (!validator.isValid(authorization)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!validator.isTenantAllowed(tenantId)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        userService.updateScim(tenantId, id, null, null, false);
        return ResponseEntity.noContent().build();
    }

    private String exactUserName(String filter) {
        if (filter == null || filter.trim().isEmpty()) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^\\s*userName\\s+eq\\s+\\\"([^\\\"]{1,128})\\\"\\s*$", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(filter);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private Map<String, Object> email(String value) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("value", value);
        result.put("primary", true);
        return result;
    }

    private String emailFrom(Object rawEmails) {
        if (rawEmails instanceof List && !((List<?>) rawEmails).isEmpty() && ((List<?>) rawEmails).get(0) instanceof Map)
            return String.valueOf(((Map<?, ?>) ((List<?>) rawEmails).get(0)).get("value"));
        if (rawEmails instanceof Object[] && ((Object[]) rawEmails).length > 0 && ((Object[]) rawEmails)[0] instanceof Map)
            return String.valueOf(((Map<?, ?>) ((Object[]) rawEmails)[0]).get("value"));
        return null;
    }

    private Map<String, Object> resource(User user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemas", new String[]{"urn:ietf:params:scim:schemas:core:2.0:User"});
        result.put("id", user.getId()); result.put("userName", user.getUsername());
        result.put("active", Integer.valueOf(1).equals(user.getState()));
        if (user.getEmail() != null) result.put("emails", new Object[]{email(user.getEmail())});
        return result;
    }

    private Map<String, Object> normalizePatch(Map<String, Object> body) {
        if (body == null || !(body.get("Operations") instanceof List)) return body;
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Object raw : (List<?>) body.get("Operations")) {
            if (!(raw instanceof Map)) continue;
            Map<?, ?> operation = (Map<?, ?>) raw;
            String op = operation.get("op") == null ? "" : String.valueOf(operation.get("op"));
            String path = operation.get("path") == null ? "" : String.valueOf(operation.get("path"));
            Object value = operation.get("value");
            if (!("add".equalsIgnoreCase(op) || "replace".equalsIgnoreCase(op) || "remove".equalsIgnoreCase(op))) continue;
            String key = path;
            if ("emails.value".equalsIgnoreCase(path) || "emails[type eq \"work\"].value".equalsIgnoreCase(path)) key = "email";
            if ("active".equalsIgnoreCase(key) || "userName".equalsIgnoreCase(key) || "email".equalsIgnoreCase(key))
                normalized.put(key, "remove".equalsIgnoreCase(op) ? ("active".equalsIgnoreCase(key) ? false : null) : value);
        }
        return normalized;
    }
}
