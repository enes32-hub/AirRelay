#!/usr/bin/env python3
"""Source-only safety checks for phases 1–4. This is not an Android compiler or media test."""
from pathlib import Path
import hashlib, json, re
from xml.etree import ElementTree as E
ROOT = Path(__file__).resolve().parents[2]
K = 'app/src/main/kotlin/io/github/jqssun/airplay/'
def sha(data): return hashlib.sha256(data).hexdigest()
def read(name): return (ROOT / name).read_text(encoding='utf-8-sig')
def reverse_boundary(path, text):
    """Accept ONLY the specific reviewed logging and first-buffer-observation changes."""
    if path.endswith('native_bridge.cpp'):
        text = text.replace('#include "privacy_log.h"\n', '')
        text = text.replace('    const std::string safe_message = airrelay_safe_log(msg);\n    __android_log_print(prio, TAG, "%s", safe_message.c_str());',
                            '    __android_log_print(prio, TAG, "%s", msg);')
    elif path.endswith('android_raop_callbacks.c'):
        text = text.replace('LOGI("video_play: [media URL omitted] @ %.2fs", start_position);',
                            'LOGI("video_play: %s @ %.2fs", location ? location : "(null)", start_position);')
    elif path.endswith('log_sink.h'):
        text = text.replace('#include "privacy_log.h"\n', '')
        text = text.replace('        const std::string safe_message = airrelay_safe_log(buf);\n        __android_log_print(prio, LOG_SINK_TAG, "%s", safe_message.c_str());',
                            '        __android_log_print(prio, LOG_SINK_TAG, "%s", buf);')
        text = text.replace('env->NewStringUTF(safe_message.c_str())', 'env->NewStringUTF(buf)')
    elif path.endswith('VideoRenderer.kt'):
        text = text.replace('    var onFirstFrameSubmitted: ((Long) -> Unit)?\n        get() = pipeline.onFirstFrameSubmitted\n        set(value) { pipeline.onFirstFrameSubmitted = value }\n', '')
        text = text.replace('    fun startSession(observationToken: Long = 0L) = synchronized(lock) {\n        _resetStats()\n        pipeline.beginPresentationObservation(observationToken)\n    }',
                            '    fun startSession() = synchronized(lock) { _resetStats() }')
        text = text.replace('    fun stopSession() = synchronized(lock) {\n        pipeline.endPresentationObservation()\n        stopCodec()\n    }',
                            '    fun stopSession() = synchronized(lock) { stopCodec() }')
        text = text.replace('        pipeline.endPresentationObservation()\n', '')
    elif path.endswith('VideoPipeline.kt'):
        text = text.replace('import io.github.jqssun.airplay.receiver.FirstFrameGate\n', '')
        text = text.replace('    // Observation only. A cached frame from the previous session cannot mark a new session live.\n    private val presentationGate = FirstFrameGate()\n    private var consumedFrameToken = -1L\n    @Volatile var onFirstFrameSubmitted: ((Long) -> Unit)? = null\n    fun beginPresentationObservation(token: Long) { presentationGate.begin(token) }\n    fun endPresentationObservation() { presentationGate.invalidate() }\n', '')
        text = text.replace('        val frameToken = presentationGate.token()\n', '')
        text = text.replace('            consumedFrameToken = frameToken\n', '')
        text = text.replace('        consumedFrameToken = frameToken\n', '')
        text = text.replace('        val submitted = egl?.swap(window) == true\n        if (submitted && presentationGate.acceptSubmission(consumedFrameToken)) {\n            // Callback must remain non-blocking: only publishes product state, no JNI calls.\n            onFirstFrameSubmitted?.invoke(consumedFrameToken)\n        }', '        egl?.swap(window)')
    else: raise AssertionError('Unknown boundary: ' + path)
    return text

def jni_calls(text):
    out=[]
    for m in re.finditer(r'NativeBridge\.(\w+)\(',text):
        level=1; i=m.end(); quote=False; escaped=False
        while i<len(text) and level:
            c=text[i]
            if quote:
                if escaped: escaped=False
                elif c=='\\': escaped=True
                elif c=='"': quote=False
            elif c=='"': quote=True
            elif c=='(': level+=1
            elif c==')': level-=1
            i+=1
        out.append(re.sub(r'\s+','',text[m.start():i]))
    return sorted(out)

def main():
    manifest = json.loads(read('docs/productization/PROTECTED_CORE_SHA256.json'))
    for item in manifest['original_strict']:
        data=(ROOT/item['path']).read_bytes()
        assert len(data)==item['bytes'] and sha(data)==item['sha256'], 'Protected source changed: '+item['path']
    print(f"PASS protected original bytes: {len(manifest['original_strict'])} files")
    for item in manifest['output_and_observation_boundaries']:
        data=(ROOT/item['path']).read_bytes()
        assert sha(data)==item['reviewed_sha256'], 'Reviewed boundary changed: '+item['path']
        baseline = reverse_boundary(item['path'],read(item['path']))
        assert sha(baseline.encode())==item['original_lf_sha256'], 'Unexpected transport change: '+item['path']
    print(f"PASS {len(manifest['output_and_observation_boundaries'])} boundary files: "
          "reversible log-output / first-buffer observation changes only")
    contract=json.loads(read('docs/productization/SERVICE_JNI_CONTRACT.json'))
    assert jni_calls(read(contract['path']))==contract['original_call_expressions'], 'Native call contract changed'
    print('PASS service JNI call expressions: same original names, arguments and occurrence counts')
    resources=ROOT/'app/src/main/res'
    xml=list(resources.rglob('*.xml'))
    for p in xml: E.parse(p)
    def strings(folder):
        d={}
        for p in (resources/folder).glob('*.xml'):
            for x in E.parse(p).getroot():
                if x.tag=='string':
                    assert x.get('name') not in d, 'Duplicate string '+x.get('name')
                    d[x.get('name')]=x.text or ''
        return d
    # English is the default resource set (values/); Turkish ships as values-tr.
    en,tr=strings('values'),strings('values-tr')
    assert en.keys()==tr.keys(),'Translation keys differ'
    for key in en:
        assert re.findall(r'%(?:\d+\$)?[dsf]',tr[key])==re.findall(r'%(?:\d+\$)?[dsf]',en[key]), 'Format differs: '+key
    refs=set()
    for p in (ROOT/'app/src/main/kotlin').rglob('*.kt'):
        # App-local references only: a library-qualified id such as
        # androidx.media3.ui.R.string.exo_controls_next_description is not ours to define.
        refs.update(re.findall(r'(?<![\w.])R\.string\.(\w+)',p.read_text(encoding='utf-8-sig')))
    assert not refs-tr.keys(), 'Undefined string IDs: '+str(refs-tr.keys())
    print(f'PASS {len(xml)} XML resources; {len(tr)} EN/TR string pairs; all referenced string IDs exist')
    vm=read(K+'viewmodel/MainViewModel.kt'); ui=read(K+'ui/MainScreen.kt'); service=read(K+'service/AirPlayService.kt')
    assert 'restartServer' not in vm and 'restartJob' not in vm, 'Automatic setting restart returned'
    assert 'NativeBridge' not in ui and 'NativeBridge' not in vm, 'UI bypasses service boundary'
    assert 'key("mirror-surface")' in ui and 'key("remote-video-surface")' in ui
    assert 'wakeGuard.event(' in ui and 'KeyEventType.KeyUp' in ui and 'KEYCODE_DPAD_CENTER' in ui
    guard = read(K+'receiver/InteractionPolicy.kt')
    assert 'code == openingKey' in guard and 'repeat == 0' in guard
    assert all(old not in ui for old in ['VideoControls(', 'PlayerGestures(', 'seekVideoBy(', 'toggleVideoDownload('])
    assert 'SecureFlagPolicy.SecureOn' in read(K+'ui/ReceiverDialogs.kt')
    size_event=service.split('override fun onVideoSize(',1)[1].split('override fun onVolumeChange(',1)[0]
    assert 'firstFrameSubmitted = true' not in size_event
    assert 'PAIRING_UI_TIMEOUT_MS = 120_000L' in service
    assert '_lastPin' not in service.split('getString(R.string.notification_pin_text',1)[1].split('\n',1)[0]
    assert 'while (true)' not in read(K+'MainActivity.kt') and 'delay(200)' not in read(K+'MainActivity.kt')
    print('PASS source safeguards: no setting auto-restart, stable surface, remote wake, private PIN, event-driven UI')
    print('SOURCE CHECKS PASSED. No Android build, APK, emulator or physical device test.')
if __name__=='__main__': main()
