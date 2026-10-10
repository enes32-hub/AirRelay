#ifndef AIRPLAY_RATE_H
#define AIRPLAY_RATE_H

#include <stdbool.h>
#include <errno.h>
#include <math.h>
#include <stdlib.h>
#include <string.h>

/* Parse the sender's /rate?value= syntax, not an arbitrary first '='.
 * Unsupported/malformed rates must not reach the player as a pause or NaN.
 * The Android player supports forward playback through 4x; 0 is pause. */
static inline bool airplay_parse_rate(const char *url, float *result) {
    if (!url || !result) return false;
    const char *query = strchr(url, '?');
    if (!query || query - url != 5 || strncmp(url, "/rate", 5)) return false;
    bool found = false;
    float parsed = 0.0f;
    for (const char *field = query + 1; *field;) {
        const char *limit = strchr(field, '&');
        if (!limit) limit = field + strlen(field);
        const char *equals = memchr(field, '=', (size_t)(limit - field));
        if (equals && equals - field == 5 && !strncmp(field, "value", 5)) {
            if (found || equals + 1 == limit) return false;
            const char *start = equals + 1;
            for (const char *p = start; p < limit; p++) {
                if (!(*p >= '0' && *p <= '9') && *p != '.' && *p != '+' &&
                    *p != '-' && *p != 'e' && *p != 'E') return false;
            }
            errno = 0;
            char *end = NULL;
            double value = strtod(start, &end);
            if (end != limit || end == start || errno == ERANGE || !isfinite(value) ||
                value < 0.0 || value > 4.0) return false;
            parsed = (float)value;
            if (value > 0.0 && parsed == 0.0f) return false;
            found = true;
        }
        field = *limit ? limit + 1 : limit;
    }
    if (found) *result = parsed;
    return found;
}
#endif
