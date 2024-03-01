Backport of CVE fixes to H2 1.4.200

https://github.com/h2database/h2database/issues/3360#issuecomment-1018351050

Security fixes since 1.4.200 (issues fixed in 1.4.200 or in previous versions aren't listed here):

    GRANT INSERT, UPDATE, DELETE (good luck with this one): 8cf8869 — preparation, 15cf1a2 — fix
    SQLXML: d83285f — fix, a21e3ca — cleanup
    JNDI: 956c624
    Host name validation in built-in web server: 456c2d0, 1392152, 0f83f48, 1c0ca27
    Remote database creation: eb75633

There should be also an additional fix for remote shutdown of TCP server somewhere between 1.4.200 and 2.0.202, but I can't find it.

# Welcome to H2, the Java SQL database. [![Build Status](https://travis-ci.org/h2database/h2database.svg?branch=master)](https://travis-ci.org/h2database/h2database)

## The main features of H2 are:

1. Very fast, open source, JDBC API
2. Embedded and server modes; in-memory databases
3. Browser based Console application
4. Small footprint: around 2 MB jar file size

More information: https://h2database.com

## Features

| | [H2](https://h2database.com/) | [Derby](https://db.apache.org/derby) | [HSQLDB](http://hsqldb.org) | [MySQL](https://www.mysql.com/) | [PostgreSQL](https://www.postgresql.org) |
|--------------------------------|---------|---------|---------|-------|---------|
| Pure Java                      | Yes     | Yes     | Yes     | No    | No      |
| Memory Mode                    | Yes     | Yes     | Yes     | No    | No      |
| Encrypted Database             | Yes     | Yes     | Yes     | No    | No      |
| ODBC Driver                    | Yes     | No      | No      | Yes   | Yes     |
| Fulltext Search                | Yes     | No      | No      | Yes   | Yes     |
| Multi Version Concurrency      | Yes     | No      | Yes     | Yes   | Yes     |
| Footprint (embedded database)  | ~2 MB   | ~3 MB   | ~1.5 MB | —     | —       |
| Footprint (JDBC client driver) | ~500 KB | ~600 KB | ~1.5 MB | ~1 MB | ~700 KB |
