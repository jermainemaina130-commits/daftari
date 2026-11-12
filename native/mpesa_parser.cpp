#include "mpesa_parser.hpp"

#include <cctype>
#include <cstdio>
#include <regex>

namespace daftari {

namespace {

// Same patterns as MpesaParser.java.
const std::regex& codeRe() {
    static const std::regex re(R"(^\s*([A-Z0-9]{10})\s+Confirmed)");
    return re;
}
const std::regex& amountRe() {
    static const std::regex re(R"(Ksh\s?([\d,]+(?:\.\d{1,2})?))", std::regex::icase);
    return re;
}
const std::regex& fromRe() {
    static const std::regex re(R"(from\s+(.+?)\s+(?:(?:\+?254|0)[\d*]{8,10}\s+)?on\s)", std::regex::icase);
    return re;
}
const std::regex& timeRe() {
    static const std::regex re(R"(on\s+(\d{1,2})/(\d{1,2})/(\d{2,4})\s+at\s+(\d{1,2}):(\d{2})\s*([AP]M))",
                               std::regex::icase);
    return re;
}

bool containsReceived(const std::string& s) {
    std::string lower = s;
    for (char& c : lower) c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    return lower.find("received") != std::string::npos;
}

bool isLeapYear(int y) { return (y % 4 == 0 && y % 100 != 0) || y % 400 == 0; }

int daysInMonth(int y, int m) {
    static const int days[] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
    return (m == 2 && isLeapYear(y)) ? 29 : days[m - 1];
}

}  // namespace

std::optional<MpesaMessage> parseMpesa(const std::string& sms) {
    if (!containsReceived(sms)) return std::nullopt;  // skip sent/paid messages

    std::smatch code, amt, from, when;
    if (!std::regex_search(sms, code, codeRe())) return std::nullopt;
    if (!std::regex_search(sms, amt, amountRe())) return std::nullopt;
    if (!std::regex_search(sms, when, timeRe())) return std::nullopt;

    int day = std::stoi(when[1]);
    int month = std::stoi(when[2]);
    int year = std::stoi(when[3]);
    int hour12 = std::stoi(when[4]);
    int minute = std::stoi(when[5]);
    if (year < 100) year += 2000;
    if (month < 1 || month > 12 || day < 1 || day > daysInMonth(year, month)) return std::nullopt;
    if (hour12 < 1 || hour12 > 12 || minute > 59) return std::nullopt;

    int hour = hour12 % 12;
    std::string meridiem = when[6];
    for (char& c : meridiem) c = static_cast<char>(std::toupper(static_cast<unsigned char>(c)));
    if (meridiem == "PM") hour += 12;

    std::string sender;
    if (std::regex_search(sms, from, fromRe())) {
        sender = from[1];
        // trim surrounding whitespace, as the Java reader does with String.trim()
        size_t start = sender.find_first_not_of(" \t\r\n");
        size_t end = sender.find_last_not_of(" \t\r\n");
        sender = (start == std::string::npos) ? "" : sender.substr(start, end - start + 1);
    }

    std::string amount;
    for (char c : std::string(amt[1])) {
        if (c != ',') amount.push_back(c);
    }

    char buf[32];
    std::snprintf(buf, sizeof buf, "%04d-%02d-%02d %02d:%02d:00", year, month, day, hour, minute);

    MpesaMessage msg;
    msg.code = code[1];
    msg.amount = amount;
    msg.sender = sender;
    msg.time = buf;
    return msg;
}

}  // namespace daftari
