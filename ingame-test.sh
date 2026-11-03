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
echo "SCOPE-TEST total 56"
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

# uct <action> <player> [args]: runs /uct; the answer line is in $ANS (empty = no answer within 20 s)
uct() {
    before=$(wc -l < server.log); say "uct $*"; n=0; ANS=""
    while [ $n -lt 40 ]; do
        ANS="$(tail -n +"$((before + 1))" server.log | grep -E "UCT $1 |Unknown command|Caught previously unhandled|cannot be found|unknown error|Usage: " | head -n 1)"
        [ -n "$ANS" ] && return 0
        sleep 0.5; n=$((n + 1))
    done
    return 1
}
ans_has() { printf '%s' "$ANS" | grep -qE -e "$1"; }
# rules_server: starts the server of the current folder (again; $SERVER_OPTS = extra Java options) and waits for "Done ("
rules_server() {
    : > cmds.txt; : > server.log
    tail -f cmds.txt | "$JAVA" -Xmx1G $SERVER_OPTS -jar "$SERVER_JAR" nogui > server.log 2>&1 &
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
SERVER_OPTS="-Duseleess.carpet.selftest=1"
RULE=endPortalNoTeleportPlayer
NOTFOUND="found nothing|cannot be found|No entity|not found|did not match"
# the whole section ends by this clock time; every wait below is also short on its own and says why it gave up
RULES_END=$(($(date +%s) + 420))
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
    # proven by summon's own answer: the portal may send the item to the End before any later testfor sees it
    ask "summon item 2.5 4.6 2.5 {Item:{id:\"minecraft:stone\",Count:1b},PickupDelay:32767}" "successfully summoned" "rror|Unable|$NOTFOUND" || why="the item was not summoned: $(tail -n 3 server.log | tr '\n' ' ')"
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

# redstoneScopeLogger: Redstone Scope's recording commands answer "switched off" while the rule is false
SCOPE_RULE=redstoneScopeLogger
OFFTEXT="switched off"
# scope_answer: runs a recording command, waits 4 s for its answer, prints whether it said "switched off"
scope_answer() {
    before=$(wc -l < server.log); say "rtick back"; sleep 4
    tail -n +"$((before + 1))" server.log | grep -q "$OFFTEXT" && echo off || echo on
}
ok=1; ask "carpet $SCOPE_RULE" "$SCOPE_RULE" && sleep 1 && tail -n 8 server.log | grep -q "false" && ask "carpet list" "$SCOPE_RULE" && ok=0
result rules/rs-logger-listed $ok "carpet $SCOPE_RULE / carpet list did not show it with false (the default, #4): $(tail -n 6 server.log | tr '\n' ' ')"

ok=1
if ask "carpet $SCOPE_RULE false" "$SCOPE_RULE"; then
    n=0; off=1; while [ $n -lt 3 ] && ! rules_late; do [ "$(scope_answer)" = off ] && { off=0; break; }; n=$((n + 1)); done
    if [ $off = 0 ]; then
        ask "carpet $SCOPE_RULE true" "$SCOPE_RULE"
        n=0; while [ $n -lt 3 ] && ! rules_late; do [ "$(scope_answer)" = on ] && { ok=0; break; }; n=$((n + 1)); done
    fi
fi
result rules/rs-logger-off $ok "rtick back did not say '$OFFTEXT' with $SCOPE_RULE false, or still did after true: $(tail -n 6 server.log | tr '\n' ' ')"

ok=1
ask "carpet setDefault $SCOPE_RULE true" "$SCOPE_RULE"
rules_stop
if grep -rq "$SCOPE_RULE true" --include=carpet.conf . && rules_server && ask "carpet $SCOPE_RULE" "$SCOPE_RULE" && sleep 1 && tail -n 8 server.log | grep -q "true"; then
    n=0; while [ $n -lt 3 ] && ! rules_late; do [ "$(scope_answer)" = on ] && { ok=0; break; }; n=$((n + 1)); done
fi
result rules/rs-logger-saved $ok "$SCOPE_RULE was not true and Redstone Scope not recording after setDefault true + restart ($(cat */carpet.conf 2>/dev/null | tr '\n' ' '))"

# antiNoFall: /uct fall replays a client's movement reports for a 10-block fall onto the grass through the real
# handleFalling: a no-fall cheat says "on the ground" all the way down, an honest client only on landing
FALL_RULE=antiNoFall
ok=1; ask "carpet $FALL_RULE" "$FALL_RULE" && sleep 1 && tail -n 8 server.log | grep -q "false" && ask "carpet list" "$FALL_RULE" && ok=0
result rules/nofall-listed $ok "carpet $FALL_RULE / carpet list did not show it with false: $(tail -n 6 server.log | tr '\n' ' ')"
# fall_damage <cheat>: the damage of one fall in $DMG (empty = no answer)
fall_damage() { DMG=""; uct fall UcFall 10 "$1" && DMG="$(printf '%s' "$ANS" | sed -n 's/.*damage=\([0-9.]*\).*/\1/p')"; }
why=""
say "player UcFall spawn"
wait_for server.log "UcFall joined" 60 || why="player UcFall did not join within 60 s: $(tail -n 4 server.log | tr '\n' ' ')"
# creative until it stands on the grass (the drop from where it joins would hurt it on landing), then survival
[ -z "$why" ] && { say "tp UcFall 8.5 4 8.5"; say "gamemode creative UcFall"; until_ok 15 "testfor @a[name=UcFall,x=8,y=4,z=8,dx=0,dy=0,dz=0]" "Found UcFall" "$NOTFOUND" || why="UcFall was never seen standing on the grass at 8 4 8: $(tail -n 6 server.log | tr '\n' ' ')"; }
[ -z "$why" ] && { ask "gamemode survival UcFall" "UcFall|Survival" "$NOTFOUND" || why="UcFall did not switch to survival: $(tail -n 4 server.log | tr '\n' ' ')"; }
ok=1; fall_damage false; HONEST_OFF="$DMG"; fall_damage true
[ -z "$why" ] && [ "$DMG" = "0.0" ] && [ -n "$HONEST_OFF" ] && [ "$HONEST_OFF" != "0.0" ] && ok=0
result rules/nofall-off-cheat-safe $ok "${why:-rule off: the cheat took '$DMG' damage (vanilla: 0.0), the honest fall '$HONEST_OFF' ($ANS)}"
ok=1; ask "carpet $FALL_RULE true" "$FALL_RULE"; fall_damage true
[ -z "$why" ] && [ -n "$DMG" ] && [ "$DMG" = "$HONEST_OFF" ] && ok=0
result rules/nofall-on-cheat-hurt $ok "${why:-rule on: the cheat took '$DMG' damage, an honest fall takes '$HONEST_OFF' ($ANS)}"
ok=1; fall_damage false
[ -z "$why" ] && [ -n "$DMG" ] && [ "$DMG" = "$HONEST_OFF" ] && ok=0
result rules/nofall-on-honest-same $ok "${why:-rule on: an honest fall took '$DMG' damage, with the rule off '$HONEST_OFF' ($ANS)}"

# litematicaShulkerRestock: /uct shulker sends Litematica Upgraded's request (as LU's client writes it) through the real
# processCustomPayload for UcFall, who carries a shulker box with 64 stone in its slot 3
SH_RULE=litematicaShulkerRestock
ok=1; ask "carpet $SH_RULE" "$SH_RULE" && sleep 1 && tail -n 8 server.log | grep -q "false" && ask "carpet list" "$SH_RULE" && ok=0
result rules/shulker-listed $ok "carpet $SH_RULE / carpet list did not show it with false: $(tail -n 6 server.log | tr '\n' ' ')"
[ -z "$why" ] && { ask 'give UcFall minecraft:white_shulker_box 1 0 {BlockEntityTag:{Items:[{Slot:3b,id:"minecraft:stone",Count:64b,Damage:0s}]}}' "Given|Gave" "$NOTFOUND|rror|Invalid" || why="the shulker box could not be given: $(tail -n 3 server.log | tr '\n' ' ')"; }
ok=1; [ -z "$why" ] && uct shulker UcFall minecraft:stone minecraft:stone 64 && ans_has "moved=0$" && ok=0
result rules/shulker-off-refused $ok "${why:-rule off: the request was not refused: $ANS; server log: $(grep -A10 -E "Exception|Error" server.log | tail -n 24 | tr '\n' ' ')}"
ask "carpet $SH_RULE true" "$SH_RULE"
ok=1; [ -z "$why" ] && uct shulker UcFall minecraft:stone minecraft:dirt 64 && ans_has "moved=0$" && ok=0
result rules/shulker-wrong-item-refused $ok "${why:-rule on: a request for dirt from the stone slot was not refused: $ANS}"
ok=1; [ -z "$why" ] && uct shulker UcFall minecraft:stone minecraft:stone 64 && ans_has "moved=64$" && uct shulker UcFall minecraft:stone minecraft:stone 64 && ans_has "no box holds" && ok=0
result rules/shulker-on-moves $ok "${why:-rule on: 64 stone did not move out of the box into the inventory, or the box still held stone: $ANS}"
rules_end
fi

# ---- careful break per player (logger carefulBreak with the options sneak / always): a server of its own, a Carpet
# fake player (survival) mining a dirt block in front of it; the drop is in its inventory or on the ground.
if section carefulbreak/; then
K="$BASE/carefulbreak"; mkdir -p "$K"; cd "$K" || exit 2
cp "$A/$SERVER_JAR" .
echo "eula=true" > eula.txt
printf 'server-ip=127.0.0.1\nserver-port=%s\nonline-mode=false\nlevel-type=FLAT\nspawn-protection=0\nmax-players=4\nsnooper-enabled=false\nspawn-monsters=false\n' "$((PORT + 2))" > server.properties
CB_NOTFOUND="found nothing|cannot be found|No entity|not found|did not match|rror"
CB_END=$(($(date +%s) + 420))
cb_late() { [ "$(date +%s)" -ge "$CB_END" ]; }
ASK_TRIES=12

# cb_join: the fake player UcBreak joins, stands at 2.5 4 2.5 looking south (level) in survival; prints why not (empty = ok)
cb_join() {
    say "player UcBreak spawn"
    wait_for server.log "UcBreak joined" 60 || { echo "UcBreak did not join within 60 s: $(tail -n 4 server.log | tr '\n' ' ')"; return; }
    say "gamemode creative UcBreak"; say "tp UcBreak 2.5 4 2.5 0 0"
    until_ok 15 "testfor @a[name=UcBreak,x=2,y=4,z=2,dx=0,dy=0,dz=0]" "Found UcBreak" "$CB_NOTFOUND" || { echo "UcBreak was never seen at the mining spot"; return; }
    say "gamemode survival UcBreak"
}
# cb_mine: UcBreak mines the dirt block at 2 5 5 and prints where the drop went: inventory, ground or nowhere
# (or "failed: why" when the block was never broken)
cb_mine() {
    say "clear UcBreak"; say "kill @e[type=item]"; sleep 1
    until_ok 10 "setblock 2 5 5 minecraft:dirt" "Block placed" "Cannot place|$CB_NOTFOUND" || { echo "failed: the dirt block could not be placed"; return; }
    say "player UcBreak attack continuous"
    if ! until_ok 30 "testforblock 2 5 5 minecraft:air" "Successfully found" "is not|$CB_NOTFOUND"; then
        say "player UcBreak stop"; echo "failed: UcBreak did not break the block within 30 s"; return
    fi
    say "player UcBreak stop"
    sleep 2
    if ask "testfor @e[type=item,x=0,y=0,z=0,r=100]" "Found " "$CB_NOTFOUND"; then echo ground
    elif ask "clear UcBreak minecraft:dirt 0 0" "has [1-9][0-9]* items" "no items|Could not"; then echo inventory
    else echo nowhere; fi
}
# cb_expect <test id> <inventory|ground> <what was set up>: the result line of one mining run
cb_expect() {
    got="$(cb_mine)"
    ok=1; [ "$got" = "$2" ] && ok=0
    result "$1" $ok "$3: the drop went to '$got', expected '$2' ($(tail -n 3 server.log | tr '\n' ' '))"
}

rules_server
say "carpet setDefault carefulBreak true"
why="$(cb_join)"
if [ -n "$why" ]; then
    for t in sneak-default always off rule-off remembered; do result carefulbreak/$t 1 "$why"; done
else
    # 1. option sneak (the console form: a fake player cannot run /log itself)
    say "log carefulBreak sneak UcBreak"; sleep 1
    gA="$(cb_mine)"
    say "player UcBreak sneak"; sleep 1
    gB="$(cb_mine)"
    say "player UcBreak stop"
    ok=1; [ "$gA" = ground ] && [ "$gB" = inventory ] && ok=0
    result carefulbreak/sneak-default $ok "subscribed with no option: not sneaking the drop went to '$gA' (expected ground), sneaking to '$gB' (expected inventory) ($(tail -n 3 server.log | tr '
' ' '))"
    # 2. always: no Sneak key needed
    say "log carefulBreak clear UcBreak"; sleep 1
    say "log carefulBreak always UcBreak"; sleep 1
    cb_expect carefulbreak/always inventory "option always, not sneaking"
    # 3. not subscribed: ground even while sneaking
    say "log carefulBreak clear UcBreak"; sleep 1
    say "player UcBreak sneak"; sleep 1
    got="$(cb_mine)"
    say "player UcBreak stop"
    ok=1; [ "$got" = ground ] && ok=0
    result carefulbreak/off $ok "not subscribed, sneaking: the drop went to '$got', expected 'ground'"
    # 4. rule carefulBreak off: ground even with always
    say "log carefulBreak always UcBreak"; sleep 1
    ask "carpet carefulBreak false" "carefulBreak" >/dev/null
    cb_expect carefulbreak/rule-off ground "rule carefulBreak false, option always"
    ask "carpet carefulBreak true" "carefulBreak" >/dev/null
    # 5. remembered over a restart: our own file by UUID; Carpet's own copy of the subscription is removed first so
    # that only ours can put it back
    n=0; while [ $n -lt 20 ] && ! grep -q always world/useleess-carpet/careful-break.json 2>/dev/null; do sleep 1; n=$((n + 1)); done
    saved="$(cat world/useleess-carpet/careful-break.json 2>/dev/null)"
    rules_stop
    for f in $(grep -rl carefulBreak --include='*.json' --include='*.conf' . 2>/dev/null | grep -v 'useleess-carpet/careful-break.json'); do
        case "$f" in *carpet.conf) ;; *) rm -f "$f" ;; esac
    done
    ok=1
    if rules_server; then
        why="$(cb_join)"
        [ -z "$why" ] && { got="$(cb_mine)"; [ "$got" = inventory ] && ok=0; why="after the restart the drop went to '$got', expected 'inventory'"; }
    else why="the server did not start again"; fi
    result carefulbreak/remembered $ok "$why (saved file: $saved)"
fi
rules_end
fi

# ---- op-made custom recipes (rule customRecipes, /customrecipe): a server of its own with the self-test command /uct,
# which drives fake players' editors by slot ROLE through their real open container. Survival ops, no client.
if section recipes/; then
Q="$BASE/recipes"; mkdir -p "$Q"; cd "$Q" || exit 2
cp "$A/$SERVER_JAR" .
echo "eula=true" > eula.txt
printf 'server-ip=127.0.0.1\nserver-port=%s\nonline-mode=false\nlevel-type=FLAT\nspawn-protection=0\nmax-players=6\nsnooper-enabled=false\nspawn-monsters=false\n' "$((PORT + 2))" > server.properties
SERVER_OPTS="-Duseleess.carpet.selftest=1"
RECIPES="world/useleess-carpet/recipes"
VAULT="world/useleess-carpet/editor"
click() { uct click UcOp "$@"; }
# until_state <seconds> <pattern>: asks UcOp's editor state until it matches (the state answers at once; this waits for scheduled window changes)
until_state() {
    t=$1; end=$(($(date +%s) + t))
    while [ "$(date +%s)" -lt "$end" ]; do uct state UcOp && ans_has "$2" && return 0; sleep 0.5; done
    return 1
}
# wait_i <text> <seconds>: like wait_for on server.log, any letter case (Carpet may log a fake player's name in lower case)
wait_i() { n=0; while [ $n -lt "$2" ]; do grep -qi -e "$1" server.log && return 0; sleep 1; n=$((n + 1)); done; return 1; }
# spawn_player <name> <x>: a Carpet fake player joins at the console's spot, 0 0 0, inside the bedrock, from where it can
# fall out of the world within a second. Carpet makes the player inside the spawn command itself, so the tp sent right
# behind it runs before the player can fall. Waits for the "[local] logged in" line (the "joined" line may carry colour
# codes); Carpet first asks Mojang for the skin, and with no answer that waits for the web timeout: up to 3 minutes.
spawn_player() {
    before=$(grep -ci "$1\[local\] logged in" server.log)
    say "player $1 spawn"; say "tp $1 $2 4 2.5"
    n=0; while [ $n -lt 360 ] && [ "$(grep -ci "$1\[local\] logged in" server.log)" -le "$before" ]; do sleep 0.5; n=$((n + 1)); done
    [ $n -lt 360 ] || return 1
    say "tp $1 $2 4 2.5"; say "gamemode survival $1"
}
# rresult: result for a recipes/ test; a failure also prints the server's errors logged since the last failure (its cause)
LOGMARK=0
rresult() {
    result "$@"
    [ "$2" = 0 ] && return
    [ "$LOGMARK" -le "$(wc -l < server.log)" ] || LOGMARK=0
    tail -n +"$((LOGMARK + 1))" server.log | grep -E -A14 "Exception|Error|Caused by|Couldn't|UseleessCarpet" | head -n 60
    LOGMARK=$(wc -l < server.log)
}
# player_gone <name> <seconds>: waits until the server no longer has that player
player_gone() {
    end=$(($(date +%s) + $2))
    while [ "$(date +%s)" -lt "$end" ]; do uct inv "$1"; ans_has "cannot be found" && return 0; sleep 0.5; done
    return 1
}
craft_sword="- minecraft:stick - - minecraft:diamond - - minecraft:diamond -"
rules_server && spawn_player UcOp 2.5 && spawn_player UcPlain 6.5 && spawn_player UcCraft 10.5 && ask "op UcOp" "Opped UcOp"
uct give UcOp minecraft:diamond 5; uct give UcOp minecraft:stick 3; uct give UcOp minecraft:golden_apple 2
uct inv UcOp; INV_BEFORE="${ANS#*UCT inv }"
echo "inventory before: $INV_BEFORE"

# the op opens the editor: survival, no items needed; the real inventory is saved and emptied
uct cmd UcOp customrecipe
ok=1; until_state 20 "mode=EDITOR ready=true" && uct inv UcOp && ans_has "\[\] cursor=-" && [ -f "$VAULT"/*.dat ] && ok=0
rresult recipes/command-opens $ok "state: $ANS; vault: $(ls $VAULT 2>/dev/null)"
[ $ok = 0 ] || { echo "recipes server log, our lines:"; grep -iE -A12 "useleess|customrecipe|exception" server.log | head -80; echo "recipes server log, last lines:"; tail -n 40 server.log; }

# a player who is not an op is refused
ok=1; uct cmd UcPlain customrecipe && ans_has "result=0" && uct state UcPlain && ans_has "state none" && ok=0
rresult recipes/non-op-refused $ok "$ANS"

# the category button: click = next, right-click = previous; ten clicks come round
ok=1
uct state UcOp; start="$(printf '%s' "$ANS" | sed -n 's/.* category=\([a-z]*\) .*/\1/p')"
click CATEGORY 0 0 && uct state UcOp && ans_has "category=decoration" && click CATEGORY 0 1 && uct state UcOp && ans_has "category=$start " && ok=0
if [ $ok = 0 ]; then ok=1; i=0; while [ $i -lt 10 ]; do click CATEGORY 0 0; i=$((i + 1)); done; uct state UcOp && ans_has "category=$start " && ok=0; fi
rresult recipes/category-cycle $ok "$ANS"

# paging in a long category
uct state UcOp; first="$(printf '%s' "$ANS" | sed -n 's/.* picker0=\([^ ]*\) .*/\1/p')"
ok=1
click NEXT 0 0 && uct state UcOp && ans_has "page=1 " && ! ans_has "picker0=$first " && click PREV 0 0 && uct state UcOp && ans_has "page=0 " && ans_has "picker0=$first " && ok=0
rresult recipes/paging $ok "first $first, then $ANS"

# search through the anvil window: the grid is kept, the picker is filtered, back in the editor
click CLEAR; uct pick UcOp minecraft:diamond && click GRID 4 0
ok=1
click SEARCH && until_state 20 "searchready=true" && uct typesearch UcOp sword && uct click UcOp ANVIL_OUT && until_state 20 "mode=EDITOR ready=true" \
    && ans_has "search=\[sword\]" && ans_has "grid=-,-,-,-,minecraft:diamond/0x1" && uct pick UcOp minecraft:diamond_sword && ans_has "picked minecraft:diamond_sword" && ok=0
rresult recipes/search-anvil $ok "$ANS"
click SEARCH; until_state 20 "searchready=true"; uct typesearch UcOp ""; uct click UcOp ANVIL_OUT; until_state 20 "mode=EDITOR ready=true"
# the search box starts empty (the hint is the paper's lore) and the anvil shows no level cost
click SEARCH; ok=1
until_state 20 "searchready=true" && ans_has "anvil=\[\] cost=0 " && ok=0
rresult recipes/search-anvil-empty $ok "$ANS"
uct typesearch UcOp ""; uct click UcOp ANVIL_OUT; until_state 20 "mode=EDITOR ready=true"

# a search looks in every category: with the category on Building, "apple" is still found
ok=1; i=0; while [ $i -lt 12 ] && { uct state UcOp; ! ans_has "category=building "; }; do click CATEGORY 0 0; i=$((i + 1)); done
click SEARCH && until_state 20 "searchready=true" && uct typesearch UcOp apple && uct click UcOp ANVIL_OUT && until_state 20 "mode=EDITOR ready=true" \
    && ans_has "search=\[apple\]" && ans_has "category=building " && uct pick UcOp minecraft:apple && ans_has "picked minecraft:apple" && ok=0
rresult recipes/search-all-categories $ok "$ANS"
click SEARCH; until_state 20 "searchready=true"; uct typesearch UcOp ""; uct click UcOp ANVIL_OUT; until_state 20 "mode=EDITOR ready=true"

# build a recipe and save it: the menu stays open, the file is written
click CLEAR
uct pick UcOp minecraft:stick && click GRID 1 0; uct pick UcOp minecraft:diamond && click GRID 4 0 && click GRID 7 0
uct pick UcOp minecraft:diamond_sword && click OUTPUT 0 0 && click SAVE
ok=1; uct state UcOp && ans_has "editing=diamond_sword" && ans_has "ready=true" && ans_has "title=\[Saved: diamond_sword\]" && [ -f "$RECIPES/diamond_sword.json" ] && ok=0
rresult recipes/build-save $ok "$ANS; files: $(ls $RECIPES 2>/dev/null)"

# a normal player at a real crafting table gets the result
ok=1; uct craft UcCraft $craft_sword && ans_has "result=minecraft:diamond_sword/0x1" && ans_has "took=minecraft:diamond_sword/0x1" && ok=0
rresult recipes/craft-works $ok "$ANS"

# any order: the repeater button, items anywhere in the grid
click CLEAR; click ORDER 0 0
uct pick UcOp minecraft:diamond && click GRID 0 0; uct pick UcOp minecraft:stick && click GRID 5 0
uct pick UcOp minecraft:emerald && click OUTPUT 0 0 && click SAVE
ok=1; uct state UcOp && ans_has "anyorder=true" && ans_has "editing=emerald" && ok=0
if [ $ok = 0 ]; then ok=1; uct craft UcCraft - - minecraft:diamond - - - minecraft:stick - - && ans_has "result=minecraft:emerald/0x1" && ok=0; fi
rresult recipes/shapeless-any-order $ok "$ANS"
click ORDER 0 0

# clear empties the grid and the result
click CLEAR
ok=1; uct state UcOp && ans_has "grid=-,-,-,-,-,-,-,-,- output=-" && ok=0
rresult recipes/clear $ok "$ANS"

# all recipes: load one (its shape at the top left of the grid), change its result to 2 diamonds (a stackable item:
# +1 stops at the stack size), save over it, delete one with two clicks
ok=1; STEP="list"; click ALL && uct state UcOp && ans_has "mode=LIST" && STEP="load" && click LIST 0 0 && uct state UcOp && ans_has "mode=EDITOR" && ans_has "editing=diamond_sword" \
    && STEP="loaded grid" && ans_has "grid=minecraft:stick/0x1,-,-,minecraft:diamond/0x1,-,-,minecraft:diamond/0x1,-,-" && STEP="pick" && uct pick UcOp minecraft:diamond && ans_has "picked minecraft:diamond/" \
    && STEP="output, then +1" && click OUTPUT 0 0 && click OUTPUT 0 0 && uct state UcOp && ans_has "output=minecraft:diamond/0x2" && STEP="save" && click SAVE && uct state UcOp && ans_has "title=\[Saved: diamond_sword\]" \
    && STEP="file" && grep -q '"count": 2' "$RECIPES/diamond_sword.json" && STEP="craft" && uct craft UcCraft $craft_sword && ans_has "took=minecraft:diamond/0x2" && ok=0
rresult recipes/all-recipes-load-overwrite $ok "stopped at: $STEP; last answer: $ANS"
ok=1
click ALL && click LIST 1 0 QUICK_MOVE && uct state UcOp && ans_has "recipes=\[diamond_sword, emerald\]" && [ -f "$RECIPES/emerald.json" ] \
    && click LIST 1 0 QUICK_MOVE && uct state UcOp && ans_has "recipes=\[diamond_sword\]" && [ ! -f "$RECIPES/emerald.json" ] && ok=0
rresult recipes/delete-two-clicks $ok "$ANS; files: $(ls $RECIPES 2>/dev/null)"
click BACK 0 0; click CLEAR

# refused saves say why and change nothing
click SAVE
ok=1; uct state UcOp && ans_has "title=\[Grid or result is empty\]" && ok=0
rresult recipes/save-empty-refused $ok "$ANS"
uct pick UcOp minecraft:planks && click GRID 1 0 && click GRID 4 0; uct pick UcOp minecraft:stick && click OUTPUT 0 0 && click SAVE
ok=1; uct state UcOp && ans_has "title=\[Recipe already exists\]" && ans_has "recipes=\[diamond_sword\]" && [ ! -f "$RECIPES/stick.json" ] && ok=0
rresult recipes/conflict-refused $ok "$ANS"
click CLEAR

# a status in the title fits the window: at most 30 characters, even for a long result name
uct pick UcOp minecraft:diamond && click GRID 0 0 && click GRID 1 0; uct pick UcOp minecraft:light_weighted_pressure_plate && click OUTPUT 0 0 && click SAVE
ok=1; uct state UcOp && ans_has "editing=light_weighted_pressure_plate" && ans_has "title=\[Saved: light_weighted_press\.\.\.\]" \
    && [ "$(printf '%s' "$ANS" | sed -n 's/.* title=\[\(.*\)\] grid=.*/\1/p' | tr -d '\n' | wc -c)" -le 30 ] && ok=0
rresult recipes/title-short $ok "$ANS"
click ALL && click LIST 1 0 QUICK_MOVE && click LIST 1 0 QUICK_MOVE; click BACK 0 0; click CLEAR

# exit: the inventory is exactly as before, the vault file gone
click EXIT 0 0
ok=1; uct state UcOp && ans_has "state none" && uct inv UcOp && [ "${ANS#*UCT inv }" = "$INV_BEFORE" ] && [ -z "$(ls $VAULT 2>/dev/null)" ] && ok=0
rresult recipes/exit-restores $ok "before: $INV_BEFORE; now: $ANS"

# closing the window (Esc) restores too
uct cmd UcOp customrecipe; until_state 20 "mode=EDITOR ready=true"
uct close UcOp
ok=1; uct state UcOp && ans_has "state none" && uct inv UcOp && [ "${ANS#*UCT inv }" = "$INV_BEFORE" ] && ok=0
rresult recipes/close-restores $ok "before: $INV_BEFORE; now: $ANS"

# nothing can leave: shift-click, number key, drop (Q and outside), double-click, middle-click, drag, anvil clicks
uct cmd UcOp customrecipe; until_state 20 "mode=EDITOR ready=true"
# items the world itself dropped (the flat world's villages) are cleared first: only new ones count
ask "kill @e[type=item]" "Killed|found nothing"
click SLOT 27 0 PICKUP; click SLOT 27 0 QUICK_MOVE; click SLOT 28 0 QUICK_MOVE; click SLOT 27 0 SWAP; click SLOT 27 8 SWAP; click SLOT 27 0 THROW; click SLOT 27 1 THROW
click OUTSIDE 0 0 PICKUP; click OUTSIDE 0 1 PICKUP; click OUTSIDE 0 0 THROW; click SLOT 28 0 PICKUP_ALL; click SLOT 28 2 CLONE; click SLOT 28 0 QUICK_CRAFT; click GRID 0 0 QUICK_MOVE
click SLOT 6 0 QUICK_MOVE; click SLOT 15 0 THROW; click SLOT 62 0 SWAP
click SEARCH; until_state 20 "searchready=true"; click SLOT 0 0 QUICK_MOVE; click SLOT 0 0 THROW; click SLOT 1 0 SWAP; click SLOT 30 0 QUICK_MOVE; uct click UcOp ANVIL_OUT; until_state 20 "mode=EDITOR ready=true"
ok=1; uct inv UcOp; real="$ANS"; ask "testfor @e[type=item,x=0,y=0,z=0,r=100]" "Found " "found nothing|cannot be found|No entity|not found|did not match"; gone=$?
ans_has "UCT inv \[\] cursor=-" && [ $gone = 1 ] && ok=0
rresult recipes/no-dupes $ok "inventory while editing: $real; item entities answer $gone (0 = some found): $(tail -n 5 server.log | grep -i found)"
click EXIT 0 0; uct inv UcOp
[ "${ANS#*UCT inv }" = "$INV_BEFORE" ] || rresult recipes/no-dupes-exit 1 "after the clicks and Exit: $ANS (before: $INV_BEFORE)"

# the op leaves mid-edit: the items are back before the player data is written, and are there when they come back
uct cmd UcOp customrecipe; until_state 20 "mode=EDITOR ready=true"
say "player UcOp kill"
ok=1; STEP="player gone"; player_gone UcOp 30 && STEP="vault emptied" && [ -z "$(ls $VAULT 2>/dev/null)" ] && STEP="rejoin" && spawn_player UcOp 2.5 \
    && STEP="inventory back" && uct inv UcOp && [ "${ANS#*UCT inv }" = "$INV_BEFORE" ] && ok=0
rresult recipes/disconnect-restores $ok "stopped at: $STEP; before: $INV_BEFORE; last answer: $ANS; vault: $(ls $VAULT 2>/dev/null)"

# the server is killed mid-edit: the vault file is still there and the items come back at the next join
uct cmd UcOp customrecipe; until_state 20 "mode=EDITOR ready=true"
kill -9 "${PIDS%% *}" 2>/dev/null
n=0; while [ $n -lt 30 ] && kill -0 "${PIDS%% *}" 2>/dev/null; do sleep 1; n=$((n + 1)); done
had="$(ls $VAULT 2>/dev/null)"
for p in $(ps 2>/dev/null | awk -v me=$$ '$2 == me && /tail/ {print $1}'); do kill "$p" 2>/dev/null; done
ok=1; STEP="vault kept after the kill"; [ -n "$had" ] && STEP="restart" && rules_server && STEP="UcOp rejoins" && spawn_player UcOp 2.5 && STEP="UcCraft rejoins" && spawn_player UcCraft 10.5 \
    && STEP="inventory back" && uct inv UcOp && [ "${ANS#*UCT inv }" = "$INV_BEFORE" ] && STEP="vault emptied" && [ -z "$(ls $VAULT 2>/dev/null)" ] && ok=0
rresult recipes/crash-restores $ok "stopped at: $STEP; vault after the kill: [$had]; before: $INV_BEFORE; last answer: $ANS"

# the rule off: saved recipes do not match and the command is refused; files stay
ask "carpet customRecipes false" "customRecipes"
ok=1; STEP="no craft result"; uct craft UcCraft $craft_sword && ans_has "result=-" && STEP="command refused" && uct cmd UcOp customrecipe && ans_has "result=1" \
    && STEP="no editor" && uct state UcOp && ans_has "state none" && STEP="file kept" && [ -f "$RECIPES/diamond_sword.json" ] && ok=0
rresult recipes/rule-off $ok "stopped at: $STEP; last answer: $ANS"
ask "carpet customRecipes true" "customRecipes"
ok=1; uct craft UcCraft $craft_sword && ans_has "took=minecraft:diamond/0x2" && ok=0
rresult recipes/rule-on-again $ok "last answer: $ANS"

# a restart keeps the recipes; a broken file and a badly named file are skipped, the server and the others go on
rules_stop
printf '{ nope' > "$RECIPES/broken.json"
printf '{}' > "$RECIPES/Bad Name.json"
ok=1; STEP="restart"; rules_server && STEP="players join" && spawn_player UcCraft 10.5 && spawn_player UcOp 2.5 \
    && STEP="bad files named in the log" && has server.log "broken.json" && has server.log "Bad Name.json" && STEP="craft" && uct craft UcCraft $craft_sword && ans_has "took=minecraft:diamond/0x2" && ok=0
rresult recipes/survives-restart $ok "stopped at: $STEP; last answer: $ANS"
ok=1; has server.log "Recipe file broken.json was skipped" && has server.log "Recipe file Bad Name.json was skipped" && ok=0
rresult recipes/bad-file-skipped $ok "$(grep -i 'skipped' server.log | head -n 3 | tr '\n' ' ')"
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
