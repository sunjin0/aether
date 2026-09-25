package com.aether.sys.vo;

import com.aether.sys.entity.User;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.HashMap;
import java.util.List;

/**
 * 表示用户VO。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class UserVo extends User {

    /**
     * 帐户
     */
    private String account;

    /**
     * 旧密码
     */
    private String oldPassword;

    /**
     * 验证码
     */
    private Integer verificationCode;

    /**
     * 令 牌
     */
    private String token;
    /**
     * 刷新令牌
     */
    private String refreshToken;


    /**
     * 角色 ID
     */
    private List<String> roleIds;


    /**
     * 权限映射
     */
    private HashMap<String, Object> permissionMap;

    /**
     * 当前账号绑定角色的稳定类型，仅返回 ADMIN 或 USER。
     * 前端据此决定是否显示跨账号数据筛选项，不能使用角色名称判断。
     */
    private String roleType;

    private Long current;
    private Long pageSize;
}
