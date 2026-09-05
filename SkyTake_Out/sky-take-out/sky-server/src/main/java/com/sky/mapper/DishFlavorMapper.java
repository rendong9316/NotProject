package com.sky.mapper;

import com.sky.entity.DishFlavor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DishFlavorMapper {

    /**
     * 批量插入口味数据
     * @param flavors
     */
    void insertbatch(List<DishFlavor> flavors);

    /**
     * 根据菜品ID批量删除口味数据
     * @param dishIds 菜品ID列表
     */
    void deleteByDishIds(@Param("dishIds") List<Long> dishIds);
}
