#!/bin/sh
# throwaway (ladder job): unit tests + build, then the filtered in-game tests; deleted after the job
cd "$(dirname "$0")" || exit 2
T="$(cd .. && pwd)/toolchain"; [ -d "$T" ] || T="$(cd ../.. && pwd)/toolchain"
(cd mod && JAVA_HOME="$(ls -d "$T"/jdk21/*/ | head -1)" GRADLE_USER_HOME="$(cygpath -w "$T/gradle-home" 2>/dev/null || echo "$T/gradle-home")" ./gradlew build --console=plain 2>&1 | grep -vE "^> Task|^$" | tail -60) || exit 1
ls mod/build/libs/ || exit 1
./ingame-test.sh "$@"
