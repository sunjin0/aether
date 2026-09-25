package com.aether.sys.service;

import java.util.List;

/** 统一账号级数据范围服务。 */
public interface AccountDataScopeService {
    String currentUserId();

    boolean isAdministrator();

    /** 返回当前请求允许读取的创建账号；空请求表示当前用户范围。 */
    List<String> readableCreatorIds(String requestedCreatorId);

    void assertReadable(String createdBy);

    void assertWritable(String createdBy);
}
