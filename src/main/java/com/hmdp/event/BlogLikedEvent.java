package com.hmdp.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlogLikedEvent {
    private Long blogId;
    private Long authorId;
    private Long userId;
    private Double score;
    private Boolean action;

}
