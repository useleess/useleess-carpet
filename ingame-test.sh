#!/bin/sh
# Useleess Carpet in-game tests: the launcher jar exactly as players get it, on real 1.12.2 servers (no client).
#
#   ./ingame-test.sh                     all tests
#   FILTER=install/ ./ingame-test.sh     only tests whose id starts with one of these (comma separated)
#
# The launcher jar: $USELEESS_CARPET_JAR, else ingame-jar/useleess-carpet-*.jar (put there for a run on the test
# box, which copies this folder without build/), else mod/build/libs/useleess-carpet-*.jar, else it is built here
# first (Java 21 from the toolchain; the master jar from toolchain/useleess-carpet, see mod/CODE_MAP.md).
# Run on the test box:  "../discord-bot/.venv/Scripts/python.exe" ../discord-bot/testbox.py run . ingame-test.sh
# Prints the SCOPE-TEST protocol and ends with "SCOPE TESTS ALL: P passed, F failed, S skipped"; exit code 0 only
# when nothing failed. Everything happens in the throwaway folder run-ingame (own servers on 127.0.0.1, a free
# port each), deleted at the end. Mojang's jar is downloaded from Mojang once (that is test install/download).
cd "$(dirname "$0")" || exit 2
HERE="$(pwd)"
T="$(cd .. && pwd)/toolchain"
[ -d "$T" ] || T="$(cd ../.. && pwd)/toolchain"
[ -f "$T/test-slot.sh" ] && . "$T/test-slot.sh" && test_slot_take
JAVA="$T/jdk8/jdk8u504-b01/bin/java"
JARTOOL="$T/jdk8/jdk8u504-b01/bin/jar"
for a in "$@"; do case "$a" in FILTER=*) FILTER="${a#FILTER=}" ;; esac; done

JAR="$USELEESS_CARPET_JAR"
findjar() { ls "$HERE"/ingame-jar/useleess-carpet-*.jar "$HERE"/mod/build/libs/useleess-carpet-*.jar 2>/dev/null | head -1; }
[ -n "$JAR" ] || JAR="$(findjar)"
if [ -z "$JAR" ]; then
    echo "no launcher jar yet: building it"
    (cd mod && JAVA_HOME="$(ls -d "$T"/jdk21/*/ | head -1)" GRADLE_USER_HOME="$(cygpath -w "$T/gradle-home" 2>/dev/null || echo "$T/gradle-home")" ./gradlew -q jar) || { echo "NO RESULT: the build failed"; exit 1; }
    JAR="$(findjar)"
fi
if [ ! -f "$JAR" ] || [ ! -x "$JAVA" ] && [ ! -x "$JAVA.exe" ]; then
    echo "launcher jar: '$JAR', java: $JAVA"
    echo "NO RESULT: no launcher jar or no Java 8"
    exit 1
fi
BASE="$HERE/run-ingame"
rm -rf "$BASE"
mkdir -p "$BASE"
PIDS=""
cleanup() {
    for p in $PIDS; do kill "$p" 2>/dev/null; done
    sleep 2
    for p in $PIDS; do kill -9 "$p" 2>/dev/null; done
    # the "tail -f" feeding the console (started by this script only)
    for p in $(ps 2>/dev/null | awk -v me=$$ '$2 == me && /tail/ {print $1}'); do kill "$p" 2>/dev/null; done
    # servers the installer window started on its own (detached): only ours, found by their folder
    kill_servers_in "$BASE"
    sleep 1
    cd "$HERE" && rm -rf "$BASE"
}
trap cleanup EXIT

# stops every Java whose command line names this folder (our own test servers only)
kill_servers_in() {
    command -v powershell >/dev/null 2>&1 || return 0
    w="$(cygpath -w "$1" 2>/dev/null || echo "$1")"
    powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe' or Name='javaw.exe'\" | Where-Object { \$_.CommandLine -like '*$w*' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }" 2>/dev/null
}

PASS=0; FAIL=0; SKIP=0
wanted() {
    [ -z "$FILTER" ] && return 0
    for f in $(echo "$FILTER" | tr ',' ' '); do case "$1" in "$f"*) return 0 ;; esac; done
    return 1
}
# section <id prefix>...: a part of this script runs only when FILTER can want one of its tests
section() {
    [ -z "$FILTER" ] && return 0
    for p in "$@"; do
        for f in $(echo "$FILTER" | tr ',' ' '); do case "$p" in "$f"*) return 0 ;; esac; case "$f" in "$p"*) return 0 ;; esac; done
    done
    return 1
}
# result <id> <ok 0/1> <why>
result() {
    if ! wanted "$1"; then echo "SCOPE-TEST skip $1"; SKIP=$((SKIP + 1)); return; fi
    if [ "$2" = 0 ]; then echo "SCOPE-TEST pass $1"; PASS=$((PASS + 1)); else echo "SCOPE-TEST fail $1 $3"; FAIL=$((FAIL + 1)); fi
}
has() { grep -q -e "$2" "$1" 2>/dev/null; }
# wait_for <file> <text> <seconds>
wait_for() {
    n=0
    while [ $n -lt "$3" ]; do has "$1" "$2" && return 0; sleep 1; n=$((n + 1)); done
    return 1
}
echo "SCOPE-TEST total 29"
SERVER_JAR=useleess-carpet-1.12.2-server.jar
echo "launcher: $JAR"

# 1. a fresh folder: downloads Mojang's jar from Mojang and builds the server jar into the folder
# (the launcher sits IN the server folder, like a player's: it deletes itself afterwards)
A="$BASE/fresh"; mkdir -p "$A"; cd "$A" || exit 2
LAUNCHER="$(basename "$JAR")"
cp "$JAR" "$LAUNCHER"
"$JAVA" -jar "$LAUNCHER" --install-only > ../fresh-install.log 2>&1; code=$?
cat ../fresh-install.log
ok=1; has ../fresh-install.log "Downloading Mojang's official" && [ $code = 0 ] && [ -f "$SERVER_JAR" ] && ok=0
result install/download $ok "exit $code, see the log above"

# 1a. user 2026-10-30: the installer deletes itself (Windows: right after its Java process ends)
n=0; while [ $n -lt 30 ] && [ -e "$LAUNCHER" ]; do sleep 1; n=$((n + 1)); done
ok=1; [ ! -e "$LAUNCHER" ] && has ../fresh-install.log "From now on start the server with" && ok=0
result install/installer-removed $ok "the installer is still there after 30 s"

# 1b. user 2026-10-30: the folder holds ONLY the server jar + the start script made for it (no temp folder, no
# Mojang jar, no installer)
left="$(ls -A | tr '\n' ' ')"
ok=1; [ "$left" = "start-useleess-carpet.bat $SERVER_JAR " ] && has start-useleess-carpet.bat "$SERVER_JAR" && ok=0
result install/clean $ok "the folder holds: $(echo $left)"

# 2. the finished jar holds vanilla, Carpet (Bullet + Kisde + VasTech) and Redstone Scope
"$JARTOOL" tf "$SERVER_JAR" > files.txt 2>/dev/null
ok=0
for f in net/minecraft/server/MinecraftServer.class carpet/CarpetSettings.class carpet/VastechRuleCheck.class carpet/KisdeRuleCheck.class com/redstonescope/; do
    has files.txt "^$f" || { ok=1; echo "missing in the built jar: $f"; }
done
result install/contents $ok "files missing"

# 3. an owner's own copy of Mojang's jar is used instead of downloading, and kept
B="$BASE/owncopy"; mkdir -p "$B"; cd "$B" || exit 2
curl -sSfL -o minecraft_server.1.12.2.jar https://piston-data.mojang.com/v1/objects/886945bfb2b978778c3a0288fd7fab09d315b25f/server.jar
"$JAVA" -jar "$JAR" --install-only > install.log 2>&1; code=$?
ok=1; has install.log "Using your own copy" && ! has install.log "Downloading" && [ $code = 0 ] && [ -f minecraft_server.1.12.2.jar ] && [ -f "$SERVER_JAR" ] && ok=0
result install/local-copy $ok "$(tr '\n' ' ' < install.log)"

PORT=$((26000 + ($$ % 4000)))
say() { printf '%s\n' "$1" >> cmds.txt; }
# ask <command> <answer pattern> [<refusal pattern>]: the server console's answer (cmds.txt / server.log of the
# current folder); 0 = answered with the pattern, 1 = refused, 2 = no answer within ASK_TRIES half seconds (20 s)
ASK_TRIES=40
ask() {
    before=$(wc -l < server.log)
    say "$1"
    n=0
    while [ $n -lt "$ASK_TRIES" ]; do
        got="$(tail -n +"$((before + 1))" server.log)"
        echo "$got" | grep -qE "$2" && return 0
        [ -n "$3" ] && echo "$got" | grep -qE "$3" && return 1
        sleep 0.5; n=$((n + 1))
    done
    return 2
}
# until_ok <seconds> <ask args>: asks again every second until the answer comes; the seconds are real time (the clock),
# however long each ask takes
until_ok() {
    t=$1; shift; end=$(($(date +%s) + t))
    while [ "$(date +%s)" -lt "$end" ]; do ask "$@" && return 0; sleep 1; done
    return 1
}

# 4..12. a real server from the launcher, reusing the jar built in step 1; the folder also holds 0.1.0's old cache
if section install/ server/; then
C="$BASE/server"; mkdir -p "$C"; cd "$C" || exit 2
cp "$A/$SERVER_JAR" .
cp "$JAR" "$LAUNCHER"
mkdir useleess-carpet && echo x > useleess-carpet/mojang-server-1.12.2.jar && echo x > useleess-carpet/useleess-carpet-0.1.0-1.12.2-server.jar
echo "eula=true" > eula.txt
printf 'server-ip=127.0.0.1\nserver-port=%s\nonline-mode=false\nlevel-type=FLAT\nspawn-protection=0\nmax-players=2\nsnooper-enabled=false\n' "$PORT" > server.properties
: > cmds.txt
tail -f cmds.txt | "$JAVA" -Xmx1G -jar "$LAUNCHER" nogui > server.log 2>&1 &
PIDS="$! $PIDS"
wait_for server.log "is ready" 60; ok=$?
has server.log "Building" && ok=1
result install/cached $ok "the jar was not reused"
ok=1; [ ! -e useleess-carpet ] && [ ! -e .useleess-carpet-temp ] && ok=0
result install/old-folder $ok "0.1.0's useleess-carpet folder or a temp folder is still there"

wait_for server.log "Done (" 300; ok=$?
result server/boot $ok "no 'Done (' after 300 s: $(tail -5 server.log | tr '\n' ' ')"

# one rule from each part, asked on the server console
for pair in vastech:commandCluster kisde:playerSit bullet:commandInventory; do
    id="${pair%%:*}"; rule="${pair#*:}"
    before=$(wc -l < server.log)
    say "carpet $rule"
    n=0; ok=1
    while [ $n -lt 20 ]; do tail -n +"$((before + 1))" server.log | grep -q "$rule" && { ok=0; break; }; sleep 1; n=$((n + 1)); done
    sleep 1
    answer="$(tail -n +"$((before + 1))" server.log | head -8)"
    echo "carpet $rule -> $answer"
    # an unknown rule also names it: only a real rule answer counts
    echo "$answer" | grep -qiE "unknown|not found|no such|usage|error|exception" && ok=1
    result "server/rule-$id" $ok "no rule answer for carpet $rule"
done

ok=1; [ -f redstonescope.properties ] && has redstonescope.properties historyLength && ok=0
result server/redstone-scope $ok "no redstonescope.properties written at start"

say "stop"
n=0; ok=1
while [ $n -lt 90 ]; do has server.log "Stopping server" && [ -f world/level.dat ] && ! kill -0 "${PIDS%% *}" 2>/dev/null && { ok=0; break; }; sleep 1; n=$((n + 1)); done
[ $ok = 0 ] || tail -15 server.log
result server/stop $ok "the server did not stop and save"

# the installer that started this server is deleted once the server has stopped (Windows keeps it locked until then)
n=0; while [ $n -lt 30 ] && [ -e "$LAUNCHER" ]; do sleep 1; n=$((n + 1)); done
ok=1; [ ! -e "$LAUNCHER" ] && [ -f "$SERVER_JAR" ] && has server.log "deleted as soon as this program closes" && ok=0
result install/installer-removed-after-stop $ok "the installer is still there 30 s after the stop"
fi

# ---- our own Carpet rules (OurRules in mod/src/tools): a server of their own, started from the built server jar ----
if section rules/; then
R="$BASE/rules"; mkdir -p "$R"; cd "$R" || exit 2
cp "$A/$SERVER_JAR" .
echo "eula=true" > eula.txt
printf 'server-ip=127.0.0.1\nserver-port=%s\nonline-mode=false\nlevel-type=FLAT\nspawn-protection=0\nmax-players=4\nsnooper-enabled=false\nspawn-monsters=false\n' "$((PORT + 1))" > server.properties
# rules_server: starts it (again) and waits for "Done ("
rules_server() {
    : > cmds.txt; : > server.log
    tail -f cmds.txt | "$JAVA" -Xmx1G -jar "$SERVER_JAR" nogui > server.log 2>&1 &
    PIDS="$! $PIDS"
    wait_for server.log "Done (" 300 || { echo "rules server did not start: $(tail -5 server.log | tr '\n' ' ')"; return 1; }
}
rules_stop() {
    say "stop"
    n=0; while [ $n -lt 60 ] && kill -0 "${PIDS%% *}" 2>/dev/null; do sleep 1; n=$((n + 1)); done
    kill -9 "${PIDS%% *}" 2>/dev/null
}
# the section's server and the "tail -f" feeding its console end with the section, also after a failure
rules_end() {
    rules_stop
    for p in $(ps 2>/dev/null | awk -v me=$$ '$2 == me && /tail/ {print $1}'); do kill "$p" 2>/dev/null; done
}
RULE=endPortalNoTeleportPlayer
NOTFOUND="found nothing|cannot be found|No entity|not found|did not match"
# the whole section ends by this clock time; every wait below is also short on its own and says why it gave up
RULES_END=$(($(date +%s) + 300))
ASK_TRIES=12
rules_late() { [ "$(date +%s)" -ge "$RULES_END" ]; }
# where UcStay is, for the failure text: the console's answer to a tp onto its own spot, and whether it is in the End
ucstay_where() {
    ask "testfor @a[name=UcStay]" "Found UcStay" "$NOTFOUND"; any=$?
    ask "testfor UcStay {Dimension:1}" "Found UcStay" "$NOTFOUND|Unknown|rror"; dim=$?
    ask "execute UcStay ~ ~ ~ tp UcStay ~ ~ ~" "Teleported" "$NOTFOUND|rror"
    pos="$(tail -n 4 server.log | grep 'Teleported' | tail -n 1 | sed 's/.*Teleported/Teleported/')"
    echo "UcStay online: $([ "$any" = 0 ] && echo yes || echo no); in the End: $([ "$dim" = 0 ] && echo yes || echo no); ${pos:-no position answer}; server log: $(grep -v -E 'Found |found nothing' server.log | tail -n 14 | tr '
' ' ')"
}
rules_server

ok=1; ask "carpet $RULE" "$RULE" && sleep 1 && tail -n 8 server.log | grep -q "false" && ask "carpet list" "$RULE" && ok=0
tail -n 12 server.log | sed 's/^/    rules: /'
result rules/listed $ok "carpet $RULE / carpet list did not show it with false"

# a fake player standing in an end portal block (on the grass of the flat world), the rule on; then a dropped item
# nobody can pick up: once the item has left the Overworld, the portal has worked on everything in it.
# Each step waits for the one before: rule on -> block placed -> player joined -> player in the world -> teleported
# into the block and seen there -> item seen -> item gone.
why=""
in_portal="testfor @a[name=UcStay,x=2,y=4,z=2,dx=0,dy=0,dz=0]"
item_ow="testfor @e[type=item,x=0,y=0,z=0,r=100]"
step=1
ask "carpet $RULE true" "$RULE" || why="the rule did not answer to carpet $RULE true"
if [ -z "$why" ]; then
    ask "carpet $RULE" "$RULE" && sleep 1 && tail -n 8 server.log | grep -q "true" || why="the rule is not true after carpet $RULE true: $(tail -n 8 server.log | tr '
' ' ')"
fi
if [ -z "$why" ]; then
    say "player UcStay spawn"
    wait_for server.log "UcStay joined" 60 || why="player UcStay did not join within 60 s (no 'UcStay joined'): $(tail -n 4 server.log | tr '
' ' ')"
fi
# the fake player joins at 0, 0, 0 (inside the flat world's bedrock) and falls out of the world within seconds: tp it
# the moment it has joined, then creative mode (no fall damage from the drop before the tp), then wait to see it
[ -z "$why" ] && { say "tp UcStay 2.5 4 2.5"; say "gamemode creative UcStay"; until_ok 15 "$in_portal" "Found UcStay" "$NOTFOUND" || why="UcStay was never seen in the portal block after tp ($(ucstay_where))"; }
# the block is placed only now: before a player is there the chunk is not loaded ("Cannot place block outside of the
# world", seen on the box) - and the player is already standing where it appears
[ -z "$why" ] && { until_ok 20 "setblock 2 4 2 minecraft:end_portal" "Block placed" "Cannot place|$NOTFOUND" || why="the end portal block could not be placed: $(tail -n 3 server.log | tr '
' ' ')"; }
[ -z "$why" ] && { until_ok 10 "testforblock 2 4 2 minecraft:end_portal" "Successfully found" "$NOTFOUND|not a valid|is not" || why="no end portal block at 2 4 2 after setblock"; }
if [ -z "$why" ]; then
    say "summon item 2.5 4.6 2.5 {Item:{id:\"minecraft:stone\",Count:1b},PickupDelay:32767}"
    until_ok 10 "$item_ow" "Found " "$NOTFOUND" || why="the dropped item was never seen in the Overworld"
fi
gone=1
if [ -z "$why" ]; then
    # the first trip into the End also generates it (can take 1 min on a busy box)
    end=$(($(date +%s) + 100))
    while [ "$(date +%s)" -lt "$end" ] && ! rules_late; do ask "$item_ow" "Found " "$NOTFOUND"; [ $? = 1 ] && { gone=0; break; }; sleep 1; done
fi
if [ $gone = 0 ]; then result rules/end-portal-items-still-go 0 ""
else result rules/end-portal-items-still-go 1 "${why:-the item stayed in the Overworld for 100 s ($(ucstay_where))}"; fi

ok=1
if [ $gone = 0 ]; then
    ask "$in_portal" "Found UcStay" "$NOTFOUND" && ok=0
    [ $ok = 0 ] || why="the player left the end portal block while $RULE was true ($(ucstay_where))"
fi
result rules/end-portal-player-stays $ok "${why:-the player left the end portal block while $RULE was true}"

# the same player, still in the portal: the rule off sends it to the End (found anywhere, not in the Overworld)
ok=1
if [ -z "$why" ] || [ $gone = 0 ]; then
    ask "carpet $RULE false" "$RULE"
    end=$(($(date +%s) + 40))
    while [ "$(date +%s)" -lt "$end" ] && ! rules_late; do
        ask "testfor @a[name=UcStay,x=0,y=0,z=0,r=100000]" "Found UcStay" "$NOTFOUND"
        [ $? = 1 ] && ask "testfor @a[name=UcStay]" "Found UcStay" "$NOTFOUND" && { ok=0; break; }
        sleep 1
    done
fi
[ $ok = 0 ] || why="the player did not reach the End within 40 s with $RULE false ($(ucstay_where))"
result rules/end-portal-player-goes-off $ok "$why"

# saved for the world: carpet setDefault writes carpet.conf, a restart reads it back
ask "carpet setDefault $RULE true" "$RULE"
rules_stop
ok=1; grep -rq "$RULE true" --include=carpet.conf . && rules_server && ask "carpet $RULE" "$RULE" && sleep 1 && tail -n 8 server.log | grep -q "true" && ok=0
result rules/saved-in-carpet-conf $ok "$RULE was not true after setDefault + restart ($(cat */carpet.conf 2>/dev/null | tr '\n' ' '))"
rules_end
fi

# ---- double-click start (user 2026-10-30): the installer window, questions answered by the test ----
if section gui/; then
# gui_run <folder> <answers>: the installer jar in that folder, its window forced on, exit code in $code
gui_run() {
    cd "$1" || exit 2
    cp "$JAR" "$LAUNCHER"
    cp "$A/$SERVER_JAR" .
    "$JAVA" -Duseleess.carpet.gui=true -Duseleess.carpet.answers="$2" -jar "$LAUNCHER" > gui.log 2>&1; code=$?
    sed 's/^/    gui: /' gui.log
}
gone() { n=0; while [ $n -lt 30 ] && [ -e "$1" ]; do sleep 1; n=$((n + 1)); done; [ ! -e "$1" ]; }

# G1. an existing server with its own start.bat: never changed (user 2026-10-30), ours is made, EULA, server starts
D="$BASE/gui-existing"; mkdir -p "$D"
printf '@echo off\r\n"C:\\Java8\\bin\\java.exe" -Xmx3G -XX:+UseG1GC -jar old-server.jar nogui\r\npause\r\n' > "$D/start.bat"
cp "$D/start.bat" "$BASE/start.bat.original"
gui_run "$D" "eula=yes"
ok=1; cmp -s start.bat "$BASE/start.bat.original" && [ -z "$(ls start.bat.* 2>/dev/null)" ] && has gui.log "still starts old-server.jar" && ok=0
result gui/own-script-untouched $ok "start.bat: $(tr '\r\n' '  ' < start.bat)"
ok=1; has start-useleess-carpet.bat "jdk8" && has start-useleess-carpet.bat "-jar $SERVER_JAR nogui" && ok=0
result gui/script-created $ok "no start-useleess-carpet.bat next to the own start.bat"
ok=1; has eula.txt "eula=true" && ok=0
result gui/eula $ok "eula.txt not accepted"
ok=1; wait_for logs/latest.log "Done (" 300 && ok=0
result gui/server-started $ok "no server started from the window ($(tail -3 logs/latest.log 2>/dev/null | tr '\n' ' '))"
ok=1; [ "$code" = 0 ] && gone "$LAUNCHER" && has gui.log "double-click start-useleess-carpet.bat" && ok=0
result gui/installer-removed $ok "exit $code, installer still there"
kill_servers_in "$D"

# G2. an empty folder: start-useleess-carpet.bat is made, the server starts; then that script starts it too
E="$BASE/gui-fresh"; mkdir -p "$E"
gui_run "$E" "eula=yes"
ok=1; has start-useleess-carpet.bat "jdk8" && has start-useleess-carpet.bat "-jar $SERVER_JAR nogui" && ok=0
result gui/script-created-fresh $ok "start-useleess-carpet.bat: $(tr '\r\n' '  ' < start-useleess-carpet.bat 2>/dev/null)"
ok=1; wait_for logs/latest.log "Done (" 300 && ok=0
result gui/server-started-fresh $ok "no server started from the window"
kill_servers_in "$E"
sleep 3
rm -f logs/latest.log
: > cmds2.txt
tail -f cmds2.txt | cmd //c start-useleess-carpet.bat > bat.log 2>&1 &
PIDS="$! $PIDS"
ok=1; wait_for logs/latest.log "Done (" 300 && ok=0
printf 'stop\n\n' >> cmds2.txt
n=0; while [ $n -lt 60 ] && ! has logs/latest.log "Stopping server"; do sleep 1; n=$((n + 1)); done
has logs/latest.log "Stopping server" || ok=1
result gui/start-script-works $ok "start-useleess-carpet.bat did not start and stop the server: $(tail -3 bat.log | tr '\n' ' ')"
kill_servers_in "$E"

# G3. the EULA declined: nothing started, the own script unchanged, the installer stays
F="$BASE/gui-declined"; mkdir -p "$F"
cp "$BASE/start.bat.original" "$F/start.bat"
gui_run "$F" "eula=no"
sleep 5
ok=1; [ "$code" != 0 ] && ! has eula.txt "eula=true" && [ ! -e logs/latest.log ] && [ -e "$LAUNCHER" ] && has gui.log "was not started" && ok=0
result gui/eula-declined $ok "exit $code"
ok=1; cmp -s start.bat "$BASE/start.bat.original" && [ ! -e start.bat.backup ] && has gui.log "still starts old-server.jar" && ok=0
result gui/script-kept $ok "start.bat was changed"
kill_servers_in "$F"
fi

echo "SCOPE TESTS ALL: $PASS passed, $FAIL failed, $SKIP skipped"
[ $FAIL = 0 ]
