package com.aether.sys.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Standard SCIM Bulk facade, reusing the tenant-scoped Users/Groups handlers. */
@RestController
@RequestMapping("${aether.identity.scim.base-path:/scim/v2}")
public class ScimBulkController {
    private static final int MAX_OPERATIONS = 100;
    private final ScimUserController users;
    private final ScimGroupController groups;

    public ScimBulkController(ScimUserController users, ScimGroupController groups) {
        this.users = users;
        this.groups = groups;
    }

    @PostMapping("/Bulk")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> bulk(@RequestHeader(value = "Authorization", required = false) String authorization,
                                  @RequestParam String tenantId, @RequestBody Map<String, Object> body) {
        Object raw = body == null ? null : body.get("Operations");
        if (!(raw instanceof List) || ((List<?>) raw).isEmpty() || ((List<?>) raw).size() > MAX_OPERATIONS)
            return ResponseEntity.badRequest().body("Operations must contain 1-100 items");
        int failOnErrors = 0;
        if (body.get("failOnErrors") != null) {
            try {
                failOnErrors = Integer.parseInt(String.valueOf(body.get("failOnErrors")));
            } catch (NumberFormatException ignored) {
                return ResponseEntity.badRequest().body("failOnErrors must be a non-negative integer");
            }
            if (failOnErrors < 0) return ResponseEntity.badRequest().body("failOnErrors must be a non-negative integer");
        }
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        int errors = 0;
        for (Object item : (List<?>) raw) {
            if (!(item instanceof Map)) return ResponseEntity.badRequest().body("Invalid SCIM bulk operation");
            Map<?, ?> operation = (Map<?, ?>) item;
            String method = string(operation.get("method"));
            String path = string(operation.get("path"));
            if (method == null || path == null) return ResponseEntity.badRequest().body("method and path are required");
            ResponseEntity<?> result = dispatch(authorization, tenantId, method, path, operation.get("data"));
            Map<String, Object> entry = new LinkedHashMap<String, Object>();
            entry.put("method", method.toUpperCase());
            entry.put("path", path);
            entry.put("status", String.valueOf(result.getStatusCodeValue()));
            if (result.getBody() != null) entry.put("response", result.getBody());
            results.add(entry);
            if (result.getStatusCodeValue() >= 400 && ++errors >= failOnErrors && failOnErrors > 0) break;
        }
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("schemas", new String[]{"urn:ietf:params:scim:api:messages:2.0:BulkResponse"});
        response.put("Operations", results);
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<?> dispatch(String authorization, String tenantId, String method, String path, Object rawData) {
        if (path.length() > 256 || path.indexOf('?') >= 0 || path.indexOf('#') >= 0 || path.contains(".."))
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid SCIM bulk path");
        String[] parts = path.replaceFirst("^/", "").split("/");
        if (parts.length < 1 || !("Users".equals(parts[0]) || "Groups".equals(parts[0])) || parts.length > 2)
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Unsupported SCIM bulk path");
        Map<String, Object> data = rawData instanceof Map ? new LinkedHashMap<String, Object>((Map<String, Object>) rawData) : new LinkedHashMap<String, Object>();
        boolean user = "Users".equals(parts[0]);
        String id = parts.length == 2 ? parts[1] : null;
        if (id != null && (id.length() > 128 || !id.matches("[A-Za-z0-9_-]+")))
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid SCIM resource id");
        if ("POST".equalsIgnoreCase(method) && id == null)
            return user ? users.create(authorization, tenantId, data) : groups.create(authorization, tenantId, data);
        if ("PATCH".equalsIgnoreCase(method) && id != null)
            return user ? users.patch(authorization, tenantId, id, data) : groups.patch(authorization, tenantId, id, data);
        if ("PUT".equalsIgnoreCase(method) && id != null)
            return user ? users.put(authorization, tenantId, id, data) : groups.put(authorization, tenantId, id, data);
        if ("DELETE".equalsIgnoreCase(method) && id != null)
            return user ? users.delete(authorization, tenantId, id) : groups.delete(authorization, tenantId, id);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Unsupported SCIM bulk operation");
    }

    private String string(Object value) {
        if (value == null || String.valueOf(value).trim().isEmpty()) return null;
        return String.valueOf(value).trim();
    }
}
