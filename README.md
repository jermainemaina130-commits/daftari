# Daftari

Website for small shops in Nairobi: stock, sales, M-Pesa SMS matching and credit.
Java servlets (Jakarta EE 10), MySQL, plain HTML. KCA Diploma in IT project, supervisor Luke Mbom.

## Run it
1. Install JDK 17+, Maven, MySQL 8 and Tomcat 10.1+, plus a C++17 compiler (g++ / MinGW-w64) for the M-Pesa reader.
2. `mysql -u root -p < sql/schema.sql`
3. Set `DB_URL`, `DB_USER`, `DB_PASS` if yours differ from the defaults in `Db.java`.
4. Build the M-Pesa reader: `powershell -File native\build.ps1` (builds `native\bin\mpesa_reader.exe` and runs its tests).
5. Set `MPESA_READER` to the full path of `native\bin\mpesa_reader.exe` in the environment Tomcat starts in.
6. `mvn package`, then copy `target/daftari.war` into Tomcat's `webapps/`.
7. Open `http://localhost:8080/daftari/setup` once to create your shop and owner login.

## M-Pesa reader (C++)
`native/mpesa_parser.cpp` reads one Safaricom "received" SMS and prints `CODE<TAB>AMOUNT<TAB>SENDER<TAB>YYYY-MM-DD HH:MM:SS`.
`native/main.cpp` is the command-line program: it reads one message on stdin and exits 0 (readable), 2 (unreadable).
`MpesaParser.java` runs it once per pasted message. If `MPESA_READER` is not set, the app throws an error instead of marking every message unreadable.
Unit tests: `native/tests/test_parser.cpp`, run by `native/build.ps1`.

## Built so far (SRS ref)
- Login, hashed passwords, 15 min session timeout, shop-only data (FR-1, FR-4, NFR-6, NFR-7)
- Products, restock, owner-only buying price (FR-5, FR-7, FR-3)
- Multi-item sales: cash / M-Pesa / credit, stock lowered in one transaction (FR-6, FR-11, FR-12, FR-27, NFR-9)
- M-Pesa paste, read by the C++ reader, duplicate rejection, unreadable list, exact-amount match suggestion (FR-15 to FR-17, FR-19, FR-20)
- Home: day total, low-stock warning, unmatched payments (FR-13, FR-21, FR-23 to FR-25)

## Still to build
- Add/remove attendants (FR-2), cancel sale (FR-14)
- Multi-payment matching, 1,500 + 1,500 = 3,000 (FR-18)
- Stock count and missing stock (FR-8, FR-9), selling by heap/packet pricing (FR-10)
- Fast-seller ranking (FR-22), customer payments and owing list (FR-28, FR-29)
- Profit and date-range reports (FR-30 to FR-33)
