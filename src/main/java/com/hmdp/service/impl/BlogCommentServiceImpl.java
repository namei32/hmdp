package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.BlogComment;
import com.hmdp.mapper.BlogCommentMapper;
import com.hmdp.service.IBlogCommentService;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogCommentServiceImpl extends ServiceImpl<BlogCommentMapper, BlogComment> implements IBlogCommentService {

}
