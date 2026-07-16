package com.aether.sys.controller;

import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.permission.Permission;
import com.aether.sys.entity.AdminPreference;
import com.aether.sys.service.AdminPreferenceService;
import com.aether.sys.vo.AdminPreferenceVo;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

@Api(tags = "用户偏好 API")
@Validated
@RestController
@Permission(path = "/sys/admin/preference")
@RequestMapping("/api/sys/admin/preference")
public class AdminPreferenceController {

    private final AdminPreferenceService adminPreferenceService;

    public AdminPreferenceController(AdminPreferenceService adminPreferenceService) {
        this.adminPreferenceService = adminPreferenceService;
    }

    @ApiOperation("用户偏好列表")
    @PostMapping("/list")
    public WebResponse<List<AdminPreferenceVo>> list(@RequestBody AdminPreferenceVo vo) {
        Page<AdminPreference> page = new Page<>(vo.getCurrent(), vo.getPageSize());
        String adminId = StringUtils.defaultIfBlank(vo.getAdminId(), currentAdminId());
        Wrapper<AdminPreference> wrapper = Wrappers.lambdaQuery(AdminPreference.class)
                .eq(StringUtils.isNotBlank(adminId), AdminPreference::getAdminId, adminId)
                .like(StringUtils.isNotBlank(vo.getCategory()), AdminPreference::getCategory, vo.getCategory())
                .like(StringUtils.isNotBlank(vo.getContent()), AdminPreference::getContent, vo.getContent())
                .eq(vo.getStatus() != null, AdminPreference::getStatus, vo.getStatus())
                .eq(AdminPreference::getDeleted, false)
                .orderByDesc(AdminPreference::getUpdatedAt);
        Page<AdminPreference> result = adminPreferenceService.page(page, wrapper);
        List<AdminPreferenceVo> list = result.getRecords().stream().map(item -> {
            AdminPreferenceVo itemVo = new AdminPreferenceVo();
            BeanUtils.copyProperties(item, itemVo);
            return itemVo;
        }).collect(Collectors.toList());
        return WebResponse.Page(list, result.getTotal());
    }

    @ApiOperation("用户偏好详情")
    @GetMapping("/{id}")
    public WebResponse<AdminPreferenceVo> detail(@PathVariable @NotBlank String id) {
        AdminPreference preference = getExisting(id);
        AdminPreferenceVo vo = new AdminPreferenceVo();
        BeanUtils.copyProperties(preference, vo);
        return WebResponse.OK(vo);
    }

    @ApiOperation("新增用户偏好")
    @Permission(path = "/sys/admin/preference", type = Permission.Type.Write)
    @PostMapping
    public WebResponse<String> save(@RequestBody AdminPreferenceVo vo) {
        AdminPreference preference = new AdminPreference();
        BeanUtils.copyProperties(vo, preference);
        if (StringUtils.isBlank(preference.getAdminId())) {
            preference.setAdminId(currentAdminId());
        }
        if (preference.getStatus() == null) {
            preference.setStatus(1);
        }
        boolean saved = adminPreferenceService.save(preference);
        return WebResponse.OK(saved ? I18nUtils.getMessage("add.success") : I18nUtils.getMessage("add.fail"), preference.getId());
    }

    @ApiOperation("编辑用户偏好")
    @Permission(path = "/sys/admin/preference", type = Permission.Type.Write)
    @PutMapping("/{id}")
    public WebResponse<Void> update(@PathVariable @NotBlank String id, @RequestBody AdminPreferenceVo vo) {
        getExisting(id);
        AdminPreference preference = new AdminPreference();
        BeanUtils.copyProperties(vo, preference);
        preference.setId(id);
        boolean updated = adminPreferenceService.updateById(preference);
        return WebResponse.OK(updated ? I18nUtils.getMessage("update.success") : I18nUtils.getMessage("update.fail"));
    }

    @ApiOperation("删除用户偏好")
    @Permission(path = "/sys/admin/preference", type = Permission.Type.Write)
    @DeleteMapping("/{id}")
    public WebResponse<Void> delete(@PathVariable @NotBlank String id) {
        boolean removed = adminPreferenceService.removeById(id);
        return WebResponse.OK(removed ? I18nUtils.getMessage("delete.success") : I18nUtils.getMessage("delete.fail"));
    }

    @ApiOperation("启用/禁用用户偏好")
    @Permission(path = "/sys/admin/preference", type = Permission.Type.Write)
    @PutMapping("/{id}/status")
    public WebResponse<Void> updateStatus(@PathVariable @NotBlank String id, @RequestBody AdminPreferenceVo vo) {
        AdminPreference preference = new AdminPreference();
        preference.setId(id);
        preference.setStatus(vo.getStatus());
        boolean updated = adminPreferenceService.updateById(preference);
        return WebResponse.OK(updated ? I18nUtils.getMessage("update.success") : I18nUtils.getMessage("update.fail"));
    }

    private AdminPreference getExisting(String id) {
        AdminPreference preference = adminPreferenceService.getById(id);
        if (preference == null || Boolean.TRUE.equals(preference.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("resource.not.found"));
        }
        return preference;
    }

    private String currentAdminId() {
        HashMap<String, String> user = CurrentUser.getUser();
        return user == null ? null : user.get("userId");
    }
}
