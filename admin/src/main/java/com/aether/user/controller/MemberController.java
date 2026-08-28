package com.aether.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aether.user.vo.MemberVo;
import com.aether.user.dto.MemberRequests;
import com.aether.user.entity.Member;
import com.aether.entity.WebResponse;
import com.aether.user.service.MemberService;
import com.aether.permission.Permission;
import com.aether.i18n.I18nUtils;
import org.springframework.web.bind.annotation.*;
import io.swagger.annotations.*;
import org.springframework.beans.BeanUtils;

import java.util.List;


/**
 * 提供Member相关的 REST 接口。
 */
@Api(tags = "控制器")
@RestController
@RequestMapping("/api/user/member")
public class MemberController {

    private final MemberService memberService;

    /**
     * 创建 {@code MemberController} 实例。
     */
    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    /**
     * 查询所有
     */
    @ApiOperation("查询所有")
    @Permission(path = "/user/member")
    @PostMapping("/list")
    public WebResponse<List<Member>> list(@RequestBody MemberRequests.ListRequest request) {
        MemberVo entity = new MemberVo();
        BeanUtils.copyProperties(request, entity);
        Page<Member> page = new Page<>(entity.getCurrent(), entity.getPageSize());
        LambdaQueryWrapper<Member> wrapper = Wrappers.lambdaQuery(Member.class);
        Page<Member> MemberPage = memberService.page(page, wrapper);
        return WebResponse.Page(MemberPage.getRecords(), MemberPage.getTotal());
    }

    /**
     * 新增
     */
    @ApiOperation("新增")
    @Permission(path = "/user/member", type = Permission.Type.Write)
    @PostMapping("/add")
    public WebResponse<Boolean> add(@RequestBody MemberRequests.SaveRequest request) {
        Member entity = new Member();
        BeanUtils.copyProperties(request, entity);
        Boolean save = memberService.save(entity);
        return WebResponse.OK(I18nUtils.getMessage(save ? "member.create.success" : "member.create.fail"), save);
    }

    /**
     * 修改
     */
    @ApiOperation("修改")
    @Permission(path = "/user/member", type = Permission.Type.Write)
    @PostMapping("/update")
    public WebResponse<Boolean> update(@RequestBody MemberRequests.SaveRequest request) {
        Member entity = new Member();
        BeanUtils.copyProperties(request, entity);
        Boolean update = memberService.updateById(entity);
        return WebResponse.OK(I18nUtils.getMessage(update ? "member.update.success" : "member.update.fail"), update);
    }

    /**
     * 删除
     */
    @ApiOperation("删除")
    @Permission(path = "/user/member", type = Permission.Type.Write)
    @GetMapping("/delete")
    public WebResponse<Boolean> delete(@RequestParam String id) {
        boolean removed = memberService.removeById(id);
        return WebResponse.OK(I18nUtils.getMessage(removed ? "member.delete.success" : "member.delete.fail"), removed);
    }

    /**
     * 查询
     */
    @ApiOperation("查询")
    @Permission(path = "/user/member")
    @GetMapping("/info")
    public WebResponse<Member> info(@RequestParam String id) {
        return WebResponse.OK(memberService.getById(id));
    }

}
