#include "privacy_log.h"
#include <cassert>
#include <iostream>
int main() {
    int n = 0;
    const char *secrets[] = {"PIN: 1234", "pin is 0123", "device_pin=6789", "PIN = 9999",
        "password=raw", "Authorization: Basic xyz", "token=foo", "aes_key=hello",
        "http://private.invalid/file", "pairing code 9876"};
    for (const char *text : secrets) { assert(airrelay_safe_log(text) == "[sensitive diagnostic omitted]"); ++n; }
    assert(airrelay_safe_log("Server started on port 7000") == "Server started on port 7000"); ++n;
    assert(airrelay_safe_log("Frame pacing 60 fps") == "Frame pacing 60 fps"); ++n;
    assert(airrelay_safe_log(nullptr).empty()); ++n;
    assert(airrelay_safe_log("a\nb\rc") == "a b c"); ++n;
    assert(airrelay_safe_log(std::string(10000, 'a').c_str()).size() <= 4096); ++n;
    std::cout << "C++ privacy output adapter: " << n << " checks passed. No receiver build.\n";
}
