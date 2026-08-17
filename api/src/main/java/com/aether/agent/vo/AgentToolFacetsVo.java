package com.aether.agent.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具中心筛选聚合数据。
 */
@Data
public class AgentToolFacetsVo {

    private List<Item> categories = new ArrayList<>();
    private List<Item> statuses = new ArrayList<>();
    private List<Item> sources = new ArrayList<>();

    /**
     * 表示Item。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Object value;
        private String label;
        private long count;
    }
}
