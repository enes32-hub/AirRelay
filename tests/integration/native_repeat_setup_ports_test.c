#include <assert.h>
#include <stdio.h>
#include "compat.h"
#include "netutils.h"
#include "raop.h"
#include "raop_rtp.h"
#include "raop_rtp_mirror.h"
#include "raop_ntp.h"

/* Runs the actual library transports without an iPhone: repeated SETUP inputs
   are zero each time, just as native auto-port configuration supplies them. */
int main(void) {
    assert(netutils_init() == 0);
    raop_ntp_global_init();
    logger_t *logger = logger_init();
    assert(logger);
    logger_set_level(logger, LOGGER_ERR);
    raop_callbacks_t callbacks = {0};
    unsigned short remote_port = 0;
    int remote_socket = netutils_init_socket(&remote_port, 0, 1);
    assert(remote_socket != -1);
    timing_protocol_t protocol = NTP;
    raop_ntp_t *ntp = raop_ntp_init(logger, &callbacks, "127.0.0.1", 4, remote_port, &protocol);
    assert(ntp);
    unsigned short timing = 0;
    raop_ntp_start(ntp, &timing);
    assert(timing > 0);
    const unsigned short initial_timing = timing;
    unsigned char key[16] = {0}, iv[16] = {0};
    raop_rtp_mirror_t *mirror = raop_rtp_mirror_init(logger, &callbacks, ntp, "127.0.0.1", 4, key);
    assert(mirror);
    unsigned short video = 0;
    raop_rtp_mirror_start(mirror, &video, 0);
    assert(video > 0);
    const unsigned short initial_video = video;
    raop_rtp_t *audio = raop_rtp_init(logger, &callbacks, ntp, "127.0.0.1", 4, key, iv);
    assert(audio);
    unsigned short control = 0, data = 0, remote_control = 0;
    unsigned char codec = 8;
    unsigned int sample_rate = 44100;
    raop_rtp_start_audio(audio, &remote_control, &control, &data, &codec, &sample_rate);
    assert(control > 0 && data > 0 && control != data);
    const unsigned short initial_control = control, initial_data = data;
    for (int i = 0; i < 25; ++i) {
        timing = video = control = data = 0;
        raop_ntp_start(ntp, &timing);
        raop_rtp_mirror_start(mirror, &video, 0);
        raop_rtp_start_audio(audio, &remote_control, &control, &data, &codec, &sample_rate);
        assert(timing == initial_timing && raop_ntp_get_port(ntp) == initial_timing);
        assert(video == initial_video);
        assert(control == initial_control && data == initial_data);
    }
    raop_rtp_destroy(audio);
    raop_rtp_mirror_destroy(mirror);
    raop_ntp_destroy(ntp);
    CLOSESOCKET(remote_socket);
    logger_destroy(logger);
    netutils_cleanup();
    puts("PASS: 25 repeated SETUP-like starts on actual NTP/mirror/audio transports return original nonzero bound ports");
    return 0;
}
