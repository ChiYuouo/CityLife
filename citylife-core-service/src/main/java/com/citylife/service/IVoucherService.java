package com.citylife.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.citylife.dto.DelayVoucherReminderDto;
import com.citylife.dto.Result;
import com.citylife.dto.SeckillVoucherDto;
import com.citylife.dto.UpdateSeckillVoucherDto;
import com.citylife.dto.UpdateSeckillVoucherStockDto;
import com.citylife.dto.VoucherDto;
import com.citylife.dto.VoucherSubscribeBatchDto;
import com.citylife.dto.VoucherSubscribeDto;
import com.citylife.entity.Voucher;
import com.citylife.vo.GetSubscribeStatusVo;

import java.util.List;

/**
 * @description: 优惠券 接口
 **/
public interface IVoucherService extends IService<Voucher> {

    Long addVoucher(VoucherDto voucherDto);
    
    Result<List<Voucher>> queryVoucherOfShop(Long shopId);

    Long addSeckillVoucher(SeckillVoucherDto seckillVoucherDto);
    
    void updateSeckillVoucher(UpdateSeckillVoucherDto updateSeckillVoucherDto);
    
    void updateSeckillVoucherStock(UpdateSeckillVoucherStockDto updateSeckillVoucherDto);
    
    void subscribe(VoucherSubscribeDto voucherSubscribeDto);
    
    void unsubscribe(VoucherSubscribeDto voucherSubscribeDto);
    
    Integer getSubscribeStatus(VoucherSubscribeDto voucherSubscribeDto);
    
    List<GetSubscribeStatusVo> getSubscribeStatusBatch(VoucherSubscribeBatchDto voucherSubscribeBatchDto);
    
    void delayVoucherReminder(DelayVoucherReminderDto delayVoucherReminderDto);
}
