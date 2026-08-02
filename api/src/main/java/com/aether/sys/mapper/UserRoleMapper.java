package com.aether.sys.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import  com.aether.sys.entity.UserRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
/**
 * <p>
 * 用户角色表 Mapper 接口
 * </p>
 *
 * @author sun
 * @since 2024-11-12
 */
@Mapper
public interface UserRoleMapper extends BaseMapper<UserRole> {

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int physicalDeleteByUserId(@Param("userId") String userId);
}
