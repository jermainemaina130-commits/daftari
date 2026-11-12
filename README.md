# Daftari

**Daftari** is a website for small shops in Nairobi. It keeps stock records, records sales, matches M-Pesa payments to sales, and tracks customer credit.

KCA University, Diploma in Information Technology. Author: Jamie Muriuki (24/01071). Supervisor: Luke Mbom.

## What it does

| Area | Feature | SRS |
|---|---|---|
| Login | Owner login with hashed passwords, 15-minute session timeout, data kept per shop | FR-1, FR-4, NFR-6, NFR-7 |
| Stock | Add products with buying and selling prices, restock, owner-only buying prices | FR-5, FR-7, FR-3 |
| Sales | Multi-item sales paid by cash, M-Pesa or credit; stock falls in the same database transaction | FR-6, FR-11, FR-12, NFR-9 |
| M-Pesa | Paste SMS messages; a C++ reader extracts code, amount, sender and time; duplicates are rejected; unreadable messages are listed; a matching sale is suggested | FR-15 to FR-17, FR-19, FR-20 |
| Home | Day totals by payment type, low-stock warning, unmatched payments warning | FR-13, FR-21, FR-23 to FR-25 |
| Credit | Credit sales add to a customer's balance | FR-27 |

Not yet built (planned for later versions): attendant accounts, cancelling a sale, matching several payments to one sale, stock count and missing-stock report, fast-seller ranking, customer payments and owing list, profit and date-range reports.

## Architecture

- **Web application:** Java 17 servlets (Jakarta EE 10) on Apache Tomcat 10.1. Plain HTML, mobile first.
- **M-Pesa reader:** C++17 program in `native/`. The Java servlet runs it once per pasted message.
- **Database:** MySQL 8.4, schema in `sql/schema.sql`.
- **Build:** Maven (`pom.xml`) builds `daftari.war`.

```
Browser -> Tomcat (daftari.war, Java servlets) -> MySQL
                          |
                          +-> mpesa_reader.exe (C++, one SMS per call)
```

## Run it

Windows, with JDK 17, Maven, Git, MySQL 8.4, Tomcat 10.1 and a C++ compiler (g++) installed:

```powershell
$env:DEMO_OWNER_PASSWORD = 'choose-a-password'   # optional: creates the demo owner on an empty database
powershell -ExecutionPolicy Bypass -File scripts\run-local.ps1
```

The script starts MySQL, loads the schema if needed, builds and tests the M-Pesa reader, builds the WAR, restarts Tomcat, and prints the address. Then open **http://localhost:8080/daftari/**.

To run by hand:

1. `mysql -u root -p < sql/schema.sql`
2. `powershell -File native\build.ps1` (builds `native\bin\mpesa_reader.exe` and runs its tests)
3. Set the `MPESA_READER` environment variable to the full path of `native\bin\mpesa_reader.exe`.
4. `mvn package`, then copy `target/daftari.war` into Tomcat's `webapps` folder.
5. Open `/daftari/setup` once to create the first shop and owner. It locks after the first account exists.

## Demo login

Local demo account (for the presentation only):

- **Phone:** `0700000000`
- **Password:** `test-pass-123`
- **Shop:** Daftari Test Shop, Icipe Road, Kasarani

## How the app works: walkthrough

1. **Log in.** Enter the phone and password. The home page shows "Hello, Test Owner (owner)".
2. **Home.** Shows today's total by payment type (cash, M-Pesa, credit), and a warning when M-Pesa payments are not matched yet. The demo shows **KES 2,630** for the day and one unmatched payment.
3. **Stock.** The table shows each product, quantity left, selling price and (owner only) buying price, with an "Add" box to restock. Use "New product" to add one.
4. **Sale.** Enter the quantity of each item and choose the payment type. For credit, choose or add a customer. Stock falls as the sale is saved. The demo has:
   - a cash sale of 3 milk, 1 sugar and 1 tray of eggs, total KES 680;
   - an M-Pesa sale of 1 gas refill, total KES 1,500;
   - a credit sale of 5 milk and 1 sugar to Mama Otieno, total KES 450, which adds to her balance.
5. **M-Pesa.** Paste an M-Pesa "received" message. The reader extracts the code, amount, sender and time and lists the payment as not matched. The demo paste of a KES 1,500 payment from JOHN DOE suggests **"Match to sale #2"**. Accepting it links the payment to that sale. Pasting the same message again is rejected as already recorded. Text that is not an M-Pesa message is listed as "Could not read".

Sample message used for the demo:

```
UBK7Q4XR2M Confirmed. You have received Ksh1,500.00 from JOHN DOE 0712345678 on 11/11/26 at 2:30 PM. New Account balance is Ksh3,000.00.
```

## Testing

- **Reader unit tests:** 8 tests in `native/tests/test_parser.cpp`, run by `native\build.ps1`. They cover received and sent messages, comma amounts, AM/PM and midnight/noon times, masked phone numbers and impossible dates. All pass.
- **Accuracy target:** the SRS asks for 90% of 50 real messages read correctly. That test is not done yet; it needs real sample messages.
- **End-to-end:** the sale, stock, credit, M-Pesa paste, duplicate rejection and match flows were run through the web app against MySQL.

## Repository layout

```
src/main/java/ke/daftari/   servlets, Page layout, database access, Java M-Pesa wrapper
src/main/webapp/WEB-INF/    web.xml
sql/schema.sql              database schema
native/                     C++ M-Pesa reader and its tests
scripts/run-local.ps1       starts the whole stack on Windows
```
