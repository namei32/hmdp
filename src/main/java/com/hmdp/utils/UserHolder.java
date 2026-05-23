package com.hmdp.utils;

import com.hmdp.dto.UserDTO;

public final class UserHolder {
    private static final ThreadLocal<UserDTO> USER_THREAD_LOCAL = new ThreadLocal<>();

    private UserHolder() {
    }

    public static void saveUser(UserDTO userDto) {
        USER_THREAD_LOCAL.set(userDto);
    }

    public static UserDTO getUser() {
        return USER_THREAD_LOCAL.get();
    }

    public static void removeUser() {
        USER_THREAD_LOCAL.remove();
    }
}
