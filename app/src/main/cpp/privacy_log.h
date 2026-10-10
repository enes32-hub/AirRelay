#ifndef AIRRELAY_PRIVACY_LOG_H
#define AIRRELAY_PRIVACY_LOG_H

#include <cctype>
#include <string>

// Output adapter only. NEVER use the returned value in protocol processing.
inline std::string airrelay_safe_log(const char *message) {
    if (!message) return {};
    std::string text(message);
    if (text.size() > 4096) text.resize(4096);
    std::string lower(text);
    for (char &c : lower) c = static_cast<char>(std::tolower(static_cast<unsigned char>(c)));
    const char *secrets[] = {"password", "passphrase", "authorization", "token", "secret",
        "private key", "private_key", "session-key", "session_key", "aeskey", "aesiv",
        "aes-key", "aes_key", "active-remote", "active_remote", "pairing code",
        "http://", "https://", "rtsp://"};
    for (const char *word : secrets) {
        if (lower.find(word) != std::string::npos) return "[sensitive diagnostic omitted]";
    }
    for (std::size_t pos = lower.find("pin"); pos != std::string::npos;
            pos = lower.find("pin", pos + 3)) {
        const auto is_word = [](char c) { return std::isalnum(static_cast<unsigned char>(c)); };
        if ((pos == 0 || !is_word(lower[pos - 1])) &&
                (pos + 3 == lower.size() || !is_word(lower[pos + 3]))) {
            return "[sensitive diagnostic omitted]";
        }
    }
    for (char &c : text) if (c == '\n' || c == '\r') c = ' ';
    return text;
}
#endif
