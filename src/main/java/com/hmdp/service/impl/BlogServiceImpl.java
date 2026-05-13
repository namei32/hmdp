package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.event.BlogLikedEvent;
import com.hmdp.event.BlogPublishedEvent;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.messaging.BlogEventProducer;
import com.hmdp.messaging.BlogLikedEventProducer;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import jodd.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
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
    @Resource
    private BlogEventProducer blogEventProducer;
    @Resource
    private BlogLikedEventProducer blogLikedEventProducer;
    @Autowired
    private KafkaTemplate<?, ?> kafkaTemplate;

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
    @Transactional
    @Override
    public Result likeBlog(Long id) {
        if(id==null) return Result.fail("笔记id不能为空");
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("博客不存在");
        }
        UserDTO user= UserHolder.getUser();
        if(user==null) return Result.fail("请先登录");
        Long userId= user.getId();
        Long authorId=baseMapper.selectById(id).getUserId();
        String key="blog:liked:"+id;
        Double score=stringRedisTemplate.opsForZSet().score(key,userId.toString());
        if(score==null) {

            boolean success= update().setSql("liked = liked + 1").eq("id", id).update();
            if(!success) {
                throw new RuntimeException("db点赞失败");
            }
            Boolean add = stringRedisTemplate.opsForZSet().add(key,userId.toString(), (double) System.currentTimeMillis());
            if(!Boolean.TRUE.equals(add)) {
                throw new RuntimeException("redis点赞失败");
            }
            blogLikedEventProducer.publishAfterCommit(new BlogLikedEvent(id,authorId,userId, (double) System.currentTimeMillis(),true));
        }else if(score!=null){


            LambdaUpdateWrapper<Blog> updateWrapper=new LambdaUpdateWrapper<>();
            updateWrapper.eq(Blog::getId,id).gt(Blog::getLiked,0).setSql("liked = liked -1");
            int success= baseMapper.update(null,updateWrapper);
            if(success==0) {
                throw new RuntimeException("db取消点赞失败");
            }
            Long removed = stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            if(removed==null||removed==0) {
                throw new RuntimeException("redis取消点赞失败");
            }
            blogLikedEventProducer.publishAfterCommit(new BlogLikedEvent(id,authorId,userId,score,false));
        }
//        Boolean isMember=stringRedisTemplate.opsForSet().isMember(key, userId.toString());
//        blogLikedEventProducer.publishAfterCommit(new BlogLikedEvent(id,authorId,userId,score)

        return Result.ok("点赞成功");
    }

    public  void isBlogLiked(Blog blog){
        UserDTO user=UserHolder.getUser();
        if(user==null||user.getId()==null){
            return;
        }
        Long userId=UserHolder.getUser().getId();
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

    @Override
    @Transactional
    public Result saveBlog(Blog blog) {
        UserDTO user=UserHolder.getUser();
        blog.setUserId(user.getId());
        boolean isSuccess=save(blog);
        if(!isSuccess){
            return Result.fail("新增笔记失败");
        }
        blogEventProducer.publishAfterCommit(new BlogPublishedEvent(
                blog.getId(),
                user.getId(),
                System.currentTimeMillis()));
        return Result.ok(blog);
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        String key = "feed:" + userId;

        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 2);

        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            String value = typedTuple.getValue();
            ids.add(Long.valueOf(value));
            long time = typedTuple.getScore().longValue();
            if (time == minTime) {
                offset++;
            } else {
                minTime = time;
                offset = 1;
            }
        }

        List<Blog>list= query().in("id",ids).last("order by field(id,"+ StringUtil.join(ids,",")+")").list();
        ScrollResult r=new ScrollResult();
        for(Blog blog:list){
            extracted(blog);
            isBlogLiked(blog);
        }
        r.setList(list);
        r.setOffset(offset);
        r.setMinTime(minTime);
        return Result.ok(r);
    }
}
