"""Real sockets and cryptographic proofs against the emulator's instrumented fixture.
No production authentication shortcut. The fixture registers one synthetic public key.
Never prints or persists the displayed PIN, derived secrets or private keys.
"""
import hashlib, json, os, plistlib, re, socket, subprocess, sys, time
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'artifacts/rc27-quality-access/python-deps'))
from cryptography.hazmat.primitives.asymmetric import ed25519, x25519
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

ADB = r'C:\Users\ASUS\AppData\Local\Android\Sdk\platform-tools\adb.exe'
def adb(*args):
    return subprocess.check_output([ADB, '-s', 'emulator-5584', *args])
def raw(key): return key.public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
def sha(data): return hashlib.sha1(data).digest()
def bn(n): return n.to_bytes(max(1, (n.bit_length()+7)//8), 'big')
def pad(n): return n.to_bytes(256, 'big')

class Client:
    def __init__(self, protocol='RTSP/1.0'):
        self.socket = socket.create_connection(('127.0.0.1', 19727), 5)
        self.file = self.socket.makefile('rb')
        self.protocol = protocol
        self.seq = 0
    def request(self, method, path, data=b'', headers=None):
        self.seq += 1
        h = {'Content-Length': str(len(data))}
        if self.protocol.startswith('RTSP'): h['CSeq'] = str(self.seq)
        else: h.update({'Host':'localhost', 'X-Apple-Session-ID':'fixture-media'})
        h.update(headers or {})
        self.socket.sendall((f'{method} {path} {self.protocol}\r\n'+''.join(f'{k}: {v}\r\n' for k,v in h.items())+'\r\n').encode()+data)
        status = self.file.readline().split()
        if len(status) < 2: raise AssertionError('Missing response')
        response_headers = {}
        while True:
            line = self.file.readline()
            if line == b'\r\n': break
            if not line: raise AssertionError('Incomplete response')
            k,v = line.decode().split(':',1); response_headers[k.lower()] = v.strip()
        body = self.file.read(int(response_headers.get('content-length','0')))
        return int(status[1]), body
    def plist(self, obj):
        return self.request('POST','/pair-setup-pin',plistlib.dumps(obj,fmt=plistlib.FMT_BINARY),{'Content-Type':'application/x-apple-binary-plist'})
    def close(self): self.file.close(); self.socket.close()

def verify(client, signing, expected=200, corrupt_signature=False):
    ephemeral = x25519.X25519PrivateKey.generate()
    ours = raw(ephemeral.public_key())
    status, reply = client.request('POST','/pair-verify', b'\x01\0\0\0'+ours+raw(signing.public_key()))
    assert status == expected, ('verify phase 1', status)
    if status != 200: return
    assert len(reply) == 96
    shared = ephemeral.exchange(x25519.X25519PublicKey.from_public_bytes(reply[:32]))
    key = hashlib.sha512(b'Pair-Verify-AES-Key'+shared).digest()[:16]
    iv = hashlib.sha512(b'Pair-Verify-AES-IV'+shared).digest()[:16]
    cipher = Cipher(algorithms.AES(key), modes.CTR(iv)).encryptor()
    cipher.update(bytes(64))
    signature = cipher.update(signing.sign(ours+reply[:32]))
    if corrupt_signature: signature = bytes([signature[0] ^ 1]) + signature[1:]
    assert client.request('POST','/pair-verify',bytes(4)+signature)[0] == (470 if corrupt_signature else 200)

def displayed_pin():
    # Test-APK loopback relay reads the real challenge in memory. No PIN file/log.
    deadline = time.monotonic()+12
    while time.monotonic() < deadline:
        with socket.create_connection(('127.0.0.1',19728),2) as channel:
            data = channel.recv(64).decode()
        if re.fullmatch(r'\d{4}',data): return data
        time.sleep(.2)
    raise AssertionError('Pairing code not visible')

def fresh_pair(client, wrong_pin=False):
    prompt = Client()
    assert prompt.request('POST','/pair-pin-start')[0] == 200
    pin = displayed_pin()
    if wrong_pin: pin = f'{(int(pin)+1)%10000:04d}'
    prompt.close() # iOS closes this socket while its code entry UI is open.
    user = '02:00:00:00:00:27'
    status, reply = client.plist({'method':'pin','user':user})
    assert status == 200
    reply = plistlib.loads(reply)
    salt = bn(int.from_bytes(reply['salt'], 'big'))
    B = int.from_bytes(reply['pk'], 'big')
    source = (ROOT/'app/src/main/cpp/third_party/UxPlay/lib/srp.c').read_text(encoding='utf-8')
    block = source.split('{ /* 2048 */',1)[1].split('},',1)[0]
    N = int(''.join(re.findall(r'"([A-F0-9]+)"', block)[:-1]),16)
    a = int.from_bytes(os.urandom(32),'big'); A = pow(2,a,N)
    x = int.from_bytes(sha(salt+sha((user+':'+pin).encode())),'big')
    u = int.from_bytes(sha(pad(A)+pad(B)),'big')
    k = int.from_bytes(sha(pad(N)+pad(2)),'big')
    S = pow((B-k*pow(2,x,N))%N, a+u*x, N)
    secret = sha(bn(S)+bytes(4))+sha(bn(S)+b'\0\0\0\1')
    proof = sha(bytes(a^b for a,b in zip(sha(bn(N)),sha(bn(2))))+sha(user.encode())+salt+bn(A)+bn(B)+secret)
    status, result = client.plist({'pk':bn(A),'proof':proof})
    if wrong_pin:
        assert status == 470, ('Wrong PIN accepted', status)
        return
    assert status == 200, ('SRP proof', status)
    assert plistlib.loads(result)['proof'] == sha(bn(A)+proof+secret)
    signing = ed25519.Ed25519PrivateKey.generate()
    aes = hashlib.sha512(b'Pair-Setup-AES-Key'+secret).digest()[:16]
    iv = bytearray(hashlib.sha512(b'Pair-Setup-AES-IV'+secret).digest()[:16]); iv[-1] = (iv[-1]+1)%256
    encrypted = AESGCM(aes).encrypt(bytes(iv),raw(signing.public_key()),None)
    assert client.plist({'epk':encrypted[:32],'authTag':encrypted[32:]})[0] == 200
    verify(client,signing)

def main():
    results = []
    trusted = ed25519.Ed25519PrivateKey.from_private_bytes(bytes(range(32)))
    owner = Client(); verify(owner,trusted)
    assert owner.request('GET_PARAMETER','*',b'volume\r\n',{'Content-Type':'text/parameters'})[0] == 200
    results.append('registered Ed25519 possession accepted; protected RTSP request accepted')
    for method, path, protocol in [('OPTIONS','*','RTSP/1.0'), ('POST','/play','HTTP/1.1'),
        ('POST','/stop','HTTP/1.1'),('POST','/action','HTTP/1.1'),('POST','/rate?value=1','HTTP/1.1'),
        ('POST','/scrub?position=1','HTTP/1.1'),('SETUP','*','RTSP/1.0'),('TEARDOWN','*','RTSP/1.0')]:
        c = Client(protocol)
        status,_ = c.request(method,path)
        assert status == (200 if method == 'OPTIONS' else 470), (method,path,status)
        c.close()
        assert owner.request('GET_PARAMETER','*',b'volume\r\n',{'Content-Type':'text/parameters'})[0] == 200
    results.append('unpaired protected siblings rejected; OPTIONS cannot evict authenticated owner')
    owner.close()
    c=Client(); verify(c,trusted,corrupt_signature=True); c.close()
    results.append('registered public key with forged signature rejected')
    c=Client(); fresh_pair(c,wrong_pin=True); c.close()
    results.append('incorrect PIN proof rejected')
    c=Client(); assert c.request('POST','/pair-setup',bytes(32))[0] == 200
    verify(c,ed25519.Ed25519PrivateKey.generate(),470); c.close()
    results.append('plain pair-setup does not authorize unknown key')
    for payload in [{'pk':b'x'*256,'proof':b'x'*65}, {'epk':b'x'*32,'authTag':b'x'*16}, {'epk':b'x','authTag':b'x'}]:
        c=Client(); assert c.plist(payload)[0] == 470; c.close()
    results.append('oversized proof, out-of-order confirmation, short fields rejected')
    c=Client(); assert c.request('POST','/pair-pin-start')[0] == 200
    assert c.plist({'method':'pin','user':'02:00:00:00:00:28'})[0] == 200
    zero = bytes(40)
    key=hashlib.sha512(b'Pair-Setup-AES-Key'+zero).digest()[:16]
    iv=bytearray(hashlib.sha512(b'Pair-Setup-AES-IV'+zero).digest()[:16]); iv[-1]=(iv[-1]+1)%256
    forged=AESGCM(key).encrypt(bytes(iv),raw(trusted.public_key()),None)
    assert c.plist({'epk':forged[:32],'authTag':forged[32:]})[0] == 470; c.close()
    results.append('zero-session-key SRP stage-skip exploit rejected')
    c=Client('HTTP/1.1'); verify(c,trusted)
    assert c.request('GET','/playback-info')[0] == 200; c.close()
    results.append('independently verified HTTP control connection accepted')
    c=Client(); fresh_pair(c)
    assert c.request('GET_PARAMETER','*',b'volume\r\n',{'Content-Type':'text/parameters'})[0] == 200; c.close()
    results.append('receiver PIN survives request-socket closure; new socket SRP proof and signed pair-verify accepted')
    result={'passed':True,'checks':results,'realAppleClientValidated':False,'mediaTransportValidated':False}
    (ROOT/'artifacts/rc27-quality-access/protocol-results.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps(result))

if __name__ == '__main__':
    try: main()
    finally: adb('shell','run-as','com.airrelay.receiver','touch','files/auth-fixture-done')
