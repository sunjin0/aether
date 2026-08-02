package com.aether.sys.mapper;

import com.aether.sys.entity.ServiceAccount;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ServiceAccountMapper extends BaseMapper<ServiceAccount> {

    @Delete("DELETE FROM sys_service_account WHERE id = #{id}")
    int physicalDeleteById(@Param("id") String id);
}
