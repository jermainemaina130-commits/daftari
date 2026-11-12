# Daftari

Website for small shops in Nairobi: stock, sales, M-Pesa SMS matching and credit.
Java servlets (Jakarta EE 10), MySQL, plain HTML. KCA Diploma in IT project, supervisor Luke Mbom.

## Run it
1. Install JDK 17+, Maven, MySQL 8 and Tomcat 10.1+.
2. `mysql -u root -p < sql/schema.sql`
3. Set `DB_URL`, `DB_USER`, `DB_PASS` if yours differ from the defaults in `Db.java`.
4. `mvn package`, then copy `target/daftari.war` into Tomcat's `webapps/`.
5. Open `http://localhost:8080/daftari/setup` once to create your shop and owner login.

## Built so far (SRS ref)
- Login, hashed passwords, 15 min session timeout, shop-only data (FR-1, FR-4, NFR-6, NFR-7)
- Products, restock, owner-only buying price (FR-5, FR-7, FR-3)
- Multi-item sales: cash / M-Pesa / credit, stock lowered in one transaction (FR-6, FR-11, FR-12, FR-27, NFR-9)
- M-Pesa paste, read, duplicate rejection, unreadable list, exact-amount match suggestion (FR-15 to FR-17, FR-19, FR-20)
- Home: day total, low-stock warning, unmatched payments (FR-13, FR-21, FR-23 to FR-25)

## Still to build
- Add/remove attendants (FR-2), cancel sale (FR-14)
- Multi-payment matching, 1,500 + 1,500 = 3,000 (FR-18)
- Stock count and missing stock (FR-8, FR-9), selling by heap/packet pricing (FR-10)
- Fast-seller ranking (FR-22), customer payments and owing list (FR-28, FR-29)
- Profit and date-range reports (FR-30 to FR-33)
- C++ message reader: `MpesaParser.java` does this job for now, same inputs and outputs
