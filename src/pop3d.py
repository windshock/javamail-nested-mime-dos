#!/usr/bin/env python3
"""Minimal POP3 server that serves a single message file.
For LOCAL testing only: lets a JavaMail POP3 client fetch the PoC email,
exactly as a real ingestion batch would pull mail from a mailbox."""
import socket, threading, sys

HOST, PORT = "127.0.0.1", 11110
MSG = open(sys.argv[1] if len(sys.argv) > 1 else "bomb.eml", "rb").read()

def dotstuff(raw: bytes) -> bytes:
    return b"\r\n".join((b"." + ln) if ln.startswith(b".") else ln
                        for ln in raw.split(b"\r\n"))

def handle(c):
    f = c.makefile("rwb", buffering=0)
    def send(s): f.write(s.encode() + b"\r\n")
    send("+OK local POP3 ready")
    while True:
        line = f.readline()
        if not line: break
        up = line.decode(errors="replace").strip().upper()
        if up.startswith("USER"): send("+OK")
        elif up.startswith("PASS"): send("+OK logged in")
        elif up.startswith("CAPA"): send("+OK"); send("USER"); send("UIDL"); send("TOP"); send(".")
        elif up.startswith("STAT"): send("+OK 1 %d" % len(MSG))
        elif up.startswith("LIST"):
            if len(up.split()) > 1: send("+OK 1 %d" % len(MSG))
            else: send("+OK 1 messages"); send("1 %d" % len(MSG)); send(".")
        elif up.startswith("UIDL"):
            if len(up.split()) > 1: send("+OK 1 UID0001")
            else: send("+OK"); send("1 UID0001"); send(".")
        elif up.startswith("TOP"):
            send("+OK"); f.write(dotstuff(MSG.split(b"\r\n\r\n")[0])); f.write(b"\r\n.\r\n")
        elif up.startswith("RETR"):
            send("+OK %d octets" % len(MSG)); f.write(dotstuff(MSG)); f.write(b"\r\n.\r\n")
        elif up.startswith(("DELE", "NOOP", "RSET")): send("+OK")
        elif up.startswith("QUIT"): send("+OK bye"); break
        else: send("-ERR unknown")
    c.close()

srv = socket.socket(); srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
srv.bind((HOST, PORT)); srv.listen(5)
print("POP3 listening on %s:%d" % (HOST, PORT), flush=True)
while True:
    conn, _ = srv.accept()
    threading.Thread(target=handle, args=(conn,), daemon=True).start()
