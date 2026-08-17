package com.aether.sys.vo;

import com.aether.sys.entity.Dict;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 表示DictVO。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class DictVo extends Dict {
    private String key;
    private List<DictVo> children;
    private Long current;
    private Long pageSize;
}
