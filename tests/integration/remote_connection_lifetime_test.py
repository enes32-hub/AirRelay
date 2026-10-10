"""Execute the production HTTP classification branch with transport spies.

This isolates its destructive side effects; it is not encrypted AirPlay acceptance.
"""
from pathlib import Path
import json
import subprocess
import os

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(os.environ.get('AIRRELAY_NATIVE_TEST_OUT', str(ROOT / 'artifacts/rc25-direct-stall/native-lifetime')))
OUT.mkdir(parents=True, exist_ok=True)
PREFIX = r'''
#include <assert.h>
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
typedef struct { int live; } raop_rtp_mirror_t;
typedef raop_rtp_mirror_t raop_rtp_t;
typedef raop_rtp_mirror_t raop_ntp_t;
typedef struct { int connection_type; char *client_session_id;
raop_rtp_mirror_t *raop_rtp_mirror; raop_rtp_t *raop_rtp; raop_ntp_t *raop_ntp; } raop_conn_t;
typedef struct { void *httpd; void *logger; bool hls_support; } raop_t;
static raop_conn_t owner;
static int stopped;
#define CONNECTION_TYPE_RAOP 1
#define CONNECTION_TYPE_AIRPLAY 2
#define LOGGER_DEBUG 7
static void logger_log(void *p, int n, const char *s, ...) {}
static void httpd_set_connection_type(void *h, void *p, int t) {}
static void *httpd_get_connection_by_type(void *h, int t, int i) { return &owner; }
static void raop_rtp_mirror_stop(raop_rtp_mirror_t *t) { t->live=0; stopped++; }
static void raop_rtp_stop(raop_rtp_t *t) { t->live=0; stopped++; }
static void raop_ntp_stop(raop_ntp_t *t) { t->live=0; stopped++; }
int main(int argc, char **argv) {
raop_rtp_mirror_t mirror={1}, audio={1}, clock={1};
owner=(raop_conn_t){.raop_rtp_mirror=&mirror,.raop_rtp=&audio,.raop_ntp=&clock};
raop_t server={.hls_support=true}; raop_t *raop=&server;
raop_conn_t child={0}; raop_conn_t *conn=&child; void *ptr=conn;
const char *client_session_id="synthetic-session";
'''
SUFFIX = r'''
assert(conn->connection_type==CONNECTION_TYPE_AIRPLAY);
assert(!strcmp(conn->client_session_id, client_session_id));
free(conn->client_session_id);
int expected=atoi(argv[1]);
return stopped==expected && mirror.live==(expected==0) && audio.live==(expected==0) && clock.live==(expected==0) ? 0 : 2;
}
'''

def run(label, path, android, expected):
    source = path.read_text(encoding='utf-8')
    branch = source.split('} else if (client_session_id) {', 1)[1].split('} else if (host) {', 1)[0]
    code = OUT / (label + '.c')
    exe = OUT / (label + '.exe')
    code.write_text(PREFIX + branch + SUFFIX)
    cmd = ['C:/msys64/ucrt64/bin/gcc.exe', '-std=c17', str(code), '-o', str(exe)]
    if android: cmd.append('-D__ANDROID__')
    compiled = subprocess.run(cmd, capture_output=True, timeout=30)
    if compiled.returncode:
        raise RuntimeError(compiled.stderr.decode(errors='replace'))
    return subprocess.run([str(exe), str(expected)], capture_output=True).returncode

before = ROOT/'artifacts/rc27-quality-access/before/raop.c'
after = ROOT/'app/src/main/cpp/third_party/UxPlay/lib/raop.c'
result = {
    'oldAndroidFailsPreservation': run('old-android', before, True, 0) == 2,
    'newAndroidPreservesMirrorAudioClock': run('new-android', after, True, 0) == 0,
    'desktopBehaviorUnchanged': run('desktop', after, False, 3) == 0,
    'testScope': 'compiled production classification branch with transport spies; no real sockets'
}
(OUT/'results.json').write_text(json.dumps(result, indent=2))
print(json.dumps(result))
assert all(result[k] for k in result if k != 'testScope')
