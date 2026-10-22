package com.citylife.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import com.citylife.dto.GetVoucherOrderRouterDto;
import com.citylife.entity.VoucherOrderRouter;
import com.citylife.mapper.VoucherOrderRouterMapper;
import com.citylife.service.IVoucherOrderRouterService;
import com.citylife.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * @description: 优惠券订单路由实现 接口
 **/
@Slf4j
@Service
public class VoucherOrderRouterServiceImpl extends ServiceImpl<VoucherOrderRouterMapper, VoucherOrderRouter> implements IVoucherOrderRouterService {
    
    @Override
    public Long get(GetVoucherOrderRouterDto getVoucherOrderRouterDto) {
        VoucherOrderRouter voucherOrderRouter = lambdaQuery()
                .eq(VoucherOrderRouter::getUserId,  UserHolder.getUser().getId())
                .eq(VoucherOrderRouter::getVoucherId, getVoucherOrderRouterDto.getVoucherId())
                .one();
        if (Objects.nonNull(voucherOrderRouter)) {
            return voucherOrderRouter.getOrderId();
        }
        return null;
    }
}
