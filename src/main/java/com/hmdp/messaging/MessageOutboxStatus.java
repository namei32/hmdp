package com.hmdp.messaging;

public final class MessageOutboxStatus {
    public static final String INIT = "INIT";
    public static final String SENDING = "SENDING";
    public static final String SENT = "SENT";
    public static final String FAILED = "FAILED";

    private MessageOutboxStatus() {
    }
}
