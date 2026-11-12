// Unit tests for the M-Pesa reader. Build and run with native\build.ps1.
#include <iostream>
#include <string>

#include "../mpesa_parser.hpp"

static int failures = 0;

static void expectParsed(const std::string& name, const std::string& sms, const std::string& code,
                         const std::string& amount, const std::string& sender, const std::string& time) {
    auto m = daftari::parseMpesa(sms);
    if (!m) {
        std::cout << "FAIL " << name << ": not parsed\n";
        ++failures;
        return;
    }
    if (m->code != code || m->amount != amount || m->sender != sender || m->time != time) {
        std::cout << "FAIL " << name << ": got " << m->code << " | " << m->amount << " | " << m->sender << " | "
                  << m->time << "\n";
        ++failures;
        return;
    }
    std::cout << "ok   " << name << "\n";
}

static void expectRejected(const std::string& name, const std::string& sms) {
    if (daftari::parseMpesa(sms)) {
        std::cout << "FAIL " << name << ": should not parse\n";
        ++failures;
    } else {
        std::cout << "ok   " << name << "\n";
    }
}

int main() {
    expectParsed("received with comma amount and PM time",
                 "QWE12ABCDE Confirmed. You have received Ksh1,500.00 from JOHN DOE 0712345678 on 11/11/26 at 2:30 PM. "
                 "New Account balance is Ksh3,000.00.",
                 "QWE12ABCDE", "1500.00", "JOHN DOE", "2026-11-11 14:30:00");

    expectParsed("midnight is 12 AM",
                 "ABC1234XYZ Confirmed. You have received Ksh250 from MARY WANJIKU 0722000111 on 1/1/2026 at 12:05 AM.",
                 "ABC1234XYZ", "250", "MARY WANJIKU", "2026-01-01 00:05:00");

    expectParsed("noon is 12 PM",
                 "ZZ99AA11BB Confirmed. You have received Ksh60 from PETER 254712345678 on 5/3/26 at 12:00 PM.",
                 "ZZ99AA11BB", "60", "PETER", "2026-03-05 12:00:00");

    expectParsed("masked phone number and decimals",
                 "KLM4567890 Confirmed. You have received Ksh1,350.50 from GRACE 0712*****67 on 30/10/2026 at 9:15 AM.",
                 "KLM4567890", "1350.50", "GRACE", "2026-10-30 09:15:00");

    expectRejected("sent message is skipped",
                   "QWE12ABCDE Confirmed. Ksh500.00 sent to SHOP on 11/11/26 at 2:30 PM.");
    expectRejected("no code", "You have received Ksh500 from JOHN on 11/11/26 at 2:30 PM.");
    expectRejected("empty text", "");
    expectRejected("impossible date 31 February",
                   "QWE12ABCDE Confirmed. You have received Ksh500 from JOHN on 31/2/26 at 2:30 PM.");

    if (failures) {
        std::cout << failures << " test(s) failed\n";
        return 1;
    }
    std::cout << "all tests passed\n";
    return 0;
}
