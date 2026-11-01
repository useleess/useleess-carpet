package com.useleess.carpet.server;

import com.useleess.carpet.core.Messages;

/** Everything players see from the server side: players have no mod, so the English text comes from our lang file here. */
public final class ServerText {
    private static final Messages ENGLISH = Messages.english();

    private ServerText() {}

    public static String get(String key, Object... args) {
        return ENGLISH.get(key, args);
    }
}
