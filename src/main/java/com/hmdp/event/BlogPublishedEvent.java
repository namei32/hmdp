package com.hmdp.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BlogPublishedEvent {

    private Long blogId;
    private Long authorId;
    private Long publishedAt;
}
