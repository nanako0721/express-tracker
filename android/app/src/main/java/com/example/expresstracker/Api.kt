package com.example.expresstracker

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64

data class TrackingEvent(val time: String, val content: String)
data class Shipment(val id: Long, val number: String, val carrier: String, val name: String, val status: Int, val statusText: String, val company: String, val updatedAt: String, val events: List<TrackingEvent>, val pickupCode: String = "", val pickupStation: String = "", val pickupAddress: String = "")
data class Account(val id: Long, val email: String, val nickname: String, val avatarUrl: String)
data class AccountSummary(val shipments: Int, val events: Int, val devices: Int, val lastSync: String, val preferencesSynced: Boolean)
data class LoginDevice(val id: Long, val name: String, val androidVersion: String, val appVersion: String, val firstLogin: String, val lastActive: String, val current: Boolean, val pushEnabled: Boolean)
data class RefreshAllResult(val success: Int, val failed: Int, val remaining: Int)
data class NotificationSettings(val pushEnabled:Boolean=true,val notifyTransit:Boolean=true,val notifyDelivery:Boolean=true,val notifyException:Boolean=true,val notifySigned:Boolean=true,val emailEnabled:Boolean=false,val smtpHost:String="",val smtpPort:Int=465,val smtpUser:String="",val smtpPassword:String="",val hasSmtpPassword:Boolean=false,val smtpFrom:String="",val smtpTo:String="",val smtpSecurity:String="ssl",val mailSubject:String="【快递追踪】{{company}} {{status}}",val mailBody:String="{{name}}\n运单号：{{number}}\n快递公司：{{company}}\n当前状态：{{status}}\n最新进度：{{latest}}\n更新时间：{{updated_at}}")

class Api(private val context: Context) {
    private val prefs = context.getSharedPreferences("offline_data", Context.MODE_PRIVATE)
    private val pickupPrefs = context.getSharedPreferences("pickup_settings", Context.MODE_PRIVATE)
    val isLoggedIn get() = true
    val pickupMobile get() = pickupPrefs.getString("receiver_mobile", "").orEmpty()
    fun savePickupMobile(value: String) = pickupPrefs.edit().putString("receiver_mobile", value).apply()

    suspend fun requestCode(email: String) = Unit
    suspend fun verifyCode(email: String, code: String) = Unit
    fun logout() = Unit

    suspend fun list(): List<Shipment> = withContext(Dispatchers.IO) { load() }
    suspend fun account(): Account = Account(1, "本地离线账户", prefs.getString("nickname", "离线用户").orEmpty(), "")
    suspend fun updateNickname(nickname: String): Account {
        prefs.edit().putString("nickname", nickname).apply()
        return account()
    }
    suspend fun uploadAvatar(uri: Uri): Account = account()

    suspend fun add(number: String, name: String, phone: String): Shipment = withContext(Dispatchers.IO) {
        val all = load().toMutableList()
        if (all.any { it.number.equals(number, true) }) throw IllegalStateException("该运单已存在")
        val item = Shipment((all.maxOfOrNull { it.id } ?: 0) + 1, number.trim(), "auto", name.trim(), -1, "待查询", "待识别", Instant.now().toString(), emptyList())
        all.add(0, item)
        prefs.edit().putString("phone_${item.id}", phone).apply()
        save(all)
        item
    }

    suspend fun refresh(id: Long): Shipment = withContext(Dispatchers.IO) {
        val current = load().firstOrNull { it.id == id } ?: throw IllegalStateException("运单不存在")
        if (BuildConfig.ALAPI_TOKEN.isBlank()) throw IllegalStateException("离线配置缺少 ALAPI_TOKEN")
        val request = JSONObject().put("token", BuildConfig.ALAPI_TOKEN).put("number", current.number).put("com", current.carrier)
        prefs.getString("phone_$id", "")?.takeIf { it.isNotBlank() }?.let { request.put("phone", it) }
        val response = postJson("https://v3.alapi.cn/api/tracking", request)
        if (!response.optBoolean("success")) throw IllegalStateException(response.optString("message", "物流查询失败"))
        val data = response.optJSONObject("data") ?: throw IllegalStateException("物流返回数据为空")
        val info = data.optJSONArray("info") ?: JSONArray()
        val events = (0 until info.length()).map { i -> info.getJSONObject(i).let { TrackingEvent(it.optString("time"), it.optString("content")) } }
        val updated = current.copy(
            status = data.optInt("status", current.status),
            statusText = data.optString("status_text", current.statusText),
            company = data.optString("exp_name", current.company),
            updatedAt = Instant.now().toString(),
            events = events
        )
        replace(updated)
        updated
    }

    suspend fun queryPickupCode(id: Long): Shipment = withContext(Dispatchers.IO) {
        val current = load().firstOrNull { it.id == id } ?: throw IllegalStateException("运单不存在")
        if (pickupMobile.length != 11) throw IllegalStateException("请先在“我的-收件手机号”填写11位手机号")
        if (BuildConfig.KDNIAO_EBUSINESS_ID.isBlank() || BuildConfig.KDNIAO_APP_KEY.isBlank()) throw IllegalStateException("离线配置缺少快递鸟凭据")
        val shipper = carrierCode(current)
        if (shipper.isBlank()) throw IllegalStateException("暂不支持该快递公司的取件码查询")
        val payload = JSONObject().put("ShipperCode", shipper).put("LogisticCode", current.number).put("ReceiverMobile", pickupMobile)
        val subscribed = kdniao("6019", payload)
        val subscribeReason = subscribed.optString("Reason")
        if (!subscribed.optBoolean("Success") && !subscribeReason.contains("Subscribe repeat", true) && !subscribeReason.contains("重复订阅")) {
            throw IllegalStateException("取件码订阅失败：$subscribeReason")
        }
        val result = kdniao("6020", payload)
        if (!result.optBoolean("Success")) throw IllegalStateException(result.optString("Reason", "取件码查询失败"))
        val updated = current.copy(
            pickupCode = result.optString("PickUpCode"),
            pickupStation = result.optString("PickUpStation"),
            pickupAddress = result.optString("PickUpAddress")
        )
        replace(updated)
        updated
    }

    suspend fun updateNote(id: Long, name: String): Shipment = withContext(Dispatchers.IO) {
        val current = load().firstOrNull { it.id == id } ?: throw IllegalStateException("运单不存在")
        current.copy(name = name).also { replace(it) }
    }
    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { save(load().filterNot { it.id == id }); prefs.edit().remove("phone_$id").apply() }
    suspend fun refreshAll(): RefreshAllResult {
        var success = 0
        var failed = 0
        load().forEach { runCatching { refresh(it.id) }.onSuccess { success++ }.onFailure { failed++ } }
        return RefreshAllResult(success, failed, 0)
    }
    suspend fun summary(): AccountSummary { val all=load();return AccountSummary(all.size,all.sumOf{it.events.size},1,all.maxOfOrNull{it.updatedAt}.orEmpty(),true) }
    suspend fun devices(): List<LoginDevice> = emptyList()
    suspend fun removeDevice(id: Long) = Unit
    suspend fun removeOtherDevices() = Unit
    suspend fun requestDeleteCode() = Unit
    suspend fun deleteAccount(code: String) { prefs.edit().clear().apply();pickupPrefs.edit().clear().apply() }
    suspend fun registerPushToken() = Unit
    suspend fun testEmail() { throw IllegalStateException("离线版不提供邮件通知") }
    suspend fun notificationSettings() = NotificationSettings(pushEnabled=false)
    suspend fun saveNotificationSettings(s: NotificationSettings) = s

    private fun postJson(url: String, body: JSONObject): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST";conn.connectTimeout=10000;conn.readTimeout=20000;conn.doOutput=true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val text=(if(conn.responseCode in 200..299)conn.inputStream else conn.errorStream).bufferedReader().use{it.readText()}
        return JSONObject(text)
    }

    private fun kdniao(requestType: String, payload: JSONObject): JSONObject {
        val data = payload.toString()
        val digest = MessageDigest.getInstance("MD5").digest((data + BuildConfig.KDNIAO_APP_KEY).toByteArray()).joinToString("") { "%02x".format(it) }
        val sign = Base64.getEncoder().encodeToString(digest.toByteArray())
        fun enc(value:String)=URLEncoder.encode(value,"UTF-8")
        val form="RequestData=${enc(data)}&EBusinessID=${enc(BuildConfig.KDNIAO_EBUSINESS_ID)}&RequestType=${enc(requestType)}&DataSign=${enc(sign)}&DataType=2"
        val conn=URL(BuildConfig.KDNIAO_API_URL).openConnection() as HttpURLConnection
        conn.requestMethod="POST";conn.connectTimeout=10000;conn.readTimeout=20000;conn.doOutput=true
        conn.setRequestProperty("Content-Type","application/x-www-form-urlencoded;charset=utf-8")
        conn.outputStream.use{it.write(form.toByteArray())}
        val text=(if(conn.responseCode in 200..299)conn.inputStream else conn.errorStream).bufferedReader().use{it.readText()}
        return JSONObject(text)
    }

    @Synchronized private fun load(): List<Shipment> {
        val array=runCatching{JSONArray(prefs.getString("shipments","[]"))}.getOrDefault(JSONArray())
        return (0 until array.length()).map{parseShipment(array.getJSONObject(it))}
    }
    @Synchronized private fun save(items: List<Shipment>) {
        val array=JSONArray();items.forEach{array.put(toJson(it))};prefs.edit().putString("shipments",array.toString()).apply()
    }
    private fun replace(item:Shipment){val all=load().toMutableList();val index=all.indexOfFirst{it.id==item.id};if(index>=0)all[index]=item;save(all)}
    private fun toJson(s:Shipment)=JSONObject().put("id",s.id).put("number",s.number).put("carrier",s.carrier).put("name",s.name).put("status",s.status).put("status_text",s.statusText).put("company",s.company).put("updated_at",s.updatedAt).put("pickup_code",s.pickupCode).put("pickup_station",s.pickupStation).put("pickup_address",s.pickupAddress).put("events",JSONArray().apply{s.events.forEach{put(JSONObject().put("time",it.time).put("content",it.content))}})
    private fun parseShipment(o:JSONObject):Shipment{val e=o.optJSONArray("events")?:JSONArray();return Shipment(o.optLong("id"),o.optString("number"),o.optString("carrier","auto"),o.optString("name"),o.optInt("status",-1),o.optString("status_text","待查询"),o.optString("company","待识别"),o.optString("updated_at"),(0 until e.length()).map{e.getJSONObject(it).let{x->TrackingEvent(x.optString("time"),x.optString("content"))}},o.optString("pickup_code"),o.optString("pickup_station"),o.optString("pickup_address"))}

    private fun carrierCode(s:Shipment):String {
        val raw=s.carrier.trim().uppercase();if(raw in setOf("SF","YTO","ZTO","YD","STO","EMS","YZPY","JD","JDKY","JTSD","DBL","DBLKY","HTKY","UC","KYSY","ZJS","SURE","FWX","ANE","DNWL","SNWL","ZYE","SX"))return raw
        val v=(s.carrier+" "+s.company).lowercase()
        return when { "京东快运" in v->"JDKY";"京东" in v->"JD";"极兔" in v||"j&t" in v->"JTSD";"顺丰" in v->"SF";"圆通" in v->"YTO";"中通" in v->"ZTO";"韵达" in v->"YD";"申通" in v->"STO";"ems" in v||"邮政特快" in v->"EMS";"邮政" in v->"YZPY";"德邦快运" in v->"DBLKY";"德邦" in v->"DBL";"百世" in v||"汇通" in v->"HTKY";"优速" in v->"UC";"跨越" in v->"KYSY";"宅急送" in v->"ZJS";"速尔" in v->"SURE";"丰网" in v->"FWX";"安能" in v->"ANE";"丹鸟" in v||"菜鸟直送" in v->"DNWL";"苏宁" in v->"SNWL";"众邮" in v->"ZYE";"顺心捷达" in v->"SX";else->"" }
    }
}
