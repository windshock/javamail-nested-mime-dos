# JavaMail Nested-MIME Denial of Service (javax.mail:mail 1.4.x)

[![PoC](https://github.com/windshock/javamail-nested-mime-dos/actions/workflows/poc.yml/badge.svg)](https://github.com/windshock/javamail-nested-mime-dos/actions/workflows/poc.yml)


A single, small, **unauthenticated** email can take down any service that
fetches and parses untrusted mail with an old JavaMail
(`javax.mail:mail` 1.4.x) on the classpath.

## TL;DR

`javax.mail:mail` 1.4.x imposes **no limit** on MIME multipart nesting depth
or part count. A typical ingestion loop — `new MimeMessage(stream)` followed
by walking the tree with `getContent()` and recursing into nested
multiparts — can be driven into:

1. **Stack exhaustion** — deeply nested `multipart/*` → `StackOverflowError`.
   It is an `Error`, so a surrounding `catch (Exception)` does **not** recover;
   the ingestion thread dies.
2. **Super-linear parse time (CPU)** — a sub-megabyte message pins a CPU core
   for minutes.

Classification: **CWE-674** (Uncontrolled Recursion), **CWE-400** (Uncontrolled
Resource Consumption), **CWE-1104** (Use of Unmaintained Third-Party Components).

> This is **not** a new 0-day. It is the well-known consequence of running an
> **EOL (2011)** library that predates the hardening (`mail.mime.multipart.maxcount`
> and friends) added in later releases. The point of this repo is a clean,
> reproducible demonstration of the risk of pinning to `1.4.x`.

## Affected / Not affected

| | |
|---|---|
| Affected | `javax.mail:mail` 1.4.x (EOL) parsing **externally received** mail |
| Fixed upstream | Later JavaMail (1.6+) / **Jakarta Mail** / **Eclipse Angus Mail** add count/size guards (`mail.mime.multipart.maxcount`, etc.) |
| Attack surface | Any code doing `new MimeMessage(stream)` + `getContent()` walk on attacker-controlled mail (POP3/IMAP/stored `.eml`) |
| Not relevant | Sending-only code (`Transport.send`) that *builds* messages from trusted data |

## Measured behavior (local, JavaMail 1.4.4, JDK 17)

Parse time vs. nesting depth (whole message read into the tree):

| nesting | size | parse+walk time |
|--------:|-----:|----------------:|
| 1,000 | 34 KB | 0.42 s |
| 2,000 | 68 KB | 0.96 s |
| 4,000 | 140 KB | 3.06 s |
| 8,000 | 560 KB | **521 s** |

Stack exhaustion (emulating a server worker-thread stack):

| thread stack | crash at nesting |
|---|---|
| `-Xss256k` | StackOverflowError ~depth 788 (≈55 KB email) |
| `-Xss512k` | StackOverflowError ~depth 2,000–6,000 (≈0.4 MB email) |

## One-click proof

You don't need a local setup: the [**PoC** GitHub Actions workflow](../../actions/workflows/poc.yml) runs the whole attack on GitHub's runners and **passes only if the crash is reproduced** (StackOverflowError + non-zero JVM exit). Open the Actions tab, or click **Run workflow**, and read the log.

Locally it is a single command:

## Run it (fully local — no external hosts contacted except Maven Central)

```bash
./run.sh            # defaults: nesting=6000, stack=512k
./run.sh 8000 256k  # custom nesting / thread stack
```

The script downloads `javax.mail:mail:1.4.4` from Maven Central, generates the
bomb, serves it over a local POP3 server, and runs an ingestion driver that
mirrors the common "fetch over POP3 → walk every MIME part" pattern. Expected
result: `StackOverflowError` and a non-zero JVM exit (the ingestion thread dies
without the `catch (Exception)` recovering).

### Representative output

```
[ingest] fetched 1 message(s) via POP3
Exception in thread "main" java.lang.StackOverflowError
    at ...MimeBodyPart.getContent(MimeBodyPart.java:637)
    at MailIngestDos.processMultipart(MailIngestDos.java:...)
    at MailIngestDos.processMultipart(MailIngestDos.java:...)
    ...
[*] JVM exit code = 1
```

## Files

| path | purpose |
|---|---|
| `src/gen_bomb.py` | generate the nested-multipart `.eml` |
| `src/pop3d.py` | minimal local POP3 server serving one message |
| `src/MailIngestDos.java` | ingestion driver (POP3 fetch + MIME walk) |
| `run.sh` | end-to-end local runner |

## Remediation

- **Upgrade** off `javax.mail:mail` 1.4.x to **Jakarta Mail / Eclipse Angus Mail**.
- **Bound the parser**: set `mail.mime.multipart.maxcount` (and related limits)
  where supported.
- **Bound your own walk**: cap recursion depth and total part count in the
  ingestion loop; catch `Throwable` (not just `Exception`) around per-message
  processing so one message cannot kill the worker thread.
- **Resource limits**: size caps on fetched messages, timeouts, isolate mail
  ingestion from other workloads.

## Disclaimer

For authorized testing, defensive research, and education only. The PoC runs
entirely against `127.0.0.1`; do not point it at systems you do not own or are
not authorized to test.
