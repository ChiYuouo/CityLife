package com.citylife.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.citylife.dto.GetVoucherOrderRouterDto;
import com.citylife.entity.VoucherOrderRouter;

/**
 * @description: 优惠券订单路由 接口
 **/
public interface IVoucherOrderRouterService extends IService<VoucherOrderRouter> {
    
    Long get(GetVoucherOrderRouterDto getVoucherOrderRouterDto);
}
