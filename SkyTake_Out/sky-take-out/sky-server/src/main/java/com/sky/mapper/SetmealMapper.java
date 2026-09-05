package com.sky.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SetmealMapper {

    /**
     * 根据分类id查询套餐的数量
     * @param id
     * @return
     */
    @Select("select count(id) from setmeal where category_id = #{categoryId}")
    Integer countByCategoryId(Long id);

    /**
     * 统计指定菜品ID列表中被套餐关联的数量（用于菜品批量删除前校验）
     * @param dishIds 菜品ID列表
     * @return 被套餐引用的数量
     */
    Integer countByDishIds(@Param("dishIds") List<Long> dishIds);

}
