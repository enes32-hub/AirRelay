#include <assert.h>
#include <stdlib.h>
#include <stdio.h>
#include "raop.h"

int main(void) {
    /* init only stores raop; these tests do not call network/server routines. */
    airplay_video_t *old = airplay_video_init((raop_t *) 1, 7000, NULL, NULL, NULL);
    airplay_video_t *next = airplay_video_init((raop_t *) 1, 7000, NULL, NULL, NULL);
    assert(old && next);
    int a = get_next_FCUP_RequestID(old);
    int b = get_next_FCUP_RequestID(next);
    assert(a != b);
    assert(!accept_fcup_response(next, a));
    assert(accept_fcup_response(next, b));
    assert(!accept_fcup_response(next, b));
    assert(accept_fcup_response(old, a));
    assert(!accept_fcup_response(old, -1));
    int ids[128];
    for (int i = 0; i < 128; i++) ids[i] = get_next_FCUP_RequestID(next);
    for (int i = 127; i >= 0; i--) assert(accept_fcup_response(next, ids[i]));
    int cancelled = get_next_FCUP_RequestID(old);
    cancel_fcup_requests(old);
    assert(!accept_fcup_response(old, cancelled));
    assert(!accept_fcup_response(old, get_next_FCUP_RequestID(old)));
    resume_fcup_requests(old);
    assert(!accept_fcup_response(old, cancelled));
    assert(accept_fcup_response(old, get_next_FCUP_RequestID(old)));
    airplay_video_destroy(old);
    airplay_video_destroy(next);
    puts("PASS: distinct media generations; stale, duplicate, missing and reordered FCUP replies");
    return 0;
}
