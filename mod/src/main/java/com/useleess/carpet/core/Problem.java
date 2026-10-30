package com.useleess.carpet.core;

import java.io.IOException;

/** A failure the player sees: a message key from the language file plus its values, never English in code. */
public final class Problem extends IOException {
    public final String key;
    public final Object[] args;

    public Problem(String key, Object... args) {
        super(key);
        this.key = key;
        this.args = args;
    }

    public Problem(Throwable cause, String key, Object... args) {
        super(key, cause);
        this.key = key;
        this.args = args;
    }
}
