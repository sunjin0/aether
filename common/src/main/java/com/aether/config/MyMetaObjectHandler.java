package com.aether.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.aether.local.CurrentUser;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/**
 * mybatis参数自动填充处理程序
 *
 * @author sun
 * @since 2024/10/08
 */
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /**
 * 处理insertFill。
 */
@Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "state", () -> 0, Integer.class);
        this.strictInsertFill(metaObject, "deleted", () -> false, Boolean.class);
        this.strictInsertFill(metaObject, "createdAt", System::currentTimeMillis, Long.class);
        this.strictInsertFill(metaObject, "updatedAt", System::currentTimeMillis, Long.class);
        this.strictInsertFill(metaObject, "sortNum", () -> 1, Integer.class);
        String userId = currentUserId();
        if (userId != null && !userId.isEmpty()) {
            // 账号审计字段由持久化插件统一接管，忽略请求体或业务对象中携带的值，避免越权伪造归属。
            this.setFieldValByName("createdBy", userId, metaObject);
            this.setFieldValByName("updatedBy", userId, metaObject);
        }
    }

    /**
 * 更新Fill。
 */
@Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", System::currentTimeMillis, Long.class);
        String userId = currentUserId();
        if (userId != null && !userId.isEmpty()) {
            // 更新人始终取当前认证账号，不依赖业务层逐个设置。
            this.setFieldValByName("updatedBy", userId, metaObject);
        }
    }

    private String currentUserId() {
        return CurrentUser.userId();
    }
}
