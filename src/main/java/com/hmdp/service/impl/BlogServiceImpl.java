package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import jodd.util.StringUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service

public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryHotBlog(Integer current) {
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            extracted(blog);
            isBlogLiked(blog);
        });
        return  Result.ok(records);
    }
    public Result queryBlogById(Long id){
        Blog blog=getById(id);
        extracted(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public void likeBlog(Long id) {
        Long userId= UserHolder.getUser().getId();
        String key="blog:liked:"+id;
        Double score=stringRedisTemplate.opsForZSet().score(key,userId.toString());
//        Boolean isMember=stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        if(score!=null){
            boolean isSuccess= update().setSql("liked=liked-1").eq("id", id).update();
            if(isSuccess){
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }else{
           boolean isSuccess= update().setSql("liked=liked+1").eq("id", id).update();
           if(isSuccess){
               stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
           }
        }

    }

    public  void isBlogLiked(Blog blog){
        Long userId=UserHolder.getUser().getId();
        if(userId==null){
            return;
        }
        String key="blog:liked:"+blog.getId();
        Double score=stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score!=null);

    }

    private void extracted(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    public Result queryBlogLikes(Long id){
        String key="blog:liked:"+id;
        Set<String> top5=stringRedisTemplate.opsForZSet().range(key,0,4);

        if(top5==null||top5.size()==0){
            return Result.ok(Collections.emptyList());
        }

        List<Long>ids=top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idstr= StringUtil.join(ids, ",");
        List<UserDTO>userDTOS=userService.query().in("id",ids).last("order by field(id,"+idstr+")").list().stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}
