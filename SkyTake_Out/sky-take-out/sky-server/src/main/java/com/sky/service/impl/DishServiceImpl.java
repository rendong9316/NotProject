package com.sky.service.impl;


import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class DishServiceImpl implements DishService {

    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private DishFlavorMapper dishFlavorMapper;
    @Autowired
    private SetmealMapper setmealMapper;


    /**
     * 新增菜品和口味
     * @param dishDTO
     */
    @Transactional   //事务注解：让方法原子性，涉及夺标插入数据，必须要么全成功要么全失败
    public void saveWithFlavor(DishDTO dishDTO) {

        //插入一条菜品数据
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO,dish);
        dishMapper.insert(dish);
        //获取insert语句生成的主键值
        Long dishId = dish.getId();
        //插入n条口味数据
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if(flavors!=null&&flavors.size()>0){
            for (DishFlavor dishFlavor : flavors) {
                dishFlavor.setDishId(dishId);
            }
            dishFlavorMapper.insertbatch(flavors);
        }

    }

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        PageHelper.startPage(dishPageQueryDTO.getPage(),dishPageQueryDTO.getPageSize());
        Page<DishVO> page=dishMapper.pageQuery(dishPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 菜品批量删除
     * @param ids
     */
    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        // 起售中的菜品不能删除：一条SQL查出IDs里status=1的数量，>0直接拦截
        Integer onSaleCount = dishMapper.countByIdsAndStatus(ids, StatusConstant.ENABLE);
        if (onSaleCount > 0) {
            throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
        }

        // 菜品被套餐关联不能删除：查setmeal_dish表中引用了这些dish_id的记录数
        Integer relatedSetmealCount = setmealMapper.countByDishIds(ids);
        if (relatedSetmealCount > 0) {
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }

        // 先删口味，再删菜品，保持数据一致性
        dishFlavorMapper.deleteByDishIds(ids);
        dishMapper.deleteByIds(ids);
    }


    /**
     * 根据id查询菜品用来回显
     * @param id
     * @return
     */
    @Override
    public DishVO getByIdWithFlavor(Long id) {
        // 查菜品基本信息（含分类名称）
        DishVO dishVO = dishMapper.getById(id);
        // 查口味列表并组装到 VO
        List<DishFlavor> flavors = dishFlavorMapper.getByDishId(id);
        dishVO.setFlavors(flavors);
        return dishVO;
    }

    /**
     * 更新菜品及口味
     * @param dishDTO
     */
    @Override
    @Transactional
    public void updateWithFlavor(DishDTO dishDTO) {
        // 更新菜品基本信息
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        dishMapper.update(dish);

        // 口味整体替换：先删旧口味，再插新口味
        dishFlavorMapper.deleteByDishIds(Arrays.asList(dishDTO.getId()));
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && !flavors.isEmpty()) {
            for (DishFlavor dishFlavor : flavors) {
                dishFlavor.setDishId(dishDTO.getId());
            }
            dishFlavorMapper.insertbatch(flavors);
        }
    }
}
