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
    private Long current;
    private Long pageSize;
}
