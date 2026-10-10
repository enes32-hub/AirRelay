#include <assert.h>
#include <stdio.h>
#include "airplay_rate.h"

int main(void) {
    float value = -123.0f;
    const char *bad[] = {NULL, "", "/rate", "/rate?", "/rate?x", "/rate?x=1",
        "/rate?value", "/rate?value=", "/rate?value=nan", "/rate?value=NaN",
        "/rate?value=inf", "/rate?value=-inf", "/rate?value=1e999",
        "/rate?value=1e-999", "/rate?value=1e-50", "/rate?value=-1",
        "/rate?value=4.01", "/rate?value=1junk", "/rate?value= 1",
        "/rate?value=0x1p0", "/rate?value=1&value=0", "/rate?xvalue=1",
        "/rate?value=1#foo", "/rateX?value=1", "/rate?value=+",
        "/rate?value=1.0.0", "/rate?value=1  ", "/rate?x=value=1"};
    for (unsigned i = 0; i < sizeof(bad)/sizeof(bad[0]); ++i) {
        assert(!airplay_parse_rate(bad[i], &value));
        assert(value == -123.0f); /* rejected input cannot implicitly pause */
    }
    assert(!airplay_parse_rate("/rate?value=1", NULL));
    assert(airplay_parse_rate("/rate?value=1.000000", &value) && value == 1.0f);
    assert(airplay_parse_rate("/rate?value=0", &value) && value == 0.0f);
    assert(airplay_parse_rate("/rate?value=0.5", &value) && value == 0.5f);
    assert(airplay_parse_rate("/rate?extra=1&value=2&other=x", &value) && value == 2.0f);
    assert(airplay_parse_rate("/rate?value=4", &value) && value == 4.0f);
    assert(airplay_parse_rate("/rate?value=1e0", &value) && value == 1.0f);
    puts("PASS: 35 native AirPlay rate parser cases; malformed input leaves player value unchanged");
    return 0;
}
