package com.example.expresstracker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.content.Intent
import android.content.ClipboardManager
import android.content.ClipData
import android.app.Activity
import android.widget.Toast
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.lightColorScheme
import top.yukonga.miuix.kmp.theme.darkColorScheme

private val Blue = Color(0xFF3482FF)
private var Page = Color(0xFFF5F5F7)
private var CardColor = Color.White

private enum class Screen { Login, Main, Detail, Profile, Nickname, PickupMobile, Backup, Push, Mail, Theme, Security, Devices, DeleteAccount, About, Privacy, Agreement }

private val demoShipments = listOf(
    Shipment(1, "SF1234567890", "sf", "新手机", 5, "派送中", "顺丰速运", "刚刚", listOf(
        TrackingEvent("今天 09:26", "快件正在派送中，请保持电话畅通"), TrackingEvent("昨天 22:10", "快件已到达上海转运中心"), TrackingEvent("昨天 18:45", "快件已从杭州转运中心发出")
    )),
    Shipment(2, "YT9876543210", "yto", "生活用品", 0, "运输中", "圆通速递", "2小时前", listOf(
        TrackingEvent("今天 07:42", "快件已发往下一站"), TrackingEvent("昨天 21:03", "快件已到达广州集运中心")
    )),
    Shipment(3, "ZT20260829001", "zto", "", 3, "已签收", "中通快递", "昨天", listOf(
        TrackingEvent("昨天 18:03", "快件已由驿站代收"), TrackingEvent("昨天 12:20", "快件开始派送")
    ))
)

@Composable
fun ExpressTrackerApp() {
    val context = LocalContext.current
    val api = remember { Api(context.applicationContext) }
    var screen by remember { mutableStateOf(if (api.isLoggedIn) Screen.Main else Screen.Login) }
    var selected by remember { mutableStateOf<Shipment?>(null) }
    var shipments by remember { mutableStateOf<List<Shipment>>(emptyList()) }
    var account by remember { mutableStateOf<Account?>(null) }
    var summary by remember { mutableStateOf<AccountSummary?>(null) }
    var loading by remember { mutableStateOf(false) }
    var mainTab by remember { mutableIntStateOf(0) }
    var pickupMobile by remember { mutableStateOf(api.pickupMobile) }
    val scope = rememberCoroutineScope()
    val uiPrefs=remember{context.getSharedPreferences("ui",android.content.Context.MODE_PRIVATE)}
    var dark by remember{mutableStateOf(uiPrefs.getBoolean("dark_mode",false))}
    Page=if(dark) Color(0xFF111216) else Color(0xFFF5F5F7);CardColor=if(dark) Color(0xFF1E2026) else Color.White
    SideEffect { (context as? Activity)?.window?.let { window -> window.statusBarColor=Page.toArgb();window.navigationBarColor=CardColor.toArgb();WindowCompat.getInsetsController(window,window.decorView).apply { isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark } } }
    suspend fun reload() { loading = true; runCatching { shipments = api.list(); account = api.account(); summary=api.summary() }; loading = false }
    var autoUpdate by remember { mutableStateOf(false) }
    LaunchedEffect(screen) { if (screen == Screen.Main && api.isLoggedIn) { reload(); runCatching { api.registerPushToken() }; if(UpdateHelper.shouldAutoCheck(context) && UpdateHelper.exists()) autoUpdate=true } }
    BackHandler(enabled = screen != Screen.Login && screen != Screen.Main) {
        screen = Screen.Main
    }
    BackHandler(enabled = screen == Screen.Main && mainTab != 0) {
        mainTab = 0
    }
    MiuixTheme(colors = if(dark) darkColorScheme(primary = Blue) else lightColorScheme(primary = Blue)) {
        MaterialTheme(colorScheme = if(dark) androidx.compose.material3.darkColorScheme(primary=Blue,background=Page,surface=CardColor) else androidx.compose.material3.lightColorScheme(primary = Blue, background = Page,surface=CardColor)) {
            when (screen) {
                Screen.Login -> LoginScreen(
                    requestCode = { api.requestCode(it) },
                    login = { email, code -> api.verifyCode(email, code); screen = Screen.Main }
                )
                Screen.Main -> MainScreen(mainTab, { mainTab = it }, shipments, account, summary, pickupMobile, loading, { selected = it; screen = Screen.Detail }, { screen = it }, { api.logout();shipments=emptyList();account=null;screen=Screen.Login }, {id->scope.launch{runCatching{api.delete(id)}.onSuccess{reload()}}}, {scope.launch{loading=true;runCatching{api.refreshAll()};reload()}}) { number, name, phone -> scope.launch { runCatching { val added = api.add(number,name,phone); shipments = listOf(added) + shipments; val fresh = api.refresh(added.id); shipments = shipments.map { if(it.id==fresh.id) fresh else it } } } }
                Screen.Detail -> selected?.let { current -> DetailScreen(current, { note -> scope.launch { runCatching { api.updateNote(current.id,note) }.onSuccess { updated -> selected=updated;shipments=shipments.map{if(it.id==updated.id)updated else it} } } }, { finished -> scope.launch { runCatching { api.queryPickupCode(current.id) }.onSuccess { updated -> selected=updated;shipments=shipments.map{if(it.id==updated.id)updated else it};Toast.makeText(context,if(updated.pickupCode.isBlank()) "暂未获取到取件码" else "已获取取件码",Toast.LENGTH_SHORT).show() }.onFailure { Toast.makeText(context,it.message ?: "查询失败",Toast.LENGTH_LONG).show() };finished() } }, { screen = Screen.Main }) } ?: run { screen = Screen.Main }
                Screen.Profile -> ProfileScreen(account, { uri -> scope.launch { runCatching { account = api.uploadAvatar(uri) } } }) { screen = Screen.Main }
                Screen.Nickname -> NicknameScreen(account?.nickname.orEmpty(), { nickname -> scope.launch { runCatching { account = api.updateNickname(nickname) }.onSuccess { screen = Screen.Main } } }) { screen = Screen.Main }
                Screen.PickupMobile -> PickupMobileScreen(pickupMobile, { pickupMobile=it;api.savePickupMobile(it);screen=Screen.Main }) { screen=Screen.Main }
                Screen.Backup -> BackupScreen(api,summary,{scope.launch{reload()}}) { screen = Screen.Main }
                Screen.Push -> PushScreen(api) { screen = Screen.Main }
                Screen.Mail -> MailScreen(api) { screen = Screen.Main }
                Screen.Theme -> ThemeScreen(dark,{dark=it;uiPrefs.edit().putBoolean("dark_mode",it).apply()}) {screen=Screen.Main}
                Screen.Security -> SecurityScreen(api,account,summary,{screen=Screen.Devices},{screen=Screen.DeleteAccount},{scope.launch{runCatching{api.removeOtherDevices()}.onSuccess{reload()}}}) { screen = Screen.Main }
                Screen.Devices -> DevicesScreen(api){screen=Screen.Security}
                Screen.DeleteAccount -> DeleteAccountScreen(api,{account=null;shipments=emptyList();screen=Screen.Login}){screen=Screen.Security}
                Screen.About -> AboutScreen({screen=Screen.Privacy},{screen=Screen.Agreement}) { screen = Screen.Main }
                Screen.Privacy -> LegalScreen("隐私政策",privacyText){screen=Screen.About}
                Screen.Agreement -> LegalScreen("用户协议",agreementText){screen=Screen.About}
            }
            if(autoUpdate) UpdateDialog({autoUpdate=false},{uiPrefs.edit().putBoolean("auto_update_disabled",true).apply();autoUpdate=false})
        }
    }
}

@Composable
private fun LoginScreen(requestCode: suspend (String) -> Unit, login: suspend (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var countdown by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(countdown) { if (countdown > 0) { delay(1000); countdown-- } }
    Box(Modifier.fillMaxSize().background(Page).padding(horizontal = 28.dp)) {
        Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            androidx.compose.foundation.Image(painterResource(R.drawable.app_icon), "软件图标", Modifier.size(82.dp).clip(RoundedCornerShape(24.dp)), contentScale = ContentScale.Crop)
            Text("快递查询", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("登录后，所有运单都会安全同步到云端", color = Color.Gray)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth().height(58.dp), label = { Text("邮箱地址") }, leadingIcon = { Icon(Icons.Rounded.Email, null) }, singleLine = true, shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(code, { code = it.take(6) }, Modifier.weight(.62f).height(58.dp), label = { Text("验证码") }, singleLine = true, shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Button(onClick = { scope.launch { busy = true; error = null; runCatching { requestCode(email.trim()) }.onSuccess { countdown = 30 }.onFailure { error = it.message }; busy = false } }, enabled = email.contains("@") && countdown == 0 && !busy, modifier = Modifier.weight(.38f).height(58.dp), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text(if (countdown > 0) "${countdown}秒后重发" else "获取验证码", maxLines = 1, fontSize = 13.sp)
                }
            }
            Button({ scope.launch { busy = true; error = null; runCatching { login(email.trim(), code.trim()) }.onFailure { error = it.message }; busy = false } }, Modifier.fillMaxWidth().height(54.dp), enabled = email.contains("@") && code.isNotBlank() && !busy, shape = RoundedCornerShape(18.dp)) { Text(if (busy) "请稍候…" else "登录", fontSize = 17.sp) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            Text("未注册的邮箱验证后将自动创建账户", Modifier.align(Alignment.CenterHorizontally), color = Color.Gray, fontSize = 13.sp)
        }
    }
}

@Composable
private fun MainScreen(tab: Int, setTab: (Int) -> Unit, shipments: List<Shipment>, account: Account?, summary:AccountSummary?, pickupMobile:String, loading: Boolean, openDetail: (Shipment) -> Unit, openPage: (Screen) -> Unit, logout:()->Unit, deleteShipment:(Long)->Unit, refreshAll:()->Unit, addShipment: (String,String,String) -> Unit) {
    var addOpen by remember { mutableStateOf(false) }
    val nav = listOf(NavigationItem("快递", Icons.Rounded.Inventory2), NavigationItem("我的", Icons.Rounded.Person))
    MiuixScaffold(
        topBar = { MiuixTopAppBar(title = if (tab == 0) "快递查询" else "我的", largeTitle = if (tab == 0) "全部快递" else "账户") },
        bottomBar = { NavigationBar(items = nav, selected = tab, onClick = setTab) },
        floatingActionButton = { if (tab == 0) top.yukonga.miuix.kmp.basic.FloatingActionButton(onClick = { addOpen = true }) { top.yukonga.miuix.kmp.basic.Icon(Icons.Rounded.Add, null, tint = Color.White) } }
    ) { padding -> if (tab == 0) ShipmentList(padding, shipments, loading, openDetail,deleteShipment,refreshAll) else AccountPage(padding, account,summary,pickupMobile, openPage, logout) }
    if (addOpen) AddShipmentDialog({ addOpen = false }) { number,name,phone -> addShipment(number,name,phone); addOpen=false }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun ShipmentList(padding: PaddingValues, shipments: List<Shipment>, loading: Boolean, openDetail: (Shipment) -> Unit,deleteShipment:(Long)->Unit,refreshAll:()->Unit) {
    var deleting by remember{mutableStateOf<Shipment?>(null)}
    LazyColumn(Modifier.fillMaxSize().background(Page).padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { AssistChip({}, { Text("全部 ${shipments.size}") }); TextButton(refreshAll) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(4.dp)); Text("全部刷新") } } }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (!loading && shipments.isEmpty()) item { Box(Modifier.fillParentMaxHeight(.65f).fillMaxWidth(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Rounded.Inventory2,null,tint=Color.LightGray,modifier=Modifier.size(64.dp));Spacer(Modifier.height(12.dp));Text("还没有快递",color=Color.Gray);Text("点击右下角添加运单",color=Color.LightGray,fontSize=13.sp)}} }
        items(shipments) { shipment ->
            Card(Modifier.fillMaxWidth().combinedClickable(onClick={openDetail(shipment)},onLongClick={deleting=shipment}), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = CardColor)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CarrierLogo(shipment.carrier,shipment.company)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) { Text(shipment.name.ifBlank { shipment.company }, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text("${shipment.company}  ${shipment.number}", color = Color.Gray, fontSize = 12.sp) }
                        StatusPill(shipment.statusText, shipment.status)
                    }
                    HorizontalDivider()
                    Text(shipment.events.firstOrNull()?.content ?: "暂无物流信息", maxLines = 2)
                    if(shipment.pickupCode.isNotBlank()) Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.Key,null,tint=Blue,modifier=Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text("取件码 ${shipment.pickupCode}",color=Blue,fontWeight=FontWeight.Bold)}
                    Text(shipment.updatedAt, color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
    deleting?.let{s->AlertDialog(onDismissRequest={deleting=null},title={Text("删除运单")},text={Text("确定删除“${s.company} ${s.number}”吗？删除后，该运单及其云端物流轨迹将无法恢复。")},confirmButton={TextButton({deleteShipment(s.id);deleting=null}){Text("删除",color=Color(0xFFE5484D))}},dismissButton={TextButton({deleting=null}){Text("取消")}})}
}

@Composable
private fun CarrierLogo(carrier: String, company:String="") {
    val key="$carrier $company".lowercase()
    val resource = when { key.contains("顺丰")||key.contains("sf") -> R.raw.logo_sf; key.contains("圆通")||key.contains("yto") -> R.raw.logo_yto; key.contains("中通")||key.contains("zto") -> R.raw.logo_zto; key.contains("韵达")||key.contains("yunda")||key.contains("yd") -> R.raw.logo_yunda; else -> null }
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color.White), contentAlignment = Alignment.Center) {
        if (resource != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(resource).decoderFactory(SvgDecoder.Factory()).build(),
                contentDescription = "快递公司 Logo",
                modifier = Modifier.fillMaxSize().padding(5.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Icon(Icons.Rounded.LocalShipping, null, tint = Blue, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun StatusPill(text: String, status: Int) { val color = when (status) { 3 -> Color(0xFF2E9B61); 5 -> Blue; else -> Color(0xFFF28C28) }; Text(text, color = color, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(.1f)).padding(horizontal = 10.dp, vertical = 5.dp)) }

@Composable
private fun DetailScreen(shipment: Shipment, updateNote:(String)->Unit, queryPickup:((()->Unit))->Unit, back: () -> Unit) {
    val context=LocalContext.current
    var note by remember { mutableStateOf(if (shipment.name.isBlank()) "" else shipment.name) }
    var editNote by remember { mutableStateOf(false) }
    var sharePreview by remember { mutableStateOf(false) }
    var queryingPickup by remember { mutableStateOf(false) }
    PageScaffold("快件详情", back, actions = { IconButton({ sharePreview = true }) { Icon(Icons.Rounded.Share, "分享") } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().background(Page).padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(CardColor)) { Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { CarrierLogo(shipment.carrier,shipment.company); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(shipment.company, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(shipment.number, color = Color.Gray) }; IconButton({ (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("运单号",shipment.number));Toast.makeText(context,"运单号已复制",Toast.LENGTH_SHORT).show() }) { Icon(Icons.Rounded.ContentCopy, "复制运单号") } }
                StatusPill(shipment.statusText, shipment.status)
                Text(shipment.events.firstOrNull()?.content ?: "暂无物流信息", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            } } }
            item { Card(shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(CardColor)){Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.Key,null,tint=Blue);Spacer(Modifier.width(10.dp));Text("取件信息",fontWeight=FontWeight.Bold,fontSize=18.sp)};if(shipment.pickupCode.isNotBlank()){Text(shipment.pickupCode,fontSize=32.sp,fontWeight=FontWeight.Bold,color=Blue);if(shipment.pickupStation.isNotBlank())Text(shipment.pickupStation,fontWeight=FontWeight.Medium);if(shipment.pickupAddress.isNotBlank())Text(shipment.pickupAddress,color=Color.Gray)}else{Text("包裹入站并生成取件码后将在这里显示",color=Color.Gray);OutlinedButton({ queryingPickup=true;queryPickup{queryingPickup=false} },Modifier.fillMaxWidth(),enabled=!queryingPickup,shape=RoundedCornerShape(16.dp)){Icon(Icons.Rounded.Refresh,null);Spacer(Modifier.width(6.dp));Text(if(queryingPickup) "正在查询…" else "查询取件码")}}}} }
            item { Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(CardColor)) { Row(Modifier.fillMaxWidth().clickable { editNote = true }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.EditNote, null, tint = Blue); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text("快件备注", fontWeight = FontWeight.Bold); Text(note.ifBlank { "添加备注，方便识别快件" }, color = Color.Gray) }; Icon(Icons.Rounded.ChevronRight, null, tint = Color.LightGray) } } }
            item { Text("物流轨迹", fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 8.dp)) }
            items(shipment.events) { event -> TimelineRow(event) }
        }
    }
    if (editNote) EditNoteDialog(note, { editNote = false }) { note = it; updateNote(it); editNote = false }
    if (sharePreview) SharePreviewDialog(shipment, note) { sharePreview = false }
}

@Composable
private fun TimelineRow(event: TrackingEvent) { Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(CardColor)) { Row(Modifier.fillMaxWidth().padding(16.dp)) { Box(Modifier.padding(top = 5.dp).size(10.dp).clip(CircleShape).background(Blue)); Spacer(Modifier.width(12.dp)); Column { Text(event.content); Spacer(Modifier.height(5.dp)); Text(event.time, color = Color.Gray, fontSize = 12.sp) } } } }

@Composable
private fun SharePreviewDialog(shipment: Shipment, note: String, close: () -> Unit) { val ctx=LocalContext.current;AlertDialog(onDismissRequest = close, title = { Text("分享长图预览") }, text = { Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(CardColor)) { Column(Modifier.heightIn(max=560.dp).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { CarrierLogo(shipment.carrier); Spacer(Modifier.width(10.dp)); Column { Text(note.ifBlank { shipment.company }, fontWeight = FontWeight.Bold); Text(shipment.number, fontSize = 12.sp, color = Color.Gray) } }; StatusPill(shipment.statusText, shipment.status); HorizontalDivider(); shipment.events.forEach { Text("• ${it.content}\n  ${it.time}", fontSize = 12.sp) }; Text("由快递查询生成", color = Color.Gray, fontSize = 11.sp) } } }, confirmButton = { Button({ShareHelper.share(ctx,shipment,note);close()}) { Text("调用系统分享") } }, dismissButton = { TextButton(close) { Text("取消") } }) }

@Composable
private fun AccountPage(padding: PaddingValues, account: Account?,summary:AccountSummary?,pickupMobile:String, open: (Screen) -> Unit, logout:()->Unit) { LazyColumn(Modifier.fillMaxSize().background(Page).padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    item { Card(Modifier.clickable { open(Screen.Profile) }, shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(CardColor)) { Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) { AccountAvatar(account,68); Spacer(Modifier.width(16.dp)); Column(Modifier.weight(1f)) { Text(account?.nickname?.ifBlank{"离线用户"}?:"离线用户", fontSize = 21.sp, fontWeight = FontWeight.Bold); Text("本地离线模式", color = Color.Gray); Text("所有数据仅保存在当前设备", fontSize = 11.sp, color = Color.Gray) }; Icon(Icons.Rounded.ChevronRight, null, tint = Color.Gray) } } }
    item { SettingsCard { SettingRow(Icons.Rounded.Edit, "修改昵称", "当前：${account?.nickname.orEmpty()}") { open(Screen.Nickname) };SettingRow(Icons.Rounded.PhoneAndroid,"收件手机号",if(pickupMobile.isBlank()) "用于查询取件码" else "当前：${pickupMobile.take(3)}****${pickupMobile.takeLast(4)}"){open(Screen.PickupMobile)};SettingRow(Icons.Rounded.DarkMode,"显示模式","浅色或深色模式"){open(Screen.Theme)} } }
    item { SettingsCard { InfoRow("本地运单", "${summary?.shipments?:0} 个");InfoRow("本地轨迹", "${summary?.events?:0} 条");SettingRow(Icons.Rounded.Info, "关于快递查询", "版本 ${BuildConfig.VERSION_NAME}") { open(Screen.About) } } }
} }

@Composable private fun ProfileScreen(account:Account?, upload:(Uri)->Unit, back: () -> Unit) { PageScaffold("个人资料", back) { p -> Column(Modifier.fillMaxSize().background(Page).padding(p).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) { Spacer(Modifier.height(16.dp)); AccountAvatar(account,112); SettingsCard { InfoRow("昵称", account?.nickname?:""); InfoRow("存储方式", "本机存储");InfoRow("数据位置", "当前设备") };Text("卸载应用或清除应用数据会删除所有本地运单。",color=Color.Gray,fontSize=12.sp) } } }

@Composable private fun NicknameScreen(initial:String, save:(String)->Unit, back: () -> Unit) { var value by remember { mutableStateOf(initial) }; PageScaffold("修改昵称", back) { p -> Column(Modifier.fillMaxSize().background(Page).padding(p).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedTextField(value, { value = it.take(20) }, Modifier.fillMaxWidth(), label = { Text("昵称") }, supportingText = { Text("${value.length}/20") }, shape = RoundedCornerShape(18.dp)); Button({save(value.trim())}, Modifier.fillMaxWidth().height(52.dp),enabled=value.isNotBlank(), shape = RoundedCornerShape(18.dp)) { Text("保存") } } } }

@Composable private fun PickupMobileScreen(initial:String,save:(String)->Unit,back:()->Unit){var value by remember{mutableStateOf(initial)};PageScaffold("收件手机号",back){p->Column(Modifier.fillMaxSize().background(Page).padding(p).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){SoftTextField(value,{value=it.filter(Char::isDigit).take(11)},"收件人手机号",Modifier.fillMaxWidth(),keyboardType=KeyboardType.Phone,icon=Icons.Rounded.PhoneAndroid);Text("手机号仅保存在本机，查询取件码时临时发送至服务器和快递鸟。",color=Color.Gray,fontSize=12.sp);Button({save(value)},Modifier.fillMaxWidth().height(52.dp),enabled=value.length==11&&value.startsWith("1"),shape=RoundedCornerShape(18.dp)){Text("保存")}}}}

@Composable private fun BackupScreen(api:Api,initial:AccountSummary?,synced:()->Unit,back: () -> Unit) { var s by remember{mutableStateOf(initial)};var busy by remember{mutableStateOf(false)};val scope=rememberCoroutineScope();PageScaffold("云端备份", back) { p -> LazyColumn(Modifier.fillMaxSize().background(Page).padding(p), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { HeroCard(Icons.Rounded.CloudDone, "备份已完成", "最后同步：${s?.lastSync?.ifBlank{"暂无"}?:"暂无"}") }; item { SettingsCard { InfoRow("已备份运单", "${s?.shipments?:0} 个"); InfoRow("物流轨迹", "${s?.events?:0} 条"); InfoRow("备注与偏好", if(s?.preferencesSynced==true)"已同步" else "未同步") } }; item { Button({scope.launch{busy=true;runCatching{api.summary()}.onSuccess{s=it;synced()};busy=false}}, Modifier.fillMaxWidth().height(52.dp),enabled=!busy, shape = RoundedCornerShape(18.dp)) { Icon(Icons.Rounded.Sync, null); Spacer(Modifier.width(8.dp)); Text(if(busy)"同步中…" else "立即同步") } }; item { Text("在新设备上使用相同邮箱登录，即可自动恢复全部快件信息。", color = Color.Gray, modifier = Modifier.padding(8.dp)) } } } }

@Composable private fun PushScreen(api:Api, back: () -> Unit) { var settings by remember { mutableStateOf(NotificationSettings()) }; var busy by remember { mutableStateOf(true) }; var message by remember { mutableStateOf("") }; val scope=rememberCoroutineScope(); LaunchedEffect(Unit){runCatching{api.notificationSettings()}.onSuccess{settings=it}.onFailure{message=it.message.orEmpty()};busy=false}; fun save(next:NotificationSettings){settings=next;scope.launch{busy=true;runCatching{api.saveNotificationSettings(next)}.onSuccess{settings=it;message="已保存"}.onFailure{message=it.message.orEmpty()};busy=false}}; PageScaffold("推送通知", back) { p -> LazyColumn(Modifier.fillMaxSize().background(Page).padding(p), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())}; item { SettingsCard { ToggleRow("允许推送通知", "关闭后不再接收物流提醒", settings.pushEnabled) { save(settings.copy(pushEnabled=it)) } } }; item { Text("通知类型", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp)); SettingsCard { ToggleRow("运输中", "物流节点发生变化", settings.notifyTransit) { save(settings.copy(notifyTransit=it)) }; ToggleRow("派送中", "快件开始派送", settings.notifyDelivery) { save(settings.copy(notifyDelivery=it)) }; ToggleRow("物流异常", "退回、延误或其他异常", settings.notifyException) { save(settings.copy(notifyException=it)) }; ToggleRow("已签收", "快件完成签收", settings.notifySigned) { save(settings.copy(notifySigned=it)) } } }; if(message.isNotBlank())item{Text(message,color=Color.Gray,fontSize=12.sp,modifier=Modifier.padding(8.dp))} } } }

@Composable
private fun MailScreen(api: Api, back: () -> Unit) {
    var s by remember { mutableStateOf(NotificationSettings()) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { api.notificationSettings() }.onSuccess { s = it }.onFailure { message = it.message.orEmpty() }; busy = false }
    fun save(after: suspend () -> Unit = {}) = scope.launch { busy = true; runCatching { s = api.saveNotificationSettings(s.copy(smtpPassword = password)); password = ""; after() }.onSuccess { message = "设置已保存" }.onFailure { message = it.message.orEmpty() }; busy = false }
    PageScaffold("邮件通知", back) { p ->
        LazyColumn(Modifier.fillMaxSize().background(Page).padding(p), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item { SettingsCard { ToggleRow("启用邮件通知", "物流更新时发送邮件", s.emailEnabled) { s = s.copy(emailEnabled = it) } } }
            item { Text("SMTP 设置", color=MaterialTheme.colorScheme.onBackground,fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp)); Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(CardColor)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftTextField(s.smtpHost, { s = s.copy(smtpHost = it) }, "SMTP 服务器", Modifier.fillMaxWidth(),icon=Icons.Rounded.Dns)
                SoftTextField(s.smtpPort.toString(), { s = s.copy(smtpPort = it.filter(Char::isDigit).toIntOrNull() ?: 0) }, "端口", Modifier.fillMaxWidth(),keyboardType=KeyboardType.Number,icon=Icons.Rounded.Numbers)
                SoftTextField(s.smtpUser, { s = s.copy(smtpUser = it) }, "邮箱账号", Modifier.fillMaxWidth(),keyboardType=KeyboardType.Email,icon=Icons.Rounded.AlternateEmail)
                SoftTextField(password, { password = it }, if (s.hasSmtpPassword) "新授权码（留空保持不变）" else "授权码/密码", Modifier.fillMaxWidth(),visualTransformation=PasswordVisualTransformation(),icon=Icons.Rounded.Key)
                SoftTextField(s.smtpFrom, { s = s.copy(smtpFrom = it) }, "发件人地址", Modifier.fillMaxWidth(),keyboardType=KeyboardType.Email,icon=Icons.Rounded.Send)
                SoftTextField(s.smtpTo, { s = s.copy(smtpTo = it) }, "收件人地址", Modifier.fillMaxWidth(),keyboardType=KeyboardType.Email,icon=Icons.Rounded.Inbox)
            } } }
            item { Text("邮件模板", color=MaterialTheme.colorScheme.onBackground,fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp)); Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(CardColor)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SoftTextField(s.mailSubject, { s = s.copy(mailSubject = it) }, "邮件标题", Modifier.fillMaxWidth(),icon=Icons.Rounded.Title)
                SoftTextField(s.mailBody, { s = s.copy(mailBody = it) }, "邮件正文", Modifier.fillMaxWidth(),singleLine=false,minLines=6,icon=Icons.Rounded.Description)
                Text("可用变量：{{name}} {{number}} {{company}} {{status}} {{latest}} {{updated_at}}", color = Color.Gray, fontSize = 11.sp)
            } } }
            if (message.isNotBlank()) item { Text(message, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(8.dp)) }
            item { OutlinedButton({ save { api.testEmail() }; message = "测试邮件已发送" }, Modifier.fillMaxWidth().height(50.dp), enabled = !busy, shape = RoundedCornerShape(18.dp)) { Text("保存并发送测试邮件") } }
            item { Button({ save() }, Modifier.fillMaxWidth().height(52.dp), enabled = !busy, shape = RoundedCornerShape(18.dp)) { Text("保存设置") } }
        }
    }
}

@Composable private fun SecurityScreen(api:Api,account:Account?,summary:AccountSummary?,devices:()->Unit,delete:()->Unit,logoutOthers:()->Unit,back: () -> Unit) { var confirm by remember{mutableStateOf(false)};PageScaffold("账户与安全", back) { p -> LazyColumn(Modifier.fillMaxSize().background(Page).padding(p), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { item { SettingsCard { InfoRow("登录邮箱", account?.email.orEmpty()); SettingRow(Icons.Rounded.Devices, "登录设备", "当前 ${summary?.devices?:0} 台设备",devices); SettingRow(Icons.Rounded.Logout, "退出其他设备", "保留当前设备") {confirm=true} } }; item { OutlinedButton(delete, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(18.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE5484D))) { Text("注销账户") } }; item { Text("注销后，账户、运单和云端备份数据将被永久删除。", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(8.dp)) } } };if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("退出其他设备")},text={Text("操作完成后，除当前设备外，其他设备上的账户将退出登录，并停止接收该账户的推送通知。")},confirmButton={TextButton({logoutOthers();confirm=false}){Text("确认退出")}},dismissButton={TextButton({confirm=false}){Text("取消")}}) }

@Composable private fun DevicesScreen(api:Api,back:()->Unit){
    var list by remember{mutableStateOf<List<LoginDevice>>(emptyList())};var remove by remember{mutableStateOf<LoginDevice?>(null)};val scope=rememberCoroutineScope()
    fun load() { scope.launch { runCatching { api.devices() }.onSuccess { list=it } } }
    LaunchedEffect(Unit){load()}
    PageScaffold("登录设备",back){p->LazyColumn(Modifier.fillMaxSize().background(Page).padding(p),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){items(list){d->
        Card(colors=CardDefaults.cardColors(CardColor),shape=RoundedCornerShape(20.dp)){Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Rounded.PhoneAndroid,null,tint=Blue);Spacer(Modifier.width(10.dp));Text(d.name,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));if(d.current)AssistChip({}, {Text("当前设备")})}
            Text("${d.androidVersion} · App ${d.appVersion}",color=Color.Gray);Text("首次登录：${d.firstLogin}",fontSize=12.sp,color=Color.Gray);Text("最近活跃：${d.lastActive}",fontSize=12.sp,color=Color.Gray)
            Text(if(d.pushEnabled) "推送状态：可用" else "推送状态：未注册",fontSize=12.sp,color=Color.Gray);if(!d.current)TextButton({remove=d}){Text("退出此设备",color=Color(0xFFE5484D))}
        }}
    }}}
    remove?.let{d->AlertDialog(onDismissRequest={remove=null},title={Text("退出设备")},text={Text("确定退出 ${d.name} 吗？")},confirmButton={TextButton({scope.launch{runCatching{api.removeDevice(d.id)}.onSuccess{load()}};remove=null}){Text("退出")}},dismissButton={TextButton({remove=null}){Text("取消")}})}
}

@Composable private fun DeleteAccountScreen(api:Api,done:()->Unit,back:()->Unit){var code by remember{mutableStateOf("")};var sent by remember{mutableStateOf(false)};var msg by remember{mutableStateOf("")};val scope=rememberCoroutineScope();PageScaffold("注销账户",back){p->Column(Modifier.fillMaxSize().background(Page).padding(p).padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Text("注销后账户、运单、物流轨迹、设置和登录设备将永久删除且无法恢复。",color=Color(0xFFE5484D));Button({scope.launch{runCatching{api.requestDeleteCode()}.onSuccess{sent=true;msg="验证码已发送至登录邮箱"}.onFailure{msg=it.message.orEmpty()}}},Modifier.fillMaxWidth()){Text("发送注销验证码")};if(sent)OutlinedTextField(code,{code=it.filter(Char::isDigit).take(6)},label={Text("验证码")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth());if(sent)Button({scope.launch{runCatching{api.deleteAccount(code)}.onSuccess{done()}.onFailure{msg=it.message.orEmpty()}}},Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFE5484D)),enabled=code.length==6){Text("确认永久注销")};Text(msg,color=Color.Gray)}}}

@Composable private fun ThemeScreen(dark:Boolean,change:(Boolean)->Unit,back:()->Unit){PageScaffold("显示模式",back){p->Column(Modifier.fillMaxSize().background(Page).padding(p).padding(16.dp)){SettingsCard{ThemeChoiceRow(Icons.Rounded.LightMode,"浅色模式",!dark){change(false)};ThemeChoiceRow(Icons.Rounded.DarkMode,"深色模式",dark){change(true)}}}}}

@Composable private fun AboutScreen(privacy:()->Unit,agreement:()->Unit,back: () -> Unit) { PageScaffold("关于快递查询", back) { p -> LazyColumn(Modifier.fillMaxSize().background(Page).padding(p), contentPadding = PaddingValues(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) { item { androidx.compose.foundation.Image(painterResource(R.drawable.app_icon), null, Modifier.size(110.dp).clip(RoundedCornerShape(28.dp)), contentScale = ContentScale.Crop); Text("快递查询", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center); Text("版本 ${BuildConfig.VERSION_NAME}", color = Color.Gray, textAlign = TextAlign.Center) }; item { SettingsCard { InfoRow("存储方式","仅保存在本机");InfoRow("物流查询","ALAPI");InfoRow("取件码查询","快递鸟"); SettingRow(Icons.Rounded.PrivacyTip, "隐私说明", "查看本地数据说明",privacy) } }; item { Text("© 2026 快递查询", color = Color.Gray, fontSize = 12.sp) } } } }

@Composable private fun UpdateDialog(close:()->Unit,disable:()->Unit){val ctx=LocalContext.current;ModernDialog("发现新版本 ${UpdateHelper.VERSION}","更新将在应用内下载，完成后会引导您安装。",close,{UpdateHelper.download(ctx);close()},"立即更新","暂不更新",disable)}

@Composable private fun LegalScreen(title:String,text:String,back:()->Unit){PageScaffold(title,back){p->Column(Modifier.fillMaxSize().background(Page).padding(p).verticalScroll(rememberScrollState()).padding(22.dp)){Text(text,lineHeight=25.sp);Spacer(Modifier.height(30.dp))}}}

private val privacyText="""隐私政策

更新日期：2026年8月30日

本版本不提供登录和云端同步。运单、备注、轨迹及偏好仅保存在当前设备。

一、我们处理的信息
1. 运单信息：包括运单号、物流轨迹、备注及查询所需的手机号。
2. 昵称、显示模式等本地偏好。

二、用途与共享
物流查询时，运单号及必要的手机号会直接发送给 ALAPI；取件码查询时会直接发送给快递鸟。本版本不经过猫普拉科技服务器。

三、保存与保护
数据保存在应用本地存储中。卸载应用或清除应用数据会永久删除本地数据。

四、您的权利
您可在应用内查看或修改昵称、通知设置、登录设备，退出其他设备或注销账户。您也可在系统设置中关闭通知权限。

五、未成年人
未成年人应在监护人指导和同意下使用本服务。

六、联系我们
服务提供者：请自行填写
邮箱：请自行填写
网站：请自行填写
地址：请自行填写
"""

private val agreementText="""用户协议

更新日期：2026年8月30日

欢迎使用快递查询。使用本应用即表示您理解并同意本协议。

一、服务内容
本应用提供运单查询、云端同步、物流提醒、设备管理等辅助功能。物流状态来自第三方接口，仅供参考，实际状态以快递承运商公布的信息为准。

二、账户使用
您应使用本人可正常接收邮件的地址登录并妥善保管验证码。不得利用本服务查询无权访问的运单、干扰系统、批量滥用接口或从事违法活动。

三、服务限制
受承运商接口、网络、系统维护及不可抗力影响，查询或通知可能出现延迟、中断或不准确。通知仅为便捷提醒，不构成送达承诺。

四、用户内容
您对录入的运单、备注、头像等内容负责，并保证有权处理这些信息。请勿填写违法、侵权或不必要的敏感信息。

五、更新与终止
我们可能为安全、兼容性或功能改进更新应用。您可随时停止使用并注销账户；严重违反协议时，我们可限制或终止相关服务。

六、责任范围
在法律允许范围内，我们不对因第三方数据错误、网络延迟、设备设置或用户操作造成的间接损失承担责任，但不会排除依法不得排除的责任。

七、联系我们
服务提供者：请自行填写
邮箱：请自行填写
网站：请自行填写
地址：请自行填写
"""

@Composable private fun PageScaffold(title: String, back: () -> Unit, actions: @Composable RowScope.() -> Unit = {}, content: @Composable (PaddingValues) -> Unit) { val foreground=MaterialTheme.colorScheme.onBackground;MiuixScaffold(topBar = { MiuixTopAppBar(title = title, navigationIcon = { IconButton(back) { Icon(Icons.Rounded.ArrowBack, "返回",tint=foreground) } }, actions = { androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides foreground) { actions() } }) }, content = content) }
@Composable private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) { Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(CardColor)) { Column(Modifier.fillMaxWidth(), content = content) } }
@Composable private fun SettingRow(icon: ImageVector, title: String, subtitle: String, click: () -> Unit) { Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Blue); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(subtitle, color = Color.Gray, fontSize = 12.sp) }; Icon(Icons.Rounded.ChevronRight, null, tint = Color.LightGray) } }
@Composable private fun InfoRow(title: String, value: String) { Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(title); Text(value, color = Color.Gray) } }
@Composable private fun ToggleRow(title: String, subtitle: String, checked: Boolean, change: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().clickable{change(!checked)}.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Medium); Text(subtitle, color = Color.Gray, fontSize = 12.sp) }; Switch(checked, change, colors=SwitchDefaults.colors(checkedThumbColor=Color.White,checkedTrackColor=Blue,uncheckedThumbColor=Color.White,uncheckedTrackColor=Color.Gray.copy(.35f))) } }
@Composable private fun ThemeChoiceRow(icon:ImageVector,title:String,selected:Boolean,click:()->Unit){Row(Modifier.fillMaxWidth().clickable(onClick=click).padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Blue);Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(title,fontWeight=FontWeight.Medium);if(selected)Text("当前使用",color=Color.Gray,fontSize=12.sp)};RadioButton(selected,click,colors=RadioButtonDefaults.colors(selectedColor=Blue))}}
@Composable private fun HeroCard(icon: ImageVector, title: String, subtitle: String) { Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(CardColor)) { Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) { Box(Modifier.size(64.dp).clip(CircleShape).background(Blue.copy(.12f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Blue, modifier = Modifier.size(36.dp)) }; Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = Color.Gray) } } }
@Composable private fun DefaultAvatar(size: Int) { Box(Modifier.size(size.dp).clip(CircleShape).background(Blue.copy(.13f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Person, "默认头像", tint = Blue, modifier = Modifier.size((size * .56f).dp)) } }
@Composable private fun AccountAvatar(account:Account?,size:Int){if(account?.avatarUrl.isNullOrBlank())DefaultAvatar(size) else AsyncImage(model=AppConfig.SERVER_URL+account!!.avatarUrl,contentDescription="用户头像",modifier=Modifier.size(size.dp).clip(CircleShape),contentScale=ContentScale.Crop)}
@Composable private fun SoftTextField(value:String,onValueChange:(String)->Unit,label:String,modifier:Modifier=Modifier,singleLine:Boolean=true,minLines:Int=1,keyboardType:KeyboardType=KeyboardType.Text,visualTransformation:androidx.compose.ui.text.input.VisualTransformation=androidx.compose.ui.text.input.VisualTransformation.None,icon:ImageVector?=null){TextField(value,onValueChange,modifier,label={Text(label)},singleLine=singleLine,minLines=minLines,keyboardOptions=KeyboardOptions(keyboardType=keyboardType),visualTransformation=visualTransformation,leadingIcon=icon?.let{{Icon(it,null,tint=Blue)}},shape=RoundedCornerShape(16.dp),colors=TextFieldDefaults.colors(focusedContainerColor=MaterialTheme.colorScheme.surfaceVariant.copy(.72f),unfocusedContainerColor=MaterialTheme.colorScheme.surfaceVariant.copy(.55f),focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,disabledIndicatorColor=Color.Transparent))}

@Composable private fun ModernDialog(title:String,subtitle:String="",close:()->Unit,confirm:()->Unit,confirmText:String="确定",dismissText:String="取消",dismiss:()->Unit=close,confirmEnabled:Boolean=true,content:@Composable ColumnScope.()->Unit={}){Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)){Surface(Modifier.fillMaxWidth().padding(horizontal=24.dp),shape=RoundedCornerShape(30.dp),color=CardColor,tonalElevation=8.dp){Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){Text(title,fontSize=24.sp,fontWeight=FontWeight.Bold);if(subtitle.isNotBlank())Text(subtitle,color=Color.Gray,fontSize=13.sp);content();Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp,Alignment.End),verticalAlignment=Alignment.CenterVertically){TextButton(dismiss){Text(dismissText)};Button(confirm,enabled=confirmEnabled,shape=RoundedCornerShape(16.dp)){Text(confirmText)}}}}}}

@Composable private fun EditNoteDialog(initial: String, close: () -> Unit, save: (String) -> Unit) { var value by remember { mutableStateOf(initial) };ModernDialog("快件备注","添加便于识别快件的名称",close,{save(value)},"保存",content={SoftTextField(value,{value=it.take(30)},"备注名称",Modifier.fillMaxWidth(),icon=Icons.Rounded.Edit);Text("${value.length}/30",Modifier.align(Alignment.End),color=Color.Gray,fontSize=12.sp)})}
@Composable private fun AddShipmentDialog(close: () -> Unit, save:(String,String,String)->Unit) { var number by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") };ModernDialog("添加快递","填写运单号后将自动识别快递公司",close,{save(number.trim(),name.trim(),phone.trim())},"保存并查询",confirmEnabled=number.isNotBlank(),content={SoftTextField(number,{number=it},"快递单号",Modifier.fillMaxWidth(),icon=Icons.Rounded.Inventory2);SoftTextField(name,{name=it.take(30)},"备注名称（可选）",Modifier.fillMaxWidth(),icon=Icons.Rounded.Edit);SoftTextField(phone,{phone=it.filter(Char::isDigit).take(4)},"顺丰手机号后四位（可选）",Modifier.fillMaxWidth(),keyboardType=KeyboardType.Number,icon=Icons.Rounded.PhoneAndroid)})}
