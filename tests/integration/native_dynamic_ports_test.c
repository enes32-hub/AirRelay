#include <assert.h>
#include <stdio.h>
#include "compat.h"
#include "netutils.h"

int main(void) {
    assert(netutils_init() == 0);
    unsigned short ports[5] = {0};
    int sockets[5];
    for (int i = 0; i < 5; ++i) {
        sockets[i] = netutils_init_socket(&ports[i], 0, i >= 2);
        assert(sockets[i] != -1 && ports[i] > 0);
        if (i < 2) assert(listen(sockets[i], 1) == 0);
        struct sockaddr_in address;
        socklen_t length = sizeof(address);
        assert(getsockname(sockets[i], (struct sockaddr *)&address, &length) == 0);
        assert(ntohs(address.sin_port) == ports[i]);
        for (int j = 0; j < i; ++j)
            if ((i >= 2) == (j >= 2)) assert(ports[i] != ports[j]);
    }
    for (int i = 0; i < 5; ++i) CLOSESOCKET(sockets[i]);
    netutils_cleanup();
    puts("PASS native dynamic ports: 2 TCP/3 UDP concurrently bound, actual ports returned, no same-protocol collision");
    return 0;
}
