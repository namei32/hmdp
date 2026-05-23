package com.hmdp.controller;

import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

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
    private final IFollowService followService;

    public FollowController(IFollowService followService) {
        this.followService = followService;
    }

    @PutMapping("/{id}/{isfollow}")
    public Result follow(@PathVariable Long id, @PathVariable("isfollow") Boolean isFollow) {
        return followService.follow(id, isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollowed(@PathVariable Long id) {
        return followService.isFollowed(id);
    }

    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable Long id) {
        return followService.followCommons(id);
    }
}
