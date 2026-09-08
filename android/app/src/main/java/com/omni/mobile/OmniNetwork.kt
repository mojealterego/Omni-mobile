package com.omni.mobile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

class OmniSocket(private val url: String, private val onEvent: (OmniServerEvent)->Unit) {
    private val client = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).retryOnConnectionFailure(true).build()
    private var socket: WebSocket? = null
    fun connect() {
        if (socket != null) return
        socket = client.newWebSocket(Request.Builder().url(url).build(), object: WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) { onEvent(OmniServerEvent.Connected) }
            override fun onMessage(ws: WebSocket, text: String) { onEvent(OmniServerEvent.Message(text)) }
            override fun onClosed(ws: WebSocket, code: Int, reason: String) { socket=null; onEvent(OmniServerEvent.Disconnected(reason)) }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) { socket=null; onEvent(OmniServerEvent.Error(t.message ?: "Transport error")) }
        })
    }
    fun dispatchMission(mission: String, maxCycles: Int) {
        socket?.send(JSONObject().put("mission", mission).put("session_id", UUID.randomUUID().toString()).put("max_cycles", maxCycles).toString())
    }
    fun close() { socket?.close(1000, "client"); socket=null }
}
sealed interface OmniServerEvent {
    data object Connected: OmniServerEvent
    data class Disconnected(val reason:String): OmniServerEvent
    data class Error(val message:String): OmniServerEvent
    data class Message(val raw:String): OmniServerEvent
}

class OmniViewModel(private val socketFactory: (String,(OmniServerEvent)->Unit)->OmniSocket = { url,cb -> OmniSocket(url,cb) }) : androidx.lifecycle.ViewModel() {
    private val _ui = MutableStateFlow(OmniUiState())
    val ui: StateFlow<OmniUiState> = _ui.asStateFlow()
    private val socket = socketFactory(BuildConfig.OMNI_WS_URL) { reduce(it) }
    init { socket.connect() }
    fun setTab(tab: OmniTab) { _ui.value=_ui.value.copy(tab=tab) }
    fun setMission(value:String) { _ui.value=_ui.value.copy(mission=value) }
    fun start() { val s=_ui.value; if(!s.connected || s.running || s.mission.isBlank()) return; _ui.value=s.copy(running=true,cycle=0,fitness=0f); push("COMMAND","Mission dispatched"); socket.dispatchMission(s.mission,s.maxCycles) }
    private fun reduce(e:OmniServerEvent) { when(e) {
        OmniServerEvent.Connected -> { _ui.value=_ui.value.copy(connected=true); push("SYSTEM","WebSocket connected") }
        is OmniServerEvent.Disconnected -> { _ui.value=_ui.value.copy(connected=false,running=false); push("SYSTEM","Disconnected: ${e.reason}") }
        is OmniServerEvent.Error -> { _ui.value=_ui.value.copy(connected=false,running=false); push("ERROR",e.message) }
        is OmniServerEvent.Message -> parse(e.raw)
    }}
    private fun parse(raw:String) { try { val o=JSONObject(raw); val type=o.optString("type","event"); if(type=="complete"){_ui.value=_ui.value.copy(running=false);push("COMPLETE","Evolution complete");return}; if(type=="error"){_ui.value=_ui.value.copy(running=false);push("ERROR",o.optString("message","Unknown error"));return}; val p=o.optJSONObject("payload"); val cycle=p?.optInt("cycle",_ui.value.cycle)?:_ui.value.cycle; val msg=p?.optString("message")?:o.optString("message",type); val fitness=p?.optDouble("fitness",_ui.value.fitness.toDouble())?.toFloat()?:_ui.value.fitness; val champion=p?.optString("candidate_id")?.takeIf{it.isNotBlank()}?:_ui.value.champion; _ui.value=_ui.value.copy(cycle=cycle,fitness=fitness.coerceIn(0f,1f),champion=champion);push(type.uppercase(),msg) } catch(_:Exception){push("EVENT",raw.take(280))} }
    private fun push(phase:String,message:String){_ui.value=_ui.value.copy(events=(_ui.value.events+OmniEvent(phase,message)).takeLast(100))}
    override fun onCleared(){socket.close();super.onCleared()}
}
enum class OmniTab(val title:String){COMMAND("Command"),EVOLUTION("Evolution"),MEMORY("Memory"),SYSTEM("System")}
data class OmniEvent(val phase:String,val message:String,val ts:Long=System.currentTimeMillis())
data class OmniUiState(val tab:OmniTab=OmniTab.COMMAND,val mission:String="",val connected:Boolean=false,val running:Boolean=false,val cycle:Int=0,val maxCycles:Int=8,val fitness:Float=0f,val mcts:Int=0,val pareto:Int=0,val rsi:String="READY",val champion:String="—",val events:List<OmniEvent> = emptyList())
