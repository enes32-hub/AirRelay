import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import io.github.jqssun.airplay.Prefs
import io.github.jqssun.airplay.discovery.NsdServiceManager
import io.github.jqssun.airplay.receiver.*
import io.github.jqssun.airplay.service.AirPlayService
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

private var passed = 0
private fun case(name: String, body: () -> Unit) {
    try { body(); passed++; println("PASS $name") }
    catch (t: Throwable) { throw AssertionError("FAIL $name", t) }
}
private val ready = ReceiverUiState(running = true, network = LocalNetwork.WIFI,
    airplay = Advertisement.REGISTERED, raop = Advertisement.REGISTERED)
private class DiscoveryFixture : AutoCloseable {
    val directory = Files.createTempDirectory("airrelay-r2-nsd").toFile()
    val context = Context(directory)
    val nsd = NsdManager()
    val wifi = WifiManager()
    val events = mutableListOf<NsdServiceManager.Result>()
    val manager: NsdServiceManager
    init {
        Handler.reset()
        context.services[Context.NSD_SERVICE] = nsd
        context.services[Context.WIFI_SERVICE] = wifi
        manager = NsdServiceManager(context) { events += it }
    }
    override fun close() { manager.release(); Handler.reset(); directory.deleteRecursively() }
    fun airplay(name: String = "Salon", port: Int = 7000) = manager.registerAirplay(name, port, mapOf("features" to "0x1234", "pw" to "true"))
    fun raop() = manager.registerRaop("ABCDEF@Salon", 7000, mapOf("ch" to "2", "sr" to "44100"))
}
fun main() {
    case("stopped receiver primary action starts") { check(ReceiverUiState().homePrimaryAction() == HomePrimaryAction.START) }
    case("ready receiver primary action is guide, never stop") { check(ready.homePrimaryAction() == HomePrimaryAction.GUIDE) }
    case("mirroring primary action returns to content") { check(ready.copy(mirrorRequested = true).homePrimaryAction() == HomePrimaryAction.VIEW) }
    case("separate media playback primary action returns to content") { check(ready.copy(mediaPlaying = true).homePrimaryAction() == HomePrimaryAction.VIEW) }
    case("connected TCP alone does not claim visible content") { check(ready.copy(connections = 1).homePrimaryAction() == HomePrimaryAction.GUIDE) }
    case("advanced Back returns to settings") { check(ReceiverPage.ADVANCED.parent == ReceiverPage.SETTINGS) }
    case("logs Back returns to help") { check(ReceiverPage.LOGS.parent == ReceiverPage.HELP) }
    case("top-level Back returns home") { listOf(ReceiverPage.HELP,ReceiverPage.SETTINGS).forEach { check(it.parent == ReceiverPage.HOME) } }
    case("nested pages keep the proper top tab selected") { check(ReceiverPage.ADVANCED.tab == ReceiverPage.SETTINGS); check(ReceiverPage.LOGS.tab == ReceiverPage.HELP) }
    case("focus recovery prefers the remembered enabled control") {
        check(focusCandidateIds("b", "a", listOf("a","b","c")) == listOf("b","a","c"))
    }
    case("focus recovery skips vanished or disabled remembered control") {
        check(focusCandidateIds("gone", "b", listOf("a","b")) == listOf("b","a"))
    }
    case("focus fallback identities are unique, including matching default") {
        check(focusCandidateIds("b", "b", listOf("a","b")) == listOf("b","a"))
    }
    case("focus recovery handles no available controls") {
        check(focusCandidateIds("old", "start", emptyList()).isEmpty())
    }
    case("focus recovery preserves composition order without a preferred target") {
        check(focusCandidateIds(null, null, listOf("c","a","b")) == listOf("c","a","b"))
    }
    val ok = 23; val left = 21; val vol = 24
    case("hidden OK reveals without clicking") { val g = RemoteWakeGuard(); check(g.event(ok,true,0,true,false) == RemoteWakeGuard.Decision(true,true)) }
    case("opening release cannot click newly focused action") {
        val g = RemoteWakeGuard(); g.event(ok,true,0,true,false)
        check(g.event(ok,false,0,true,true) == RemoteWakeGuard.Decision(true,false))
        check(!g.event(ok,true,0,true,true).consume)
    }
    case("opening repeat stays consumed") {
        val g = RemoteWakeGuard(); g.event(ok,true,0,true,false)
        repeat(20) { check(g.event(ok,true,it+1,true,true).consume) }
        check(g.event(ok,false,0,true,true).consume)
    }
    case("lost opening release does not swallow next physical press") {
        val g = RemoteWakeGuard(); g.event(ok,true,0,true,false)
        check(!g.event(ok,true,0,true,true).consume); check(!g.event(ok,false,0,true,true).consume)
    }
    case("next directional key is not swallowed by old latch") {
        val g = RemoteWakeGuard(); g.event(ok,true,0,true,false)
        check(!g.event(left,true,0,true,true).consume)
    }
    case("volume key does not reveal or intercept Android volume") {
        val g = RemoteWakeGuard(); check(g.event(vol,true,0,false,false) == RemoteWakeGuard.Decision(false,false))
        check(!g.event(vol,false,0,false,false).consume)
    }
    case("hidden stray release does not reveal") { check(!RemoteWakeGuard().event(ok,false,0,true,false).reveal) }
    case("modal reset clears an in-flight opening gesture") {
        val g=RemoteWakeGuard();g.event(ok,true,0,true,false);g.reset();check(!g.event(ok,false,0,true,true).consume)
    }
    case("repeated hide/reveal sequences recover (5000 deterministic gestures)") {
        val g = RemoteWakeGuard()
        repeat(5000) { i ->
            check(g.event(ok,true,0,true,false).reveal)
            if (i % 3 != 0) check(g.event(ok,false,0,true,true).consume)
            check(!g.event(ok,true,0,true,true).consume)
            check(!g.event(ok,false,0,true,true).consume)
        }
    }
    case("overlay hides only in the single safe combination of 32 cases") {
        var allowed = 0
        repeat(32) { bits ->
            val visible=bits and 1 != 0;val waiting=bits and 2 != 0;val modal=bits and 4 != 0
            val network=bits and 8 != 0;val pressed=bits and 16 != 0
            val actual=OverlayPolicy.mayHide(visible,waiting,modal,network,pressed)
            check(actual == (bits == 1));if(actual)allowed++
        };check(allowed==1)
    }
    case("cancelled startup cannot resume") { val g=StartRequestGate();val t=g.begin();g.cancel();check(!g.isCurrent(t)) }
    case("new startup invalidates previous one") { val g=StartRequestGate();val a=g.begin();val b=g.begin();check(!g.isCurrent(a)&&g.isCurrent(b)) }
    case("repeated start/cancel ordering never revives an older token") {
        val g=StartRequestGate();repeat(1000) { val t=g.begin();check(g.isCurrent(t));g.cancel();check(!g.isCurrent(t)) }
    }
    case("discovery retry is unavailable while AirPlay registers") { check(!ready.copy(airplay=Advertisement.REGISTERING).canRetryDiscovery) }
    case("discovery retry is unavailable while RAOP registers") { check(!ready.copy(raop=Advertisement.REGISTERING).canRetryDiscovery) }
    case("discovery retry is unavailable while stopped or starting") { check(!ReceiverUiState().canRetryDiscovery);check(!ready.copy(starting=true).canRetryDiscovery) }
    case("failed discovery can be retried") { check(ready.copy(airplay=Advertisement.FAILED,raop=Advertisement.FAILED).canRetryDiscovery) }
    case("controller rejects unbound retry") { check(!ReceiverController().retryDiscovery()) }
    case("controller rejects pending registration without issuing commands") {
        val s=AirPlayService();s.receiverState.value=ready.copy(airplay=Advertisement.REGISTERING)
        val c=ReceiverController();c.attach(s);check(!c.retryDiscovery());check(s.commands.isEmpty())
    }
    case("accepted discovery retry emits only rediscover, never native restart") {
        val s=AirPlayService();s.receiverState.value=ready;val c=ReceiverController();c.attach(s)
        check(c.retryDiscovery());check(s.commands==listOf("rediscover"))
    }
    case("notice dismissal has its own command") {
        val s=AirPlayService();val c=ReceiverController();c.attach(s);c.dismissNotice();check(s.commands==listOf("dismiss-notice"))
    }
    val dir=Files.createTempDirectory("airrelay-r2-drafts").toFile()
    val ctx=Context(dir);val active=ctx.getSharedPreferences(Prefs.NAME,0);val drafts=PendingReceiverSettings(ctx)
    try {
        active.edit().putString(Prefs.SERVER_NAME,"Old TV").apply()
        case("accepted edit updates draft, not running configuration") {
            check(drafts.put(Prefs.SERVER_NAME,"Next TV"));check(active.getString(Prefs.SERVER_NAME,"")=="Old TV")
            check(drafts.string(Prefs.SERVER_NAME,"")=="Next TV")
        }
        case("UI edits fail fast while durable commit is blocked, without optimistic storage") {
            val entered=CountDownLatch(1);val release=CountDownLatch(1);val uiDone=CountDownLatch(1)
            val failures=AtomicReference<Throwable?>();val committed=AtomicReference<Boolean?>()
            active.beforeCommit={ entered.countDown();check(release.await(5,TimeUnit.SECONDS)) }
            val io=thread { try { committed.set(drafts.applyAtReceiverStart()) } catch(t:Throwable) { failures.set(t) } }
            check(entered.await(3,TimeUnit.SECONDS))
            val ui=thread {
                try {
                    check(!drafts.put(Prefs.SERVER_NAME,"Rejected TV"));check(!drafts.discard())
                    check(drafts.string(Prefs.SERVER_NAME,"")=="Next TV")
                } catch(t:Throwable) { failures.set(t) } finally { uiDone.countDown() }
            }
            try { check(uiDone.await(2,TimeUnit.SECONDS)) { "UI operation waited for disk lock" } }
            finally { release.countDown();io.join(4000);ui.join(4000);active.beforeCommit=null }
            failures.get()?.let { throw it };check(committed.get()==true)
            check(active.getString(Prefs.SERVER_NAME,"")=="Next TV");check(!drafts.hasPending())
        }
        case("editing resumes after commit lock release") { check(drafts.put(Prefs.SERVER_NAME,"Later TV"));check(drafts.discard());check(!drafts.hasPending()) }
        case("failed active persistence retains draft for recovery") {
            check(drafts.put(Prefs.SERVER_NAME,"Retry TV"));active.failCommit=true
            check(!drafts.applyAtReceiverStart());check(drafts.hasPending());active.failCommit=false
        }
        case("failed draft cleanup remains idempotently recoverable") {
            val store=ctx.getSharedPreferences("airrelay_next_receiver_start",0);store.failCommit=true
            check(!drafts.applyAtReceiverStart());check(drafts.hasPending());store.failCommit=false
            check(drafts.applyAtReceiverStart());check(!drafts.hasPending());check(active.getString(Prefs.SERVER_NAME,"")=="Retry TV")
        }
        case("returning to saved default removes the unnecessary pending flag") {
            drafts.boolean(Prefs.REQUIRE_PIN,Prefs.DEF_REQUIRE_PIN)
            check(drafts.put(Prefs.REQUIRE_PIN,!Prefs.DEF_REQUIRE_PIN));check(drafts.hasPending())
            check(drafts.put(Prefs.REQUIRE_PIN,Prefs.DEF_REQUIRE_PIN));check(!drafts.hasPending())
        }
    } finally { dir.deleteRecursively() }
    case("NSD passes the exact AirPlay name, port, type and TXT values") {
        DiscoveryFixture().use { f ->
            f.airplay();val request=f.nsd.requests.single()
            check(request.info.serviceName=="Salon"&&request.info.port==7000&&request.info.serviceType=="_airplay._tcp")
            check(request.info.attributes==mapOf("features" to "0x1234","pw" to "true"))
            check(request.protocol==NsdManager.PROTOCOL_DNS_SD)
            check(f.events.single().state==Advertisement.REGISTERING)
        }
    }
    case("NSD passes the exact RAOP name, port, type and TXT values") {
        DiscoveryFixture().use { f ->
            f.raop();val request=f.nsd.requests.single()
            check(request.info.serviceName=="ABCDEF@Salon"&&request.info.port==7000&&request.info.serviceType=="_raop._tcp")
            check(request.info.attributes==mapOf("ch" to "2","sr" to "44100"))
        }
    }
    case("NSD observation does not claim success before callback") {
        DiscoveryFixture().use { f -> f.airplay();Handler.advanceBy(14999);check(f.events.last().state==Advertisement.REGISTERING) }
    }
    case("NSD observation timeout reports failure but does not unregister or stop transport") {
        DiscoveryFixture().use { f ->
            f.airplay();Handler.advanceBy(15000);check(f.events.last().errorCode==NsdServiceManager.ERROR_TIMEOUT)
            check(f.nsd.unregistered.isEmpty());check(Handler.pending==0)
        }
    }
    case("successful registration uses Android's actual renamed service name") {
        DiscoveryFixture().use { f ->
            f.airplay();val req=f.nsd.requests.single();val response=NsdServiceInfo().apply { serviceName="Salon (2)" }
            req.listener.onServiceRegistered(response);check(f.events.last().actualName=="Salon (2)")
            Handler.advanceBy(30000);check(f.events.last().state==Advertisement.REGISTERED);check(Handler.pending==0)
        }
    }
    case("late success after observation timeout can still report actual registration") {
        DiscoveryFixture().use { f -> f.airplay();Handler.advanceBy(15000);val req=f.nsd.requests.single()
            req.listener.onServiceRegistered(req.info);check(f.events.last().state==Advertisement.REGISTERED) }
    }
    case("explicit registration failure cancels observation timer") {
        DiscoveryFixture().use { f -> f.airplay();val req=f.nsd.requests.single();req.listener.onRegistrationFailed(req.info,42)
            Handler.advanceBy(30000);check(f.events.last().errorCode==42);check(Handler.pending==0) }
    }
    case("old registration callback cannot make replacement ready") {
        DiscoveryFixture().use { f -> f.airplay();val old=f.nsd.requests.last();f.manager.unregisterAll();f.airplay("New TV")
            val n=f.events.size;old.listener.onServiceRegistered(old.info);check(f.events.size==n)
            Handler.advanceBy(15000);check(f.events.last().errorCode==NsdServiceManager.ERROR_TIMEOUT) }
    }
    case("old unregistered callback cannot cancel a new attempt's deadline") {
        DiscoveryFixture().use { f -> f.airplay();val old=f.nsd.requests.last();f.manager.unregisterAll();f.airplay("New TV")
            old.listener.onServiceUnregistered(old.info);Handler.advanceBy(15000)
            check(f.events.last().errorCode==NsdServiceManager.ERROR_TIMEOUT) }
    }
    case("old unregister failure cannot cancel a new attempt's deadline") {
        DiscoveryFixture().use { f -> f.airplay();val old=f.nsd.requests.last();f.manager.unregisterAll();f.airplay("New TV")
            old.listener.onUnregistrationFailed(old.info,7);Handler.advanceBy(15000)
            check(f.events.last().errorCode==NsdServiceManager.ERROR_TIMEOUT) }
    }
    case("unregister invalidates callbacks and removes pending deadlines") {
        DiscoveryFixture().use { f -> f.airplay();f.raop();val requests=f.nsd.requests.toList();f.manager.unregisterAll()
            check(Handler.pending==0);val n=f.events.size;requests.forEach { it.listener.onServiceRegistered(it.info) }
            Handler.advanceBy(30000);check(f.events.size==n);check(f.nsd.unregistered.size==2) }
    }
    case("RAOP success does not cancel AirPlay's separate deadline") {
        DiscoveryFixture().use { f -> f.airplay();f.raop();val r=f.nsd.requests.last();r.listener.onServiceRegistered(r.info)
            Handler.advanceBy(15000);check(f.events.last().kind==NsdServiceManager.Kind.AIRPLAY)
            check(f.events.last().errorCode==NsdServiceManager.ERROR_TIMEOUT) }
    }
    case("release clears timers and ignores all late callback kinds") {
        DiscoveryFixture().use { f -> f.airplay();val req=f.nsd.requests.single();f.manager.release();val n=f.events.size
            req.listener.onServiceRegistered(req.info);req.listener.onRegistrationFailed(req.info,1)
            req.listener.onServiceUnregistered(req.info);req.listener.onUnregistrationFailed(req.info,2)
            Handler.advanceBy(30000);check(f.events.size==n);check(Handler.pending==0) }
    }
    case("released manager cannot register new service") {
        DiscoveryFixture().use { f -> f.manager.release();f.airplay();check(f.nsd.requests.isEmpty()&&f.events.isEmpty()) }
    }
    case("permission denial is surfaced and timer cleaned") {
        DiscoveryFixture().use { f -> f.nsd.registrationException=SecurityException();f.airplay()
            check(f.events.last().errorCode==NsdServiceManager.ERROR_PERMISSION);check(Handler.pending==0) }
    }
    case("platform failure is surfaced and timer cleaned") {
        DiscoveryFixture().use { f -> f.nsd.registrationException=IllegalStateException();f.airplay()
            check(f.events.last().errorCode==NsdServiceManager.ERROR_PLATFORM);check(Handler.pending==0) }
    }
    case("multicast lock is released exactly through explicit lifecycle cleanup") {
        DiscoveryFixture().use { f -> f.manager.acquireMulticastLock();check(f.wifi.locks.single().isHeld)
            f.manager.release();check(!f.wifi.locks.single().isHeld);f.manager.release() }
    }
    case("Ethernet-only TV without Wi-Fi manager can still register discovery") {
        DiscoveryFixture().use { f ->
            f.context.services.remove(Context.WIFI_SERVICE)
            f.manager.acquireMulticastLock()
            check(f.wifi.locks.isEmpty())
            f.airplay()
            val request = f.nsd.requests.single()
            request.listener.onServiceRegistered(request.info)
            check(f.events.last().state == Advertisement.REGISTERED)
        }
    }
    println("REFINEMENT HOST CHECKS: $passed passed. Actual decision/settings/NSD-wrapper code, explicit Android doubles.")
    println("NOT RUN: Compose type checking, Android build, input instrumentation, real NSD, JNI, emulator, device or media performance.")
}
