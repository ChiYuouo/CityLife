package com.citylife.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.citylife.entity.RollbackFailureLog;
import com.citylife.mapper.RollbackFailureLogMapper;
import com.citylife.service.IRollbackFailureLogService;
import org.springframework.stereotype.Service;

/**
 * @description: 回滚失败日志 接口实现
 **/
@Service
public class RollbackFailureLogServiceImpl extends ServiceImpl<RollbackFailureLogMapper, RollbackFailureLog>
        implements IRollbackFailureLogService {
}