package com.aether.convert;

import com.aether.exception.ServerException;
import org.modelmapper.ModelMapper;

import java.util.ArrayList;
import java.util.Collection;

/**
 * 转换工具类
 * 主要是将实体对象转换为VO对象
 * 拷贝模式: 浅拷贝
 *
 * @author sun
 * @since 2024/11/28
 */
public class Convert {
    private final static ModelMapper modelMapper = new ModelMapper();

    /**
     * 单转换
     *
     * @param source 源
     * @param clazz  类型
     * @return {@link V }
     */
    public static <V, E> V singleConvert(E source, Class<V> clazz) {
        try {
            return modelMapper.map(source, clazz);
        } catch (Exception e) {
            throw new ServerException(e.getMessage());
        }
    }

    /**
     * 列表转换
     *
     * @param source 源
     * @param clazz  类型
     * @return {@link Collection }<{@link V }>
     */
    public static <V, E> Collection<V> listConvert(Collection<E> source, Class<V> clazz) {
        ArrayList<V> list = new ArrayList<>();
        for (E e : source) {
            list.add(singleConvert(e, clazz));
        }
        return list;
    }
}
