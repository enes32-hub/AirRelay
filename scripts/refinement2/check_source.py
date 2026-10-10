#!/usr/bin/env python3
"""Static R2 change-boundary/interaction guardrails. NOT a UI execution test."""
from pathlib import Path
import hashlib,json
ROOT=Path(__file__).resolve().parents[2]
K=ROOT/'app/src/main/kotlin/io/github/jqssun/airplay'
def source(path): return (K/path).read_text(encoding='utf-8')
def main():
    manifest=json.loads((ROOT/'docs/refinement2/PREVIOUS_DELIVERY_PROTECTED_SHA256.json').read_text())
    for item in manifest['files']:
        path=(ROOT/item['path']).resolve()
        assert path.is_relative_to(ROOT) and path.is_file(),item['path']
        data=path.read_bytes()
        assert len(data)==item['bytes'] and hashlib.sha256(data).hexdigest()==item['sha256'],item['path']
    print(f"PASS R2 protected native/renderer/audio/default/build files: {len(manifest['files'])}, byte-identical to previous delivery")
    ui=source('ui/MainScreen.kt');components=source('ui/ReceiverComponents.kt');vm=source('viewmodel/MainViewModel.kt')
    svc=source('service/AirPlayService.kt');settings=source('ui/ReceiverSettings.kt');advanced=source('ui/SettingsScreen.kt')
    logs=source('ui/LogsScreen.kt');focus=source('ui/TvFocusMemory.kt');home=source('ui/ReceiverHome.kt')
    policy=source('receiver/InteractionPolicy.kt');draft=source('receiver/PendingReceiverSettings.kt')
    checks={
      'press and hover feedback without user-disabled ripple': 'collectIsPressedAsState' in components and 'collectIsHoveredAsState' in components and 'indication = ripple(' not in components and 'indication = null' in components,
      'animated transform reads are in graphics layer': '.graphicsLayer { scaleX = scale.value' in components and 'translationY = (1f - progress.value)' in ui,
      'home guide is explanatory rather than a dead focus stop': '.focusable(' not in home and 'HomePrimaryAction' in home,
      'page-local saved state and focus recovery are connected': 'pageStates.SaveableStateProvider(page.name)' in ui and 'TvFocusRegion(enabled' in ui and 'focusCandidateIds(' in focus,
      'focus acceptance uses an actual observed focused predicate': 'if (candidate.focused) return' in focus,
      'single incoming page never wraps media surfaces': 'AnimatedContent' not in ui and 'Crossfade' not in ui and 'key("mirror-surface")' in ui and 'key("remote-video-surface")' in ui,
      'modal priority and background input lock': 'when {\n                state.pin != null -> PairingDialog' in ui and 'LocalBlockingOverlay provides globalModal' in ui and 'if (!blocked) DialogInputScope' in settings,
      'overlay wake guard receives native repeat and release events': 'wakeGuard.event(code, down, event.nativeKeyEvent.repeatCount' in ui and 'repeat == 0' in policy,
      'overlay timeout defers to controls, modal and accessibility': 'OverlayPolicy.mayHide(' in ui and 'calculateRecommendedTimeoutMillis' in ui and 'LocalControlPressed provides' in ui,
      'draft commit is dispatched to IO, with cancelled request guard': 'withContext(Dispatchers.IO) { pendingSettings.applyAtReceiverStart() }' in svc and '!startRequests.isCurrent(request)' in svc and 'startRequests.cancel()' in svc,
      'draft lock contention fails fast instead of blocking UI': draft.count('if (!lock.tryLock()) return false') == 2 and 'if (queueSetting(Prefs.' in vm,
      'debug native snapshots only in the diagnostics visibility collector': vm.count('collectDebugInfo()') == 1 and 'combine(_debugEnabled, _diagnosticsVisible)' in vm,
      'log export/clear is gated and reading snapshot is stable': '_logActionBusy.value' in vm and 'if ((follow && !reading) || !initialized)' in logs,
      'advanced numeric fields have explicit save and validation': 'enabled = valid && text != value' in advanced and 'onFocusChanged {\n' not in advanced,
      'settings grouped with separate list positions': 'SettingsCategory.CONNECTION' in settings and 'connectionList = rememberLazyListState()' in settings and 'appearanceList = rememberLazyListState()' in settings,
      'decorative illustration stays in its own bounds': 'Canvas(modifier.clipToBounds())' in source('ui/ReceiverScene.kt'),
    }
    for name,ok in checks.items():
        assert ok,name
        print('PASS static guard: '+name)
    print(f'STATIC INTERACTION GUARDS: {len(checks)} passed. Not Compose type checking or runtime navigation proof.')
if __name__=='__main__':main()
