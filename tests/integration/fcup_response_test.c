#include <assert.h>
#include <stdio.h>
#include "fcup_response.h"

int main(void) {
    const char playlist[] = "#EXTM3U\n#EXT-X-VERSION:3\n";
    assert(fcup_playlist_response_valid(0, playlist, sizeof(playlist)-1));
    assert(fcup_playlist_response_valid(200, playlist, sizeof(playlist)-1));
    assert(fcup_playlist_response_valid(206, playlist, sizeof(playlist)-1));
    assert(!fcup_playlist_response_valid(404, playlist, sizeof(playlist)-1));
    assert(!fcup_playlist_response_valid(500, playlist, sizeof(playlist)-1));
    assert(!fcup_playlist_response_valid(302, playlist, sizeof(playlist)-1));
    assert(!fcup_playlist_response_valid(0, NULL, 0));
    assert(!fcup_playlist_response_valid(0, "<html>error</html>", 18));
    assert(!fcup_playlist_response_valid(0, "#EXTM3Uevil\n", 12));
    assert(!fcup_playlist_response_valid(200, playlist, sizeof(playlist)));
    assert(!fcup_playlist_response_valid(0, "#EXTM3U", 7));
    assert(fcup_missing_prefetch_can_continue(404, false, 2, 3, 5));
    assert(fcup_missing_prefetch_can_continue(410, false, 4, 5, 5));
    assert(!fcup_missing_prefetch_can_continue(404, false, -1, 0, 5));
    assert(!fcup_missing_prefetch_can_continue(404, true, 2, 3, 5));
    assert(!fcup_missing_prefetch_can_continue(404, false, 1, 3, 5));
    assert(!fcup_missing_prefetch_can_continue(403, false, 2, 3, 5));
    assert(!fcup_missing_prefetch_can_continue(500, false, 2, 3, 5));
    assert(!fcup_missing_prefetch_can_continue(404, false, 5, 6, 5));
    puts("PASS: FCUP body validation and missing-rendition progression guards");
}
