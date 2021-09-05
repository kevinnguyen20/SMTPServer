# SMTPServer

A simple SMTP server storing your emails locally.

## How to use it

1. Execute  
```SMTPServer.java```.

2. Select a networking utility of your choice (e.g. netcat or telnet) and run, let's say,  
```telnet localhost 8000```.

3. Use the implemented SMTP-commands in the list below and have fun!

## Implemented SMTP-Commands

      - HELO <SP> <domain> <CRLF>

      - MAIL <SP> FROM:<reverse-path> <CRLF>

      - RCPT <SP> TO:<forward-path> <CRLF>

      - DATA <CRLF>

      - HELP [<SP> <string>] <CRLF>

      - QUIT <CRLF>

## Reply codes by function groups (not completely implemented)

      - 500 Syntax error, command unrecognized 
      [This may include errors such as command line too long]
      - 501 Syntax error in parameters or arguments
      - 502 Command not implemented
      - 503 Bad sequence of commands
      - 504 Command parameter not implemented

----

      - 211 System status, or system help reply
      - 214 Help message 
      [Information on how to use the receiver or the meaning of a particular non-standard command;
      this reply is useful only to the human user]

----

      - 220 \<domain> Service ready
      - 221 \<domain> Service closing transmission channel
      - 421 \<domain> Service not available, closing transmission channel 
      [This may be a reply to any command if the service knows it must shut down]

----

      - 250 Requested mail action okay, completed
      - 251 User not local; will forward to \<forward-path>
      - 450 Requested mail action not taken: mailbox unavailable [E.g., mailbox busy]
      - 550 Requested action not taken: mailbox unavailable [E.g., mailbox not found, no access]
      - 451 Requested action aborted: error in processing
      - 551 User not local; please try \<forward-path>
      - 452 Requested action not taken: insufficient system storage
      - 552 Requested mail action aborted: exceeded storage allocation
      - 553 Requested action not taken: mailbox name not allowed
      [E.g., mailbox syntax incorrect]
      - 354 Start mail input; end with \<CRLF>.\<CRLF>
      - 554 Transaction failed

## Command-Reply sequences

      S success
      F failure
      E error
      I intermediate

      CONNECTION ESTABLISHMENT
         S: 220
         F: 421
      HELO
         S: 250
         E: 500, 501, 504, 421
      MAIL
         S: 250
         F: 552, 451, 452
         E: 500, 501, 421
      RCPT
         S: 250, 251
         F: 550, 551, 552, 553, 450, 451, 452
         E: 500, 501, 503, 421
      DATA
         I: 354 -> data -> S: 250
                           F: 552, 554, 451, 452
         F: 451, 554
         E: 500, 501, 503, 421
      HELP
         S: 211, 214
         E: 500, 501, 502, 504, 421
      QUIT
         S: 221
         E: 500


For further reading check out <https://datatracker.ietf.org/doc/html/rfc821/>
