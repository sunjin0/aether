package com.aether.sys.mapper;

import com.aether.sys.entity.AdminPreference;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 提供管理员偏好映的数据访问能力。
 */
@Mapper
public interface AdminPreferenceMapper extends BaseMapper<AdminPreference> {

    /**
     * 处理selectEffectivePreferences。
     */
    @Select("SELECT * FROM sys_admin_preference WHERE admin_id = #{adminId} AND deleted = false AND status = 1 ORDER BY effective_score DESC")
    List<AdminPreference> selectEffectivePreferences(@Param("adminId") String adminId);

    /**
     * 处理select按Key。
     */
    @Select("SELECT * FROM sys_admin_preference WHERE admin_id = #{adminId} AND key_name = #{keyName} AND deleted = false LIMIT 1")
    AdminPreference selectByKey(@Param("adminId") String adminId, @Param("keyName") String keyName);

    /**
     * 处理select按Identity。
     */
    @Select("SELECT * FROM sys_admin_preference WHERE admin_id = #{adminId} AND category = #{category} "
            + "AND key_name = #{keyName} AND scope = #{scope} AND COALESCE(scope_detail, '') = #{scopeDetail} "
            + "AND deleted = false LIMIT 1")
    AdminPreference selectByIdentity(@Param("adminId") String adminId,
                                     @Param("category") String category,
                                     @Param("keyName") String keyName,
                                     @Param("scope") String scope,
                                     @Param("scopeDetail") String scopeDetail);

    /**
     * 统计Duplicate。
     */
    @Select("SELECT COUNT(*) FROM sys_admin_preference WHERE admin_id = #{adminId} AND key_name = #{keyName} AND value = #{value} AND deleted = false")
    int countDuplicate(@Param("adminId") String adminId, @Param("keyName") String keyName, @Param("value") String value);

    /**
     * 更新EffectiveScore。
     */
    @Update("UPDATE sys_admin_preference SET effective_score = #{score} WHERE id = #{id} AND deleted = false")
    int updateEffectiveScore(@Param("id") String id, @Param("score") BigDecimal score);
}
