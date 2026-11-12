// Daftari M-Pesa reader (FR-16): reads one Safaricom "received" SMS and
// extracts the transaction code, amount, sender and time.
// Patterns match the Java reader it replaces (see README).
#ifndef DAFTARI_MPESA_PARSER_HPP
#define DAFTARI_MPESA_PARSER_HPP

#include <optional>
#include <string>

namespace daftari {

struct MpesaMessage {
    std::string code;     // 10 characters, e.g. "QWE12ABCDE"
    std::string amount;   // plain decimal without commas, e.g. "1500.00"
    std::string sender;   // e.g. "JOHN DOE"
    std::string time;     // "YYYY-MM-DD HH:MM:SS"
};

// Returns std::nullopt when the message is not a readable "received" payment.
std::optional<MpesaMessage> parseMpesa(const std::string& sms);

}  // namespace daftari

#endif
