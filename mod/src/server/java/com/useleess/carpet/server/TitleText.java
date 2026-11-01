package com.useleess.carpet.server;

/** Status text for the chest title, which is only as wide as the window. Game-free. */
public final class TitleText {
    public static final int MAX = 30;

    private TitleText() {}

    /** The text itself, or its start with "..." when it is longer than {@link #MAX}. */
    public static String fit(String text) {
        return text.length() <= MAX ? text : text.substring(0, MAX - 3) + "...";
    }
}
