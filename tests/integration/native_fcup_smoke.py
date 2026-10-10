"""Loopback synthetic sender -> real emulator FCUP -> Media3 first video frame.
No phone/authentication/YouTube network acceptance is claimed.
"""
import functools
import http.server
import json
import plistlib
from pathlib import Path
import socket
import subprocess
import threading
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
ADB = r'adb\Local\Android\Sdk\platform-tools\adb.exe'
SERIAL = 'emulator-5584'
CONTROL, MEDIA = 38609, 38608

def adb(*args):
    return subprocess.check_output([ADB, '-s', SERIAL, *args], timeout=20)

class QuietHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass

def read_message(reader):
    first = reader.readline()
    if not first:
        raise RuntimeError('Connection closed')
    headers = {}
    while True:
        line = reader.readline()
        if line == b'\r\n':
            break
        if not line:
            raise RuntimeError('Truncated headers')
        name, value = line.decode().split(':', 1)
        headers[name.lower()] = value.strip()
    body = reader.read(int(headers.get('content-length', '0')))
    return first.decode().strip(), body

def send(sock, method, path, session, body=b'', extra=''):
    headers = (f'{method} {path} HTTP/1.1\r\nHost: localhost\r\n'
               f'X-Apple-Session-ID: {session}\r\nContent-Length: {len(body)}\r\n'
               'Content-Type: application/x-apple-binary-plist\r\n' + extra + '\r\n')
    sock.sendall(headers.encode() + body)

def frames(pid):
    return adb('logcat', '-d', f'--pid={pid}', '-v', 'brief', '-s', 'AirPlayVideoPlayer:I').count(b'First video frame:')

def main():
    forward = adb('forward', '--list').decode()
    reverse = adb('reverse', '--list').decode()
    assert f'tcp:{CONTROL}' not in forward and f'tcp:{MEDIA}' not in reverse, 'Test ports already in use'
    server = http.server.ThreadingHTTPServer(('127.0.0.1', MEDIA), functools.partial(
        QuietHandler, directory=str(ROOT / 'app/src/androidTest/assets/media')))
    threading.Thread(target=server.serve_forever, daemon=True).start()
    results = []
    try:
        adb('forward', f'tcp:{CONTROL}', 'tcp:7000')
        adb('reverse', f'tcp:{MEDIA}', f'tcp:{MEDIA}')
        pid = adb('shell', 'pidof', 'com.airrelay.receiver').decode().strip()
        assert pid.isdigit()
        for status, start_position in ((200, 0.0), (0, 0.0), (0, 9.223372e12)):
            session = str(uuid.uuid4())
            with socket.create_connection(('127.0.0.1', CONTROL), timeout=10) as reverse_sock, \
                 socket.create_connection(('127.0.0.1', CONTROL), timeout=10) as control_sock:
                rr, cr = reverse_sock.makefile('rb'), control_sock.makefile('rb')
                try:
                    send(reverse_sock, 'POST', '/reverse', session, extra='Connection: Upgrade\r\nUpgrade: PTTH/1.0\r\nX-Apple-Purpose: event\r\n')
                    assert '101' in read_message(rr)[0]
                    before = frames(pid)
                    send(control_sock, 'POST', '/play', session, plistlib.dumps({
                        'Content-Location': 'mlhls://airrelay-local-test/master.m3u8',
                        'uuid': session, 'Start-Position-Seconds': start_position}, fmt=plistlib.FMT_BINARY))
                    assert '200' in read_message(cr)[0]
                    for index in range(2):
                        event, body = read_message(rr)
                        assert event.startswith('POST /event')
                        request = plistlib.loads(body)['request']
                        reverse_sock.sendall(b'HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n')
                        url = request['FCUP_Response_URL']
                        if index == 0:
                            assert url.endswith('/master.m3u8')
                            playlist = '#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000000,CODECS="avc1.42c01e"\nmlhls://airrelay-local-test/video.m3u8\n'
                        else:
                            assert url.endswith('/video.m3u8')
                            playlist = '#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXT-X-MEDIA-SEQUENCE:0\n' + (
                                f'#EXTINF:2,\nhttp://127.0.0.1:{MEDIA}/video00.ts\n' * 3) + '#EXT-X-ENDLIST\n'
                        params = dict(request, FCUP_Response_StatusCode=status, FCUP_Response_Data=playlist.encode())
                        send(control_sock, 'POST', '/action', session, plistlib.dumps(
                            {'type': 'unhandledURLResponse', 'params': params}, fmt=plistlib.FMT_BINARY))
                        assert '200' in read_message(cr)[0]
                    deadline = time.monotonic() + 10
                    while frames(pid) <= before and time.monotonic() < deadline:
                        time.sleep(.2)
                    assert frames(pid) > before, f'No rendered video frame for FCUP status {status}'
                    results.append({'status': status, 'senderStartSeconds': start_position,
                                    'nativePlaylistsAccepted': 2, 'media3FirstFrame': True})
                    send(control_sock, 'POST', '/stop', session)
                    read_message(cr)
                finally:
                    rr.close(); cr.close()
            time.sleep(.5)
        print(json.dumps({'passed': True, 'cases': results, 'realIPhoneValidated': False}))
    finally:
        server.shutdown(); server.server_close()
        adb('forward', '--remove', f'tcp:{CONTROL}')
        adb('reverse', '--remove', f'tcp:{MEDIA}')

if __name__ == '__main__':
    main()
