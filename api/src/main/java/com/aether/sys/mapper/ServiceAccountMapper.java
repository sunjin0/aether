package com.aether.sys.mapper;

import com.aether.sys.entity.ServiceAccount;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 提供服务账户映的数据访问能力。
 */
@Mapper
public interface ServiceAccountMapper extends BaseMapper<ServiceAccount> {

    /**
     * 处理physical删除按Id。
     */
    @Delete("DELETE FROM sys_service_account WHERE id = #{id}")
    int physicalDeleteById(@Param("id") String id);
}
