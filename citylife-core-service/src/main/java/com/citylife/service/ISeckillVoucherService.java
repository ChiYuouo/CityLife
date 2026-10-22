package com.citylife.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.citylife.entity.SeckillVoucher;
import com.citylife.model.SeckillVoucherFullModel;

/**
 * @description: 秒杀优惠券 接口
 **/
public interface ISeckillVoucherService extends IService<SeckillVoucher> {
    
    SeckillVoucherFullModel queryByVoucherId(Long voucherId);
    
    void loadVoucherStock(Long voucherId);
    
    boolean rollbackStock(Long voucherId);
}
