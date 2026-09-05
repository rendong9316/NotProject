package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.AutoFill;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.enumeration.OperationType;
import com.sky.vo.DishVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DishMapper {

    /**
     * 根据分类id查询菜品数量
     * @param categoryId
     * @return
     */
    @Select("select count(id) from dish where category_id = #{categoryId}")
    Integer countByCategoryId(Long categoryId);

    /**
     * 插入菜品数据
     * @param dish
     */
    @AutoFill(value = OperationType.INSERT)
    void insert(Dish dish);

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    Page<DishVO> pageQuery(DishPageQueryDTO dishPageQueryDTO);

    /**
     * 统计指定ID列表中状态为status的菜品数量（用于批量删除前校验）
     * @param ids   菜品ID列表
     * @param status 状态值（1=起售）
     * @return 符合条件的菜品数量
     */
    Integer countByIdsAndStatus(@Param("ids") List<Long> ids, @Param("status") Integer status);

    /**
     * 根据ID批量删除菜品
     * @param ids 菜品ID列表
     */
    void deleteByIds(@Param("ids") List<Long> ids);

}
