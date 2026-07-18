package com.aether.sys.mapper;

import com.aether.sys.entity.AdminPreference;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface AdminPreferenceMapper extends BaseMapper<AdminPreference> {

    @Select("SELECT * FROM sys_admin_preference WHERE admin_id = #{adminId} AND deleted = false AND status = 1 ORDER BY effective_score DESC")
    List<AdminPreference> selectEffectivePreferences(@Param("adminId") String adminId);

    @Select("SELECT * FROM sys_admin_preference WHERE admin_id = #{adminId} AND key_name = #{keyName} AND deleted = false LIMIT 1")
    AdminPreference selectByKey(@Param("adminId") String adminId, @Param("keyName") String keyName);

    @Select("SELECT COUNT(*) FROM sys_admin_preference WHERE admin_id = #{adminId} AND key_name = #{keyName} AND value = #{value} AND deleted = false")
    int countDuplicate(@Param("adminId") String adminId, @Param("keyName") String keyName, @Param("value") String value);

    @Update("UPDATE sys_admin_preference SET effective_score = #{score} WHERE id = #{id} AND deleted = false")
    int updateEffectiveScore(@Param("id") String id, @Param("score") BigDecimal score);
}
