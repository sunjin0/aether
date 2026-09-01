package com.aether.sys.controller;

import com.aether.sys.service.ScimBearerTokenValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/** SCIM capability discovery for the tenant-bound provisioning surface. */
@RestController
@RequestMapping("${aether.identity.scim.base-path:/scim/v2}")
public class ScimConfigurationController {
    private final ScimBearerTokenValidator validator;

    public ScimConfigurationController(ScimBearerTokenValidator validator) {
        this.validator = validator;
    }

    @GetMapping("/ServiceProviderConfig")
    public ResponseEntity<?> serviceProviderConfig(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!validator.isValid(authorization)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("schemas", new String[]{"urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"});
        result.put("patch", capability(true));
        result.put("bulk", capability(true));
        result.put("filter", capability(true));
        result.put("changePassword", capability(false));
        result.put("sort", capability(false));
        result.put("etag", capability(false));
        Map<String, Object> auth = new LinkedHashMap<String, Object>();
        auth.put("type", "oauthbearertoken");
        auth.put("name", "Bearer Token");
        auth.put("description", "Dedicated SCIM bearer token");
        auth.put("primary", true);
        result.put("authenticationSchemes", new Object[]{auth});
        return ResponseEntity.ok(result);
    }

    @GetMapping("/ResourceTypes")
    public ResponseEntity<?> resourceTypes(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!validator.isValid(authorization)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        List<Map<String, Object>> resources = new ArrayList<Map<String, Object>>();
        resources.add(resourceType("User", "Users", "urn:ietf:params:scim:schemas:core:2.0:User"));
        resources.add(resourceType("Group", "Groups", "urn:ietf:params:scim:schemas:core:2.0:Group"));
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("schemas", new String[]{"urn:ietf:params:scim:api:messages:2.0:ListResponse"});
        result.put("totalResults", resources.size());
        result.put("itemsPerPage", resources.size());
        result.put("startIndex", 1);
        result.put("Resources", resources);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/Schemas")
    public ResponseEntity<?> schemas(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!validator.isValid(authorization)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        List<Map<String, Object>> resources = new ArrayList<Map<String, Object>>();
        resources.add(schema("urn:ietf:params:scim:schemas:core:2.0:User", "User",
                new String[]{"userName", "active", "name.formatted", "emails.value"}));
        resources.add(schema("urn:ietf:params:scim:schemas:core:2.0:Group", "Group",
                new String[]{"displayName", "members.value"}));
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("schemas", new String[]{"urn:ietf:params:scim:api:messages:2.0:ListResponse"});
        result.put("totalResults", resources.size());
        result.put("itemsPerPage", resources.size());
        result.put("startIndex", 1);
        result.put("Resources", resources);
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> schema(String id, String name, String[] attributeNames) {
        Map<String, Object> schema = new LinkedHashMap<String, Object>();
        schema.put("id", id);
        schema.put("name", name);
        schema.put("description", "SCIM core " + name + " resource");
        List<Map<String, Object>> attributes = new ArrayList<Map<String, Object>>();
        for (String attributeName : attributeNames) {
            Map<String, Object> attribute = new LinkedHashMap<String, Object>();
            attribute.put("name", attributeName);
            attribute.put("type", attributeName.contains(".") ? "complex" : "string");
            attribute.put("multiValued", attributeName.startsWith("emails") || attributeName.startsWith("members"));
            attribute.put("mutability", writableAttribute(attributeName) ? "readWrite" : "readOnly");
            attributes.add(attribute);
        }
        schema.put("attributes", attributes);
        return schema;
    }

    private Map<String, Object> resourceType(String name, String endpoint, String schema) {
        Map<String, Object> resource = new LinkedHashMap<String, Object>();
        resource.put("id", name);
        resource.put("name", name);
        resource.put("endpoint", "/" + endpoint);
        resource.put("schema", schema);
        resource.put("meta", capabilityMeta());
        return resource;
    }

    private boolean writableAttribute(String name) {
        return "userName".equals(name) || "active".equals(name) || "emails.value".equals(name)
                || "displayName".equals(name) || "members.value".equals(name);
    }

    private Map<String, Object> capabilityMeta() {
        Map<String, Object> meta = new LinkedHashMap<String, Object>();
        meta.put("resourceType", "ResourceType");
        return meta;
    }

    private Map<String, Object> capability(boolean supported) {
        Map<String, Object> value = new LinkedHashMap<String, Object>();
        value.put("supported", supported);
        return value;
    }
}
