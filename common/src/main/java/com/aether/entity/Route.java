package com.aether.entity;

import lombok.Data;

import java.util.ArrayList;

/**
 * 表示Route。
 */
@Data
public class Route {

    /**
     * 名字
     */
    private String name;
    /**
     * 路径
     */
    private String path;
    /**
     * 图标
     */
    private String icon;
    /**
     * 路线
     */
    private ArrayList<Route> routes;
}
