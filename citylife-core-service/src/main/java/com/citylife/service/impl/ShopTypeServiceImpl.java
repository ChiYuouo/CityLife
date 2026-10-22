package com.citylife.service.impl;

import com.citylife.entity.ShopType;
import com.citylife.mapper.ShopTypeMapper;
import com.citylife.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * @description: 商铺类型 接口实现
 **/
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

}
