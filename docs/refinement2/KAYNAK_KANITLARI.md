# İkinci inceleme: kaynak kanıtları

Önce: `AynaTV_Ilk_4_Asama_Guncellenmis_Kaynak.zip`. Sonra: bu teslimin `AynaTV/` kaynakları. Satırlar bu teslimle birlikte geçerlidir. Çalışma zamanı ölçümü değildir.

## R2-01 — Yüksek
Basma geri bildirimi yoktu; odaklanma ile basılma aynı deneyimdi.

Önce: `ui/ReceiverComponents.kt:55` · Sonra: `ui/ReceiverComponents.kt:10`

### Önce — `ui/ReceiverComponents.kt`
```kotlin
  53 |             if (focused) AynaColors.Text else if (selected) AynaColors.Accent else AynaColors.Border, shape)
  54 |         .clickable(enabled = enabled, role = Role.Button,
  55 |             interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
  56 |         .padding(horizontal = 18.dp, vertical = 12.dp),
  57 |         horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
  58 |         if (icon != null) { Icon(icon, null, Modifier.size(21.dp), tint = ink); Spacer(Modifier.width(9.dp)) }
  59 |         Text(text, style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 2,
  60 |             overflow = TextOverflow.Ellipsis)
  61 |     }
  62 | }
```

### Sonra — `ui/ReceiverComponents.kt`
```kotlin
   8 | import androidx.compose.foundation.clickable
   9 | import androidx.compose.foundation.interaction.MutableInteractionSource
  10 | import androidx.compose.foundation.interaction.collectIsPressedAsState
  11 | import androidx.compose.foundation.interaction.collectIsHoveredAsState
  12 | import androidx.compose.foundation.layout.*
  13 | import androidx.compose.foundation.selection.toggleable
  14 | import androidx.compose.foundation.shape.CircleShape
  15 | import androidx.compose.foundation.shape.RoundedCornerShape
  16 | import androidx.compose.material.icons.Icons
  17 | import androidx.compose.material.icons.rounded.*
```

**Uygulanan:** Basma/ripple, odak ve fare üzerine gelme durumları ayrıldı; animasyonlu ölçek çizim katmanına taşındı.
**Doğrulama sınırı:** Kaynak + 16 statik koruma kontrolü; gerçek kumanda testi yapılmadı.

## R2-02 — Orta
Rehber metinleri işlem yapmadığı halde kumanda odağı alıyordu.

Önce: `ui/ReceiverHome.kt:138` · Sonra: `ui/ReceiverHome.kt:134`

### Önce — `ui/ReceiverHome.kt`
```kotlin
 136 | @Composable
 137 | private fun GuideStep(number: String, title: Int, body: Int) {
 138 |     Row(Modifier.focusRing(RoundedCornerShape(8.dp)).focusable().padding(3.dp),
 139 |         horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
 140 |         Text(number, style = MaterialTheme.typography.labelLarge, color = AynaColors.Accent,
 141 |             modifier = Modifier.padding(top = 2.dp))
 142 |         Column {
 143 |             Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
 144 |             Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = AynaColors.Muted)
 145 |         }
```

### Sonra — `ui/ReceiverHome.kt`
```kotlin
 132 | 
 133 | @Composable
 134 | private fun GuideStep(number: String, title: Int, body: Int) {
 135 |     // Explanatory content remains accessible to screen readers, but is not a dead D-pad stop.
 136 |     Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
 137 |         Box(Modifier.size(28.dp).background(AynaColors.AccentSoft, CircleShape), contentAlignment = Alignment.Center) {
 138 |             Text(number, style = MaterialTheme.typography.labelMedium, color = AynaColors.Accent)
 139 |         }
 140 |         Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
 141 |             Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
```

**Uygulanan:** Rehber adımları pasif açıklama olarak bırakıldı; yardım düğmesi gerçek eylem olarak kaldı.
**Doğrulama sınırı:** Kaynak incelemesi.

## R2-03 — Yüksek
Sayfaya dönüşte ilk öğeye odaklanılıyor; sayfa durumu ve odak kimliği birlikte saklanmıyordu.

Önce: `ui/ReceiverHelp.kt:31` · Sonra: `ui/TvFocusMemory.kt:21`

### Önce — `ui/ReceiverHelp.kt`
```kotlin
  29 |     var settingsFailed by remember { mutableStateOf(false) }
  30 |     val first = remember { FocusRequester() }
  31 |     LaunchedEffect(Unit) { first.requestFocusUntilLanded() }
  32 |     val topics = listOf(
  33 |         R.string.p_help_find to R.string.p_help_find_body,
  34 |         R.string.p_help_pin to R.string.p_help_pin_body,
  35 |         R.string.p_help_sound to R.string.p_help_sound_body,
  36 |         R.string.p_help_disconnect to R.string.p_help_disconnect_body,
  37 |         R.string.p_help_black to R.string.p_help_black_body,
  38 |     )
```

### Sonra — `ui/TvFocusMemory.kt`
```kotlin
  19 | 
  20 | /** Only currently composed targets are registered; no retained Android Views or lazy item handles. */
  21 | private class TvFocusMemory {
  22 |     class Target(val requester: FocusRequester, var enabled: Boolean, var focused: Boolean = false)
  23 |     val targets = linkedMapOf<String, Target>()
  24 |     var remembered: String? = null
  25 |     var defaultId: String? = null
  26 |     var enabled = true
  27 |     var rememberFocus: (String) -> Unit = {}
  28 |     var requestRestore: () -> Unit = {}
```

**Uygulanan:** Sayfa başına SaveableStateProvider, kayıtlı odak kimlikleri, yerleşim karesi bekleme ve geçerli yedek hedefler eklendi.
**Doğrulama sınırı:** Odak aday sırası host testli; Compose odak davranışı cihazda çalıştırılmadı.

## R2-04 — Orta
Hazır ve boş alıcıda en baskın eylem Durdur idi. Kullanıcı ana işi yaparken alıcıyı kapatabiliyordu.

Önce: `ui/ReceiverHome.kt:100` · Sonra: `receiver/InteractionPolicy.kt:19`

### Önce — `ui/ReceiverHome.kt`
```kotlin
  98 |                 }
  99 |             }, icon = if (state.running && !state.sessionBusy) Icons.Rounded.Stop else Icons.Rounded.Cast,
 100 |                 primary = true, enabled = bound && !state.starting,
 101 |                 modifier = Modifier.focusRequester(initial).onFocusChanged { focused = it.hasFocus })
 102 |             if (needsHelp) TvAction(stringResource(R.string.p_help), onHelp, icon = Icons.Rounded.HelpOutline)
 103 |         }
 104 |         val notice = when (state.notice) {
 105 |             ReceiverNotice.CONNECTION_LOST -> R.string.p_notice_lost
 106 |             ReceiverNotice.CONNECTION_RESET -> R.string.p_notice_reset
 107 |             ReceiverNotice.PAIRING_CANCELLED -> R.string.p_notice_cancelled
```

### Sonra — `receiver/InteractionPolicy.kt`
```kotlin
  17 | 
  18 | enum class HomePrimaryAction { START, VIEW, GUIDE }
  19 | fun ReceiverUiState.homePrimaryAction(): HomePrimaryAction = when {
  20 |     mirrorRequested || mediaPlaying -> HomePrimaryAction.VIEW
  21 |     !running -> HomePrimaryAction.START
  22 |     else -> HomePrimaryAction.GUIDE
  23 | }
  24 | 
  25 | /** An opening press cannot also activate a control. A lost key-up cannot trap the next press. */
  26 | class RemoteWakeGuard {
```

**Uygulanan:** Başlat / İçeriğe dön / Bağlantı rehberi ayrıldı. Durdur ikincil ve uyarı renkli; meşgul oturumda onaylı.
**Doğrulama sınırı:** Gerçek karar fonksiyonu host testli.

## R2-05 — Yüksek
Kontrolleri açan tuşun bırakma olayı kaybolursa aynı tuşun sonraki basışları yutulabiliyordu.

Önce: `ui/MainScreen.kt:197` · Sonra: `receiver/InteractionPolicy.kt:26`

### Önce — `ui/MainScreen.kt`
```kotlin
 195 |             if (modalOpen) return@onPreviewKeyEvent false
 196 |             val code = event.nativeKeyEvent.keyCode
 197 |             if (event.type == KeyEventType.KeyUp && code == openingKey) {
 198 |                 openingKey = -1
 199 |                 return@onPreviewKeyEvent true
 200 |             }
 201 |             // Consume repeats as well as key-up: reopening must not activate a button.
 202 |             if (code == openingKey) return@onPreviewKeyEvent true
 203 |             val controlKey = code in setOf(AndroidKey.KEYCODE_DPAD_CENTER, AndroidKey.KEYCODE_ENTER,
 204 |                 AndroidKey.KEYCODE_NUMPAD_ENTER, AndroidKey.KEYCODE_DPAD_UP, AndroidKey.KEYCODE_DPAD_DOWN,
```

### Sonra — `receiver/InteractionPolicy.kt`
```kotlin
  24 | 
  25 | /** An opening press cannot also activate a control. A lost key-up cannot trap the next press. */
  26 | class RemoteWakeGuard {
  27 |     private var openingKey: Int? = null
  28 |     data class Decision(val consume: Boolean, val reveal: Boolean = false)
  29 |     fun reset() { openingKey = null }
  30 |     fun event(code: Int, down: Boolean, repeat: Int, wakeKey: Boolean, visible: Boolean): Decision {
  31 |         if (down && repeat == 0) openingKey = null // a fresh physical gesture, even after a lost release
  32 |         if (code == openingKey) {
  33 |             if (!down) openingKey = null
```

**Uygulanan:** Yeni fiziksel basış eski kilidi temizliyor; açılış basışı, tekrarı ve bırakması yeni düğmeyi yanlışlıkla çalıştırmıyor.
**Doğrulama sınırı:** Host testli; 5.000 deterministik aç/kapat dizisi, gerçek tuş donanımı değil.

## R2-06 — Yüksek
Kontrollerin kaybolması yalnız tuş sayacına bağlıydı; basılı kontrol, erişilebilirlik süresi ve pointer etkileşimi yeterince hesaba katılmıyordu.

Önce: `ui/MainScreen.kt:181` · Sonra: `ui/MainScreen.kt:240`

### Önce — `ui/MainScreen.kt`
```kotlin
 179 |     onPlayPause: () -> Unit, onPip: (() -> Unit)?) {
 180 |     var visible by remember { mutableStateOf(true) }
 181 |     var interaction by remember { mutableIntStateOf(0) }
 182 |     var openingKey by remember { mutableIntStateOf(-1) }
 183 |     val rootFocus = remember { FocusRequester() }
 184 |     val safeAction = remember { FocusRequester() }
 185 |     LaunchedEffect(visible, interaction, waiting, modalOpen, networkMissing) {
 186 |         if (visible && !waiting && !modalOpen && !networkMissing) { delay(5000); visible = false }
 187 |     }
 188 |     LaunchedEffect(visible, modalOpen) {
```

### Sonra — `ui/MainScreen.kt`
```kotlin
 238 |         containsIcons = true, containsText = true, containsControls = true) ?: 5000L
 239 |     LaunchedEffect(visible, interaction, waiting, modalOpen, networkMissing, pressed, timeout) {
 240 |         if (OverlayPolicy.mayHide(visible, waiting, modalOpen, networkMissing, pressed)) {
 241 |             delay(timeout); visible = false
 242 |         }
 243 |     }
 244 |     LaunchedEffect(visible, modalOpen) {
 245 |         if (modalOpen) wakeGuard.reset()
 246 |         else if (visible) safeAction.requestFocusUntilLanded(isFocused = { safeFocused })
 247 |         else rootFocus.requestFocusUntilLanded(isFocused = { rootFocused })
```

**Uygulanan:** Odak, tıklama, dokunma ve basma süreyi yeniliyor. Bekleme, ağ sorunu, modal veya basılı kontrol varken otomatik gizleme yok.
**Doğrulama sınırı:** 32 politika kombinasyonu host testli; zamanlayıcı ve focus cihazda ölçülmedi.

## R2-07 — Yüksek
Kurulum, PIN ve durdurma pencereleri ayrı koşullarla çiziliyordu; yerel ayar pencereleri de bağımsızdı.

Önce: `ui/MainScreen.kt:154` · Sonra: `ui/MainScreen.kt:166`

### Önce — `ui/MainScreen.kt`
```kotlin
 152 |             // Dialogs are independent of the selected page; changing tabs cannot hide a live PIN.
 153 |             if (!setupComplete && showSetup) ReceiverSetupDialog(viewModel) { showSetup = false }
 154 |             state.pin?.let { PairingDialog(it, viewModel::cancelPairing) }
 155 |             if (confirmStop) ConfirmReceiverAction(stringResource(R.string.p_stop_title),
 156 |                 stringResource(R.string.p_stop_body), stringResource(R.string.p_stop_confirm),
 157 |                 onConfirm = { confirmStop = false; viewModel.stopServer() }, onCancel = { confirmStop = false })
 158 |             if (confirmMediaStop) ConfirmReceiverAction(stringResource(R.string.p_media_end_title),
 159 |                 stringResource(R.string.p_media_end_body), stringResource(R.string.p_stop_confirm),
 160 |                 onConfirm = { confirmMediaStop = false; viewModel.stopVideoPlayback(); fullScreen = false },
 161 |                 onCancel = { confirmMediaStop = false })
```

### Sonra — `ui/MainScreen.kt`
```kotlin
 164 |                     .widthIn(max = 600.dp))
 165 |             }
 166 |             // One global modal at a time. A PIN always takes priority over setup/stop prompts.
 167 |             when {
 168 |                 state.pin != null -> PairingDialog(state.pin!!, viewModel::cancelPairing)
 169 |                 setupDialog -> ReceiverSetupDialog(viewModel) { showSetup = false }
 170 |                 confirmStop -> ConfirmReceiverAction(stringResource(R.string.p_stop_title),
 171 |                     stringResource(R.string.p_stop_body), stringResource(R.string.p_stop_confirm),
 172 |                     onConfirm = { confirmStop = false; viewModel.stopServer() }, onCancel = { confirmStop = false })
 173 |                 confirmMediaStop -> ConfirmReceiverAction(stringResource(R.string.p_media_end_title),
```

**Uygulanan:** PIN öncelikli tek global pencere seçimi; alttaki sayfaya giriş engeli ve diyalog için ayrı etkin kontrol kapsamı.
**Doğrulama sınırı:** Kaynak incelemesi; Android pencere kabulü yapılmadı.

## R2-08 — Orta
Gelişmiş sayısal alanlar odak kaybında kaydediliyordu. Düzenleme ile uygulama niyeti belirsizdi.

Önce: `ui/SettingsScreen.kt:489` · Sonra: `ui/SettingsScreen.kt:476`

### Önce — `ui/SettingsScreen.kt`
```kotlin
 487 |             .padding(horizontal = 16.dp)
 488 |             .padding(bottom = 8.dp)
 489 |             .onFocusChanged { if (!it.isFocused) save() }
 490 |     )
 491 | }
 492 | 
 493 | @Composable
 494 | private fun SettingChipField(
 495 |     title: String,
 496 |     description: String,
```

### Sonra — `ui/SettingsScreen.kt`
```kotlin
 474 | 
 475 | @Composable
 476 | private fun SettingTextField(label: String, value: String, onCommit: (String) -> Unit,
 477 |     description: String? = null, range: IntRange? = null) {
 478 |     var text by rememberSaveable(value) { mutableStateOf(value) }
 479 |     val valid = if (range == null) text.isNotBlank() else text.toIntOrNull()?.let { it in range } == true
 480 |     val keyboard = LocalSoftwareKeyboardController.current
 481 |     Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
 482 |         OutlinedTextField(value = text, onValueChange = { text = if (range == null) it.take(96) else it.filter { c -> c.isDigit() }.take(6) },
 483 |             label = { Text(label) }, singleLine = true, isError = !valid, enabled = LocalTvInputEnabled.current,
```

**Uygulanan:** Açık Kaydet eylemi, geçersiz giriş açıklaması, geçersiz IME Done ile formun kaybolmaması.
**Doğrulama sınırı:** Kaynak ve mevcut giriş doğrulama host kontrolleri.

## R2-09 — Orta
Temel ayarlar uzun ve tek parça bir listeydi; bağlantı, başlangıç ve görünüm birbirine karışıyordu.

Önce: `ui/ReceiverSettings.kt:40` · Sonra: `ui/ReceiverSettings.kt:57`

### Önce — `ui/ReceiverSettings.kt`
```kotlin
  38 | 
  39 | @Composable
  40 | fun ReceiverSettings(viewModel: MainViewModel, onAdvanced: () -> Unit) {
  41 |     val name by viewModel.serverName.collectAsState()
  42 |     val auto by viewModel.autoStart.collectAsState()
  43 |     val boot by viewModel.bootAutoStart.collectAsState()
  44 |     val background by viewModel.runInBackground.collectAsState()
  45 |     val pin by viewModel.requirePin.collectAsState()
  46 |     val takeover by viewModel.allowNewConn.collectAsState()
  47 |     val fullscreen by viewModel.autoFullscreen.collectAsState()
```

### Sonra — `ui/ReceiverSettings.kt`
```kotlin
  55 | }
  56 | 
  57 | private enum class SettingsCategory { CONNECTION, STARTUP, APPEARANCE }
  58 | 
  59 | @Composable
  60 | fun ReceiverSettings(viewModel: MainViewModel, onAdvanced: () -> Unit, onModalChange: (Boolean) -> Unit = {}) {
  61 |     val name by viewModel.serverName.collectAsStateWithLifecycle()
  62 |     val auto by viewModel.autoStart.collectAsStateWithLifecycle()
  63 |     val boot by viewModel.bootAutoStart.collectAsStateWithLifecycle()
  64 |     val background by viewModel.runInBackground.collectAsStateWithLifecycle()
```

**Uygulanan:** Üç kategori, geniş ekranda yan seçim, dar ekranda yatay seçim; ayrı liste konumları.
**Doğrulama sınırı:** Kaynak incelemesi; gerçek ekran görüntüsü yok.

## R2-10 — Yüksek
Ayarların kalıcı commit işlemi alıcı başlatılırken ana iş parçacığında yapılıyordu.

Önce: `service/AirPlayService.kt:454` · Sonra: `service/AirPlayService.kt:471`

### Önce — `service/AirPlayService.kt`
```kotlin
 452 |         stopping = false
 453 |         _receiverState.update { it.sessionEnded(ReceiverNotice.NONE).copy(starting = true) }
 454 |         if (!pendingSettings.applyAtReceiverStart()) {
 455 |             log("Unable to commit receiver settings")
 456 |             _failStart()
 457 |             return
 458 |         }
 459 |         refreshPendingSettings()
 460 |         try {
 461 |             // Boot and notification paths may have read the old name before the draft commit.
```

### Sonra — `service/AirPlayService.kt`
```kotlin
 469 |                 // Durable preference commits are disk I/O, not UI work. Native startup remains
 470 |                 // on its existing thread with exactly its existing call sequence and parameters.
 471 |                 val committed = withContext(Dispatchers.IO) { pendingSettings.applyAtReceiverStart() }
 472 |                 if (!startRequests.isCurrent(request) || stopping) return@launch
 473 |                 if (!committed) {
 474 |                     log("Unable to commit receiver settings")
 475 |                     _failStart()
 476 |                     return@launch
 477 |                 }
 478 |                 refreshPendingSettings()
```

**Uygulanan:** Kalıcı yazma IO üzerinde. Taslak yazma/discard kilit doluyken beklemiyor; reddedilen işlem kaydedilmiş gibi gösterilmiyor. İptal edilen başlangıç token ile canlanmıyor.
**Doğrulama sınırı:** Gerçek taslak sınıfı ve token kapısı host testli; Android servis başlatması test edilmedi.

## R2-11 — Yüksek
Debug bilgisi hem her servis güncellemesinde hem ayrı saniyelik döngüde toplanıyordu.

Önce: `viewmodel/MainViewModel.kt:634` · Sonra: `viewmodel/MainViewModel.kt:714`

### Önce — `viewmodel/MainViewModel.kt`
```kotlin
 632 |             _trackInfo.value = it.trackInfo.value
 633 |             if (_debugEnabled.value) {
 634 |                 _debugInfo.value = it.collectDebugInfo()
 635 |             }
 636 |         }
 637 |     }
 638 | 
 639 |     init {
 640 |         viewModelScope.launch {
 641 |             _debugEnabled.collectLatest { enabled ->
```

### Sonra — `viewmodel/MainViewModel.kt`
```kotlin
 712 |     init {
 713 |         viewModelScope.launch {
 714 |             kotlinx.coroutines.flow.combine(_debugEnabled, _diagnosticsVisible) { enabled, visible -> enabled && visible }
 715 |                 .collectLatest { enabled ->
 716 |                 while (enabled) {
 717 |                     service?.let { _debugInfo.value = it.collectDebugInfo() }
 718 |                     delay(1000)
 719 |                 }
 720 |             }
 721 |         }
```

**Uygulanan:** Tek saniyelik toplama yolu; yalnız debug açık ve günlük sayfası STARTED yaşam döngüsündeyken.
**Doğrulama sınırı:** Kaynak incelemesi; native maliyet ölçümü yok.

## R2-12 — Orta
Canlı günlük listesi okunurken değişebiliyor; temizleme ve dışa aktarım tekrar tekrar başlatılabiliyordu.

Önce: `ui/LogsScreen.kt:26` · Sonra: `ui/LogsScreen.kt:61`

### Önce — `ui/LogsScreen.kt`
```kotlin
  24 | 
  25 | @Composable
  26 | fun LogsScreen(viewModel: MainViewModel) {
  27 |     val logs by viewModel.logs.collectAsState()
  28 |     val storageError by viewModel.logStorageError.collectAsState()
  29 |     val actionError by viewModel.logActionError.collectAsState()
  30 |     val debugEnabled by viewModel.debugEnabled.collectAsState()
  31 |     val debug by viewModel.debugInfo.collectAsState()
  32 |     val list = rememberLazyListState()
  33 |     var follow by remember { mutableStateOf(false) }
```

### Sonra — `ui/LogsScreen.kt`
```kotlin
  59 |         }
  60 |     }
  61 |     // Freeze the reading snapshot. Appending/rotating logs must never replace a focused line.
  62 |     LaunchedEffect(logs, follow, reading) {
  63 |         if ((follow && !reading) || !initialized) {
  64 |             displayed = logs
  65 |             initialized = logs.isNotEmpty()
  66 |             if (follow && displayed.isNotEmpty() && !list.isScrollInProgress) list.scrollToItem(displayed.lastIndex)
  67 |         }
  68 |     }
```

**Uygulanan:** Sabit okuma anlık görüntüsü, açık yenileme ve isteğe bağlı takip; export/clear meşgul kapısı.
**Doğrulama sınırı:** Günlük saklama önceki 76 test içinde; yeni UI davranışı kaynak kontrolü.

## R2-13 — Yüksek
Ağ servis kaydı hiç sonuçlanmazsa görünür bekleme belirsiz süre sürebiliyordu.

Önce: `discovery/NsdServiceManager.kt:36` · Sonra: `discovery/NsdServiceManager.kt:88`

### Önce — `discovery/NsdServiceManager.kt`
```kotlin
  34 |         register(Kind.AIRPLAY, serviceName, "_airplay._tcp", port, txtRecords)
  35 | 
  36 |     private fun register(kind: Kind, name: String, type: String, port: Int, txt: Map<String, String>) {
  37 |         if (released) return
  38 |         lateinit var listener: NsdManager.RegistrationListener
  39 |         fun current() = !released && listener ===
  40 |             (if (kind == Kind.RAOP) raopRegistration else airplayRegistration)
  41 |         listener = object : NsdManager.RegistrationListener {
  42 |             override fun onServiceRegistered(info: NsdServiceInfo) {
  43 |                 if (!current()) return
```

### Sonra — `discovery/NsdServiceManager.kt`
```kotlin
  86 |         }
  87 |         deadlines[kind] = deadline
  88 |         main.postDelayed(deadline, REGISTRATION_OBSERVATION_MS)
  89 |         try {
  90 |             val info = NsdServiceInfo().apply {
  91 |                 serviceName = name
  92 |                 serviceType = type
  93 |                 this.port = port
  94 |                 txt.forEach { (key, value) -> setAttribute(key, value) }
  95 |             }
```

**Uygulanan:** 15 saniyelik yalnız gözlem süresi ve sonuç geri bildirimi; tekrar eden yenileme engeli; eski kayıt callbackleri yenisini etkileyemiyor.
**Doğrulama sınırı:** Gerçek NSD sarmalayıcı Android test doubles ile host testli; gerçek mDNS değil.

## R2-14 — Yüksek
Servis bağlantısı kesilince bazı video/oynatım alanları eski değerleri taşıyordu.

Önce: `viewmodel/MainViewModel.kt:458` · Sonra: `viewmodel/MainViewModel.kt:513`

### Önce — `viewmodel/MainViewModel.kt`
```kotlin
 456 |     }
 457 | 
 458 |     fun unbindService() {
 459 |         serviceObservation?.cancel()
 460 |         serviceObservation = null
 461 |         service?.let { svc ->
 462 |             pendingMirrorSurface?.let(svc::clearVideoSurface)
 463 |             pendingPlaybackSurface?.let(svc::clearVideoPlaybackSurface)
 464 |         }
 465 |         controller.detach()
```

### Sonra — `viewmodel/MainViewModel.kt`
```kotlin
 511 |     }
 512 | 
 513 |     fun unbindService() {
 514 |         serviceObservation?.cancel()
 515 |         serviceObservation = null
 516 |         service?.let { svc ->
 517 |             pendingMirrorSurface?.let(svc::clearVideoSurface)
 518 |             pendingPlaybackSurface?.let(svc::clearVideoPlaybackSurface)
 519 |         }
 520 |         controller.detach()
```

**Uygulanan:** Eski oynatım, bekleme, metadata, eylem zamanı ve debug durumu temizleniyor.
**Doğrulama sınırı:** Kaynak incelemesi; Binder/Activity testi yapılmadı.

## R2-15 — Orta
Bildirim izni çağrısı kurulumdan önce, kullanıcı arka plan tercihini yapmadan geliyordu.

Önce: `MainActivity.kt:68` · Sonra: `MainActivity.kt:67`

### Önce — `MainActivity.kt`
```kotlin
  66 |         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
  67 |             ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
  68 |             notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
  69 |         }
  70 | 
  71 |         bindService(Intent(this, AirPlayService::class.java), connection, BIND_AUTO_CREATE)
  72 | 
  73 |         lifecycleScope.launch {
  74 |             repeatOnLifecycle(Lifecycle.State.STARTED) {
  75 |                 combine(viewModel.keepScreenOn, viewModel.connectionCount) { keep, conns ->
```

### Sonra — `MainActivity.kt`
```kotlin
  65 |         enableEdgeToEdge()
  66 | 
  67 |         // Ask once, in context, only after the user opts into background/boot operation.
  68 |         lifecycleScope.launch {
  69 |             repeatOnLifecycle(Lifecycle.State.STARTED) {
  70 |                 combine(viewModel.setupComplete, viewModel.bootAutoStart, viewModel.runInBackground) { setup, boot, background ->
  71 |                     setup && (boot || background)
  72 |                 }.collect { needsNotification ->
  73 |                     val experience = getSharedPreferences(UserExperiencePrefs.NAME, MODE_PRIVATE)
  74 |                     if (needsNotification && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
```

**Uygulanan:** İzin çağrısı kurulum sonrasına ve arka plan/boot tercihine bağlandı; uygulamanın tekrar istemesini önleyen kayıt eklendi.
**Doğrulama sınırı:** Kaynak incelemesi; sistem izin diyaloğu test edilmedi.

## R2-16 — Yüksek
Activity dururken boşluk yalnız bağlantı sayısı üzerinden değerlendiriliyordu; bekleyen başlangıç/medya durumu eksik kalabiliyordu.

Önce: `MainActivity.kt:158` · Sonra: `MainActivity.kt:171`

### Önce — `MainActivity.kt`
```kotlin
 156 |     }
 157 | 
 158 |     override fun onStop() {
 159 |         super.onStop()
 160 |         // never stop mid-session
 161 |         if (!viewModel.runInBackground.value && !isChangingConfigurations &&
 162 |             viewModel.serverState.value == AirPlayService.ServerState.RUNNING &&
 163 |             viewModel.connectionCount.value == 0) {
 164 |             viewModel.stopServer(userInitiated = false)
 165 |         }
```

### Sonra — `MainActivity.kt`
```kotlin
 169 |     }
 170 | 
 171 |     override fun onStop() {
 172 |         super.onStop()
 173 |         // never stop mid-session
 174 |         if (!viewModel.runInBackground.value && !isChangingConfigurations &&
 175 |             (viewModel.receiverState.value.running || viewModel.receiverState.value.starting) &&
 176 |             !viewModel.receiverState.value.sessionBusy) {
 177 |             viewModel.stopServer(userInitiated = false)
 178 |         }
```

**Uygulanan:** Arka plan kapalıysa durdurma kararı starting/running ve sessionBusy durumlarına dayanıyor; meşgul oturum korunuyor.
**Doğrulama sınırı:** sessionBusy kararları host testli; gerçek yaşam döngüsü testi yok.

## R2-17 — Orta
Seçilecek alıcı adı kırpılabiliyor; kullanıcı tam eşleşme bilgisini kaybedebiliyordu.

Önce: `ui/ReceiverHome.kt:76` · Sonra: `ui/ReceiverHome.kt:62`

### Önce — `ui/ReceiverHome.kt`
```kotlin
  74 |             Text(if (state.running) state.advertisedName.ifEmpty { savedName } else savedName,
  75 |                 style = MaterialTheme.typography.headlineMedium, color = AynaColors.Accent,
  76 |                 maxLines = 2, overflow = TextOverflow.Ellipsis)
  77 |             ReceiverStatus(state)
  78 |             Text(stringResource(when (state.phase) {
  79 |                 ReceiverPhase.STOPPED -> R.string.p_stopped_note
  80 |                 ReceiverPhase.ERROR -> R.string.p_start_error_note
  81 |                 ReceiverPhase.NETWORK_WAIT -> R.string.p_network_note
  82 |                 ReceiverPhase.DISCOVERY_WAIT, ReceiverPhase.DISCOVERY_ERROR -> R.string.p_discovery_note
  83 |                 ReceiverPhase.WAITING_VIDEO -> R.string.p_video_wait_body
```

### Sonra — `ui/ReceiverHome.kt`
```kotlin
  60 |             Text(stringResource(if (state.running) R.string.p_pick_name else R.string.p_name_when_started),
  61 |                 style = MaterialTheme.typography.labelSmall, color = AynaColors.Muted)
  62 |             // The full advertised name is essential to choosing the right receiver. Never ellipsize it.
  63 |             Text(if (state.running) state.advertisedName.ifEmpty { savedName } else savedName,
  64 |                 style = MaterialTheme.typography.headlineMedium, color = AynaColors.Text)
  65 |             ReceiverStatus(state)
  66 |             Text(stringResource(when (state.phase) {
  67 |                 ReceiverPhase.STOPPED -> R.string.p_stopped_note
  68 |                 ReceiverPhase.ERROR -> R.string.p_start_error_note
  69 |                 ReceiverPhase.NETWORK_WAIT -> R.string.p_network_note
```

**Uygulanan:** Ana kimlik kartında gerçek ilan edilen ad tam gösteriliyor; uzun ad ve büyük yazı için kaydırılabilir dar düzen eklendi.
**Doğrulama sınırı:** Kaynak incelemesi ve Preview tanımları; Preview render edilmedi.

## R2-18 — Orta
Görsel sistem çok sayıda eşit ağırlıklı teknik kart ve düğmeye dayanıyordu; odak/sekme/seçim rolleri ayrışmıyordu.

Önce: `ui/theme/Theme.kt:13` · Sonra: `ui/ReceiverScene.kt:18`

### Önce — `ui/theme/Theme.kt`
```kotlin
  11 | import androidx.compose.ui.unit.sp
  12 | 
  13 | object AynaColors {
  14 |     val Background = Color(0xFF08111C)
  15 |     val Panel = Color(0xFF111F2F)
  16 |     val Elevated = Color(0xFF192B3D)
  17 |     val Accent = Color(0xFF68E0D1)
  18 |     val Text = Color(0xFFF3F8FC)
  19 |     val Muted = Color(0xFFB6C7D6)
  20 |     val Border = Color(0xFF344B61)
```

### Sonra — `ui/ReceiverScene.kt`
```kotlin
  16 | /** Static decorative vector scene: no bitmap allocation, network asset, blur or perpetual animation. */
  17 | @Composable
  18 | fun ReceiverScene(modifier: Modifier = Modifier) {
  19 |     Canvas(modifier.clipToBounds()) {
  20 |         val factor = minOf(size.width / 440f, size.height / 200f)
  21 |         withTransform({
  22 |             translate((size.width - 440f * factor) / 2f, (size.height - 200f * factor) / 2f)
  23 |             scale(factor, factor, Offset.Zero)
  24 |         }) {
  25 |             drawCircle(Brush.radialGradient(listOf(AynaColors.Accent.copy(alpha = .10f), Color.Transparent),
```

**Uygulanan:** Grafit tema, kontrollü turkuaz vurgu, asimetrik kimlik/rehber alanları, özel TV/telefon vektör sahnesi, kategori düzeni ve kısa giriş geçişleri.
**Doğrulama sınırı:** Kaynak tasarımı; görsel beğeni ve TV okunurluğu fiziksel kabul almadı.

