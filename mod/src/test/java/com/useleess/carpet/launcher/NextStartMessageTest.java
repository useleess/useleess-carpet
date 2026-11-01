package com.useleess.carpet.launcher;

import com.useleess.carpet.core.Messages;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The "from now on start the server with" line: a start script is run as itself, only a jar needs java -jar. */
class NextStartMessageTest {
    @Test void jarIsStartedWithJavaAndScriptOnItsOwn() {
        Messages m = Messages.load();
        assertEquals("From now on start the server with:  java -jar server.jar nogui", m.get("install.next_start", "server.jar"));
        assertEquals("From now on start the server with:  start-useleess-carpet.bat", m.get("install.next_start_script", "start-useleess-carpet.bat"));
    }
}
