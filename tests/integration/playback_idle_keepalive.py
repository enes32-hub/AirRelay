"""Real native idle/EOS playback-info polls must preserve the same TCP channel.
Run only on the idle AirRelay emulator; not a substitute for an iPhone video test.
"""
import argparse
import plistlib
import socket
import subprocess
import functools
import http.server
import pathlib
import threading
import time
import uuid

def response(reader, expected=200):
    status = reader.readline()
    assert str(expected).encode() in status, f'Connection closed or bad status: {status!r}'
    headers = {}
    while True:
        line = reader.readline()
        assert line, 'connection closed while reading headers'
        if line == b'\r\n': break
        key, value = line.split(b':', 1)
        headers[key.lower()] = value.strip()
    return reader.read(int(headers.get(b'content-length', b'0')))

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--adb', required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--seek', action='store_true')
    args = parser.parse_args()
    def adb(*cmd):
        return subprocess.check_output([args.adb, '-s', args.serial, *cmd], timeout=15)
    port = int(adb('forward', 'tcp:0', 'tcp:7000'))
    class QuietHandler(http.server.SimpleHTTPRequestHandler):
        def log_message(self, *args): pass
    fixtures = pathlib.Path(__file__).resolve().parents[2] / 'app/src/androidTest/assets/media'
    server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), functools.partial(QuietHandler, directory=str(fixtures)))
    media_port = server.server_port
    device_port = int(adb('reverse', 'tcp:0', f'tcp:{media_port}'))
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        with socket.create_connection(('127.0.0.1', port), timeout=5) as sock:
            reader = sock.makefile('rb')
            pid = adb('shell', 'pidof', 'com.airrelay.receiver').decode().strip()
            def ended_count():
                return adb('logcat', '-d', f'--pid={pid}', '-s', 'AirPlayService:I').count(b'AirPlay Video stopped (player)')
            before = ended_count()
            session = str(uuid.uuid4())
            body = plistlib.dumps({'Content-Location': f'http://127.0.0.1:{device_port}/clip.mp4',
                                  'Start-Position-Seconds': 0.0, 'uuid': session}, fmt=plistlib.FMT_BINARY)
            sock.sendall((f'POST /play HTTP/1.1\r\nX-Apple-Session-ID: {session}\r\nContent-Type: application/x-apple-binary-plist\r\nContent-Length: {len(body)}\r\n\r\n').encode() + body)
            response(reader)
            if args.seek:
                seek_deadline = time.monotonic() + 10
                while True:
                    sock.sendall(b'GET /scrub HTTP/1.1\r\nContent-Length: 0\r\n\r\n')
                    status_body = response(reader).decode()
                    fields = dict(line.split(':', 1) for line in status_body.splitlines() if ':' in line)
                    assert 'duration' in fields and 'position' in fields, 'GET /scrub status missing'
                    if float(fields['duration']) > 0: break
                    assert time.monotonic() < seek_deadline, 'seek timeline not ready'
                    time.sleep(.1)
                sock.sendall((f'POST /scrub?position=2 HTTP/1.1\r\nX-Apple-Session-ID: {session}\r\nContent-Length: 0\r\n\r\n').encode())
                response(reader)
                time.sleep(.3)
                sock.sendall(b'GET /scrub HTTP/1.1\r\nContent-Length: 0\r\n\r\n')
                fields = dict(line.split(':', 1) for line in response(reader).decode().splitlines())
                assert float(fields['position']) >= 1.8, 'Sender seek not applied'
                sock.sendall((f'POST /scrub?invalid HTTP/1.1\r\nX-Apple-Session-ID: {session}\r\nContent-Length: 0\r\n\r\n').encode())
                response(reader)
                with socket.create_connection(('127.0.0.1', port), timeout=5) as stale:
                    stale.sendall(b'POST /scrub?position=0 HTTP/1.1\r\nX-Apple-Session-ID: stale-test-session\r\nContent-Length: 0\r\n\r\n')
                    response(stale.makefile('rb'))
                sock.sendall(b'POST /scrub?position=0 HTTP/1.1\r\nX-Apple-Session-ID: changed-test-session\r\nContent-Length: 0\r\n\r\n')
                response(reader, expected=400)
                sock.sendall(b'GET /scrub HTTP/1.1\r\nContent-Length: 0\r\n\r\n')
                fields = dict(line.split(':', 1) for line in response(reader).decode().splitlines())
                assert float(fields['position']) >= 1.8, 'Stale or malformed seek changed playback position'
                print('PASS: GET /scrub, sender POST seek, malformed query, stale session and changed-session 400 without crash')
            deadline = time.monotonic() + 30
            while ended_count() <= before and time.monotonic() < deadline:
                time.sleep(.5)
            assert ended_count() > before, 'Local video did not naturally end; EOS precondition not met'
            for index in range(3):
                sock.sendall(b'GET /playback-info HTTP/1.1\r\nContent-Length: 0\r\n\r\n')
                body = response(reader)
                assert body, 'idle response was empty rather than a playback snapshot'
                info = plistlib.loads(body)
                assert info['duration'] == 0 and info['rate'] == 0
                assert info['loadedTimeRanges'] == [] and info['seekableTimeRanges'] == []
            print('PASS: three idle/EOS polls returned valid snapshots on the same TCP connection')
    finally:
        server.shutdown()
        server.server_close()
        adb('reverse', '--remove', f'tcp:{device_port}')
        adb('forward', '--remove', f'tcp:{port}')

if __name__ == '__main__':
    main()
