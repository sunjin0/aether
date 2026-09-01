package com.aether.agent.controller;

import com.aether.agent.skill.dto.AgentArtifactQueryDto;
import com.aether.agent.skill.entity.AgentArtifact;
import com.aether.agent.skill.service.AgentArtifactService;
import com.aether.agent.skill.vo.AgentArtifactVo;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.permission.Permission;
import com.aether.storage.exception.ObjectNotFoundException;
import com.aether.storage.exception.ObjectStorageUnavailableException;
import com.aether.storage.service.ObjectStorageService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 提供智能体Artifact相关的 REST 接口。
 */
@Api(tags = "生成文件 API")
@Validated
@RestController
@Permission(path = "/agent/artifact")
@RequestMapping("/api/agent/artifact")
public class AgentArtifactController {
    private final AgentArtifactService artifactService;
    private final ObjectStorageService objectStorageService;
    private final String artifactBucket;

    /**
     * 创建 {@code AgentArtifactController} 实例。
     */
    public AgentArtifactController(AgentArtifactService artifactService,
                                   ObjectStorageService objectStorageService,
                                   @Value("${artifact.storage.bucket:${MINIO_CHAT_ATTACHMENT_BUCKET:aether-chat}}") String artifactBucket) {
        this.artifactService = artifactService;
        this.objectStorageService = objectStorageService;
        this.artifactBucket = artifactBucket;
    }

    /**
     * 查询我的生成文件。
     */
    @ApiOperation("查询我的生成文件")
    @PostMapping("/list")
    public WebResponse<List<AgentArtifactVo>> list(@RequestBody AgentArtifactQueryDto query) {
        Page<AgentArtifactVo> page = artifactService.pageOwned(currentUserId(), query == null ? new AgentArtifactQueryDto() : query);
        return WebResponse.Page(page.getRecords(), page.getTotal());
    }

    /**
     * 查询本次运行已生成文件。
     */
    @ApiOperation("查询本次运行已生成文件")
    @GetMapping("/run/{runId}")
    public WebResponse<AgentArtifact> byRun(@PathVariable @NotBlank String runId) {
        String tenantId = currentTenantId();
        AgentArtifact artifact = artifactService.getOne(com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(AgentArtifact.class)
                .eq(AgentArtifact::getRunId, runId).eq(AgentArtifact::getUserId, currentUserId())
                .eq(StringUtils.isNotBlank(tenantId), AgentArtifact::getTenantId, tenantId)
                .isNull(AgentArtifact::getRecycledAt).orderByDesc(AgentArtifact::getCreatedAt).last("limit 1"));
        return WebResponse.OK(artifact);
    }

    /**
     * 预览生成文件。
     */
    @ApiOperation("预览生成文件")
    @GetMapping("/{id}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable @NotBlank String id) {
        return fileResponse(artifactService.requireOwned(id, currentUserId(), false), true);
    }

    /**
     * 下载生成文件。
     */
    @ApiOperation("下载生成文件")
    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable @NotBlank String id) {
        return fileResponse(artifactService.requireOwned(id, currentUserId(), false), false);
    }

    /**
     * 将生成文件移入回收站。
     */
    @ApiOperation("将生成文件移入回收站")
    @Permission(path = "/agent/artifact", type = Permission.Type.Write)
    @DeleteMapping("/{id}")
    public WebResponse<Void> recycle(@PathVariable @NotBlank String id) {
        artifactService.recycle(id, currentUserId());
        return WebResponse.OK(I18nUtils.getMessage("agent.artifact.recycled"));
    }

    /**
     * 恢复生成文件。
     */
    @ApiOperation("恢复生成文件")
    @Permission(path = "/agent/artifact", type = Permission.Type.Write)
    @PostMapping("/{id}/restore")
    public WebResponse<Void> restore(@PathVariable @NotBlank String id) {
        artifactService.restore(id, currentUserId());
        return WebResponse.OK(I18nUtils.getMessage("agent.artifact.restored"));
    }

    /**
     * 当前用户Id。
     */
    private String currentUserId() {
        String userId = CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("userId");
        if (StringUtils.isBlank(userId))
            throw new ServerException(401, I18nUtils.getMessage("agent.artifact.unauthorized"));
        return userId;
    }

    private String currentTenantId() {
        return CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("tenantId");
    }

    /**
     * 文件Response。
     */
    private ResponseEntity<byte[]> fileResponse(AgentArtifact artifact, boolean inline) {
        try {
            byte[] content = objectStorageService.getObject(artifactBucket, artifact.getObjectKey());
            ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                    .filename(artifact.getFileName(), StandardCharsets.UTF_8).build();
            return ResponseEntity.ok().cacheControl(CacheControl.noCache())
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .contentType(mediaType(artifact.getContentType(), artifact.getFileName())).contentLength(content.length).body(content);
        } catch (ObjectNotFoundException e) {
            throw new ServerException(404, I18nUtils.getMessage("agent.artifact.file-not-found"));
        } catch (ObjectStorageUnavailableException e) {
            throw new ServerException(503, I18nUtils.getMessage("file.storage.unavailable"));
        }
    }

    /**
     * 处理mediaType。
     */
    private MediaType mediaType(String contentType, String fileName) {
        try {
            if (StringUtils.isNotBlank(contentType)) return MediaType.parseMediaType(contentType);
            return MediaTypeFactory.getMediaType(fileName).orElse(MediaType.APPLICATION_OCTET_STREAM);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
