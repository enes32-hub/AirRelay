#include <assert.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include "raop.h"
#include "airplay_video.h"

int main(void) {
    /* The store does not dereference raop; no network or decoder is involved. */
    int owner = 1;
    airplay_video_t *video = airplay_video_init((raop_t *)&owner, 12345, "en", "en", "en");
    char *uris[] = {strdup("mlhls://test/repeated.m3u8"), strdup("mlhls://test/repeated.m3u8")};
    create_media_data_store(video, uris, 2);
    /* First occurrence failed. The next occurrence succeeds and must not
     * strcmp a null playlist or alias the unavailable first occurrence. */
    char *body = strdup("#EXTM3U\n#EXT-X-TARGETDURATION:2\n#EXTINF:2,\nsegment.ts\n#EXT-X-ENDLIST\n");
    int count = 1;
    float duration = 2;
    bool endlist = true;
    assert(store_media_playlist(video, body, &count, &duration, &endlist, 1) == 0);
    assert(get_media_uri_index_by_remote_url(video, "mlhls://test/unknown.m3u8") == -1);
    airplay_video_destroy(video);
    puts("PASS: missing first rendition followed by successful duplicate does not crash");
}
