package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import com.hmdp.service.impl.FollowServiceImpl;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    IFollowService followService;

    @PutMapping("/{id}/{isfollow}")
    public Result follow(@PathVariable Long id, @PathVariable Boolean isfollow) {
        return followService.follow(id,isfollow);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollowed(@PathVariable Long id) {
        return followService.isFollowed(id);
    }

    @GetMapping("/common/{id}")
    public  Result followCommons(@PathVariable Long id){
        return  followService.followCommons(id);
    }
}
