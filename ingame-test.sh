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
    sleep 1
    cd "$HERE" && rm -rf "$BASE"
}
trap cleanup EXIT

PASS=0; FAIL=0; SKIP=0
wanted() {
    [ -z "$FILTER" ] && return 0
    for f in $(echo "$FILTER" | tr ',' ' '); do case "$1" in "$f"*) return 0 ;; esac; done
    return 1
}
# result <id> <ok 0/1> <why>
result() {
    if ! wanted "$1"; then echo "SCOPE-TEST skip $1"; SKIP=$((SKIP + 1)); return; fi
    if [ "$2" = 0 ]; then echo "SCOPE-TEST pass $1"; PASS=$((PASS + 1)); else echo "SCOPE-TEST fail $1 $3"; FAIL=$((FAIL + 1)); fi
}
has() { grep -q "$2" "$1" 2>/dev/null; }
# wait_for <file> <text> <seconds>
wait_for() {
    n=0
    while [ $n -lt "$3" ]; do has "$1" "$2" && return 0; sleep 1; n=$((n + 1)); done
    return 1
}
echo "SCOPE-TEST total 12"
SERVER_JAR=useleess-carpet-1.12.2-server.jar
echo "launcher: $JAR"

# 1. a fresh folder: downloads Mojang's jar from Mojang and builds the server jar into the folder
A="$BASE/fresh"; mkdir -p "$A"; cd "$A" || exit 2
"$JAVA" -jar "$JAR" --install-only > ../fresh-install.log 2>&1; code=$?
cat ../fresh-install.log
ok=1; has ../fresh-install.log "Downloading Mojang's official" && [ $code = 0 ] && [ -f "$SERVER_JAR" ] && ok=0
result install/download $ok "exit $code, see the log above"

# 1b. user 2026-10-30: the folder holds ONLY the server jar afterwards (no temp folder, no Mojang jar)
left="$(ls -A)"
ok=1; [ "$left" = "$SERVER_JAR" ] && ok=0
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

# 4..12. a real server from the launcher, reusing the jar built in step 1; the folder also holds 0.1.0's old cache
C="$BASE/server"; mkdir -p "$C"; cd "$C" || exit 2
cp "$A/$SERVER_JAR" .
mkdir useleess-carpet && echo x > useleess-carpet/mojang-server-1.12.2.jar && echo x > useleess-carpet/useleess-carpet-0.1.0-1.12.2-server.jar
PORT=$((26000 + ($$ % 4000)))
echo "eula=true" > eula.txt
printf 'server-ip=127.0.0.1\nserver-port=%s\nonline-mode=false\nlevel-type=FLAT\nspawn-protection=0\nmax-players=2\nsnooper-enabled=false\n' "$PORT" > server.properties
: > cmds.txt
tail -f cmds.txt | "$JAVA" -Xmx1G -jar "$JAR" nogui > server.log 2>&1 &
PIDS="$! $PIDS"
say() { printf '%s\n' "$1" >> cmds.txt; }
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

echo "SCOPE TESTS ALL: $PASS passed, $FAIL failed, $SKIP skipped"
[ $FAIL = 0 ]
