package com.citylife.service.impl;

import com.citylife.entity.BlogComments;
import com.citylife.mapper.BlogCommentsMapper;
import com.citylife.service.IBlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * @description: 博客评论接口实现
 **/
@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
