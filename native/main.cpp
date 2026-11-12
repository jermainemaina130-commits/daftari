// Command-line front end for the M-Pesa reader.
//
// Reads one SMS from stdin (UTF-8), prints one tab-separated line on success:
//     CODE<TAB>AMOUNT<TAB>SENDER<TAB>YYYY-MM-DD HH:MM:SS
// and exits with 0. Prints nothing and exits with 2 if the message cannot be read.
// The Java servlet calls this program once per pasted message.

#include <cstdio>
#include <fcntl.h>
#include <io.h>
#include <iostream>
#include <iterator>
#include <string>

#include "mpesa_parser.hpp"

int main() {
    // Read raw bytes so Windows does not translate line endings.
    _setmode(_fileno(stdin), _O_BINARY);
    _setmode(_fileno(stdout), _O_BINARY);
    std::string sms((std::istreambuf_iterator<char>(std::cin)), std::istreambuf_iterator<char>());

    auto msg = daftari::parseMpesa(sms);
    if (!msg) return 2;

    std::cout << msg->code << '\t' << msg->amount << '\t' << msg->sender << '\t' << msg->time << '\n';
    return 0;
}
