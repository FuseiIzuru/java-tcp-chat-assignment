# Concurrent TCP Chat (Java)

A console-based multi-party chat assignment using only the Java networking and thread APIs.

## Build and run

```text
javac ChatServer.java ChatClient.java
java ChatServer 5000 myAdminSecret
java ChatClient localhost 5000
```

Run the server first, then launch one client process per user. A normal user enters `alice`; a super user enters `admin myAdminSecret`. The password is the server's second command-line argument, or can be supplied through `CHAT_ADMIN_PASSWORD`.

## Commands

- Type a line to send a public message.
- `/private USER` opens a private pair channel.
- `/public` leaves the private pair channel.
- `/ban USER` permanently bans a username (super users only).
- `/quit` logs out.

The ban list is stored in `banned-users.txt` in the server's working directory. Read [EXPLANATION_CN.txt](EXPLANATION_CN.txt) for the Chinese walkthrough and [REPORT_EN_ZH.txt](REPORT_EN_ZH.txt) for the short bilingual report.