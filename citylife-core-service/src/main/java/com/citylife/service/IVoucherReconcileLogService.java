package com.citylife.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.citylife.dto.VoucherReconcileLogDto;
import com.citylife.entity.VoucherReconcileLog;
import com.citylife.kafka.message.SeckillVoucherMessage;
import com.citylife.message.MessageExtend;

/**
 * @description: 对账日志 接口
 **/
public interface IVoucherReconcileLogService extends IService<VoucherReconcileLog> {
    
    boolean saveReconcileLog(Integer logType,
                             Integer businessType,
                             String detail,
                             MessageExtend<SeckillVoucherMessage> message);
    
    boolean saveReconcileLog(Integer logType,
                             Integer businessType,
                             String detail,
                             Long traceId,
                             MessageExtend<SeckillVoucherMessage> message);
    
    
    boolean saveReconcileLog(VoucherReconcileLogDto voucherReconcileLogDto);
}