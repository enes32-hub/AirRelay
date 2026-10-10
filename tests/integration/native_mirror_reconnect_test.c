#include <assert.h>
#include <stdio.h>
#include <stdatomic.h>
#include "compat.h"
#include "netutils.h"
#include "raop.h"
#include "raop_rtp_mirror.h"
#include "raop_ntp.h"

static atomic_int starts, ends;
static void test_log(void *ctx, int level, const char *msg) {
    (void)ctx; (void)level; fprintf(stderr, "%s\n", msg);
}
static void running(void *ctx, bool active) {
    (void) ctx;
    if (active) atomic_fetch_add(&starts, 1); else atomic_fetch_add(&ends, 1);
}
static void wait_count(atomic_int *counter, int expected) {
    for (int i=0; i<500 && atomic_load(counter)<expected; ++i) usleep(10000);
    if (atomic_load(counter)!=expected) {
        fprintf(stderr, "FAIL worker callback: expected=%d actual=%d\n", expected, atomic_load(counter));
        _exit(2);
    }
}
int main(void) {
    fprintf(stderr,"stage netutils\n"); assert(netutils_init()==0);
    fprintf(stderr,"stage ntp global\n"); raop_ntp_global_init();
    fprintf(stderr,"stage logger\n"); logger_t *log=logger_init(); assert(log);
    logger_set_level(log, LOGGER_ERR);
    logger_set_callback(log, test_log, NULL);
    raop_callbacks_t cb={0}; cb.mirror_video_running=running;
    unsigned short remote_port=0;
    int remote=netutils_init_socket(&remote_port, 0, 1); assert(remote!=-1);
    timing_protocol_t protocol=NTP;
    fprintf(stderr,"stage ntp init\n"); raop_ntp_t *ntp=raop_ntp_init(log, &cb, "127.0.0.1", 4, remote_port, &protocol); assert(ntp);
    unsigned char key[16]={0};
    fprintf(stderr,"stage mirror init\n"); raop_rtp_mirror_t *mirror=raop_rtp_mirror_init(log, &cb, ntp, "127.0.0.1", 4, key); assert(mirror);
    for (int round=1; round<=8; ++round) {
        fprintf(stderr,"round %d\n",round); unsigned short port=0;
        raop_rtp_mirror_start(mirror, &port, 0); assert(port>0);
        wait_count(&starts, round);
        unsigned short repeated=0;
        raop_rtp_mirror_start(mirror, &repeated, 0); assert(repeated==port);
        int fd=socket(AF_INET, SOCK_STREAM, 0); assert(fd>=0);
        struct sockaddr_in addr={0}; addr.sin_family=AF_INET;
        addr.sin_addr.s_addr=htonl(INADDR_LOOPBACK); addr.sin_port=htons(port);
        assert(connect(fd, (struct sockaddr *)&addr, sizeof(addr))==0);
        /* A partial header then RST recreates an interrupted TCP stream, without
           authentication bypass, encrypted media or changes to the TV network. */
        char partial=0; assert(send(fd, &partial, 1, 0)==1);
        usleep(50000);
        struct linger abortive={1,0};
        assert(setsockopt(fd,SOL_SOCKET,SO_LINGER,(const char *)&abortive,sizeof(abortive))==0);
        CLOSESOCKET(fd);
        wait_count(&ends, round);
    }
    raop_rtp_mirror_destroy(mirror);
    raop_ntp_destroy(ntp);
    CLOSESOCKET(remote);
    logger_destroy(log);
    netutils_cleanup();
    puts("PASS: 8 actual mirror TCP resets and SETUP restarts; duplicate SETUP port stable; ended worker joined on destruction. Not an iPhone/VPN acceptance test.");
    return 0;
}
