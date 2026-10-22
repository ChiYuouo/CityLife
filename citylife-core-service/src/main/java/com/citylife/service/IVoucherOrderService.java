package com.citylife.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.citylife.dto.CancelVoucherOrderDto;
import com.citylife.dto.GetVoucherOrderByVoucherIdDto;
import com.citylife.dto.GetVoucherOrderDto;
import com.citylife.dto.Result;
import com.citylife.entity.VoucherOrder;
import com.citylife.kafka.message.SeckillVoucherMessage;
import com.citylife.message.MessageExtend;

/**
 * @description: 优惠券订单 接口
 **/
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result<Long> seckillVoucher(Long voucherId);

    void createVoucherOrderV1(VoucherOrder voucherOrder);
    
    boolean createVoucherOrderV2(MessageExtend<SeckillVoucherMessage> message);
    
    Long getSeckillVoucherOrder(GetVoucherOrderDto getVoucherOrderDto);
    
    Boolean cancel(CancelVoucherOrderDto cancelVoucherOrderDto);
    
    boolean autoIssueVoucherToEarliestSubscriber(final Long voucherId, final Long excludeUserId);
    
    Long getSeckillVoucherOrderIdByVoucherId(GetVoucherOrderByVoucherIdDto getVoucherOrderByVoucherIdDto);
}
