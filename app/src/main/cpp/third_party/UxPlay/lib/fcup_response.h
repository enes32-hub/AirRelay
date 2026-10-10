#ifndef AYNATV_FCUP_RESPONSE_H
#define AYNATV_FCUP_RESPONSE_H
#include <stdbool.h>
#include <stddef.h>
#include <string.h>

/* Session/request acceptance is checked by the caller first. Only the
 * currently outstanding initial child request can advance the sequence. */
static inline bool fcup_missing_prefetch_can_continue(int status, bool complete,
        int index, int next, int total) {
    return (status == 404 || status == 410) && !complete &&
           index >= 0 && index < total && next == index + 1;
}

/* FCUP may return a sender-resolved (non-HTTP) resource with status 0 or
 * no HTTP status. Accept that only with an actual HLS body, never an error
 * page or empty result. Request/session correlation remains mandatory. */
static inline bool fcup_playlist_response_valid(int status, const char *data, size_t length) {
    if (status != 0 && (status < 200 || status >= 300)) return false;
    if (!data || length < 8 || memcmp(data, "#EXTM3U", 7) != 0) return false;
    if (data[7] != '\n' && data[7] != '\r') return false;
    return memchr(data, '\0', length) == NULL;
}
#endif
