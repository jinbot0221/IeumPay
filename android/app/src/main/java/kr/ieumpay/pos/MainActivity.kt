package kr.ieumpay.pos

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.Context
import android.net.ConnectivityManager
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID
import java.util.concurrent.Executors
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {
    private val green = Color.rgb(32,89,61)
    private val muted = Color.rgb(117,133,122)
    private val pool = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("demo", MODE_PRIVATE) }
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private var state: JSONObject? = null
    private var screen = "home"
    private var amount = 15000L
    private var busy = false
    private var current: JSONObject? = null
    private var loggedIn = false
    private var cancelTarget: JSONObject? = null
    private var reason = ""
    private val products = linkedMapOf("아메리카노" to 4500, "카페라떼" to 5000, "바닐라 라떼" to 5500, "크루아상" to 4000)
    private val cart = mutableMapOf<String,Int>()
    private fun won(n: Long) = NumberFormat.getIntegerInstance(java.util.Locale.KOREA).format(n)
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screen = if (prefs.getBoolean("registered",false)) "login" else "register"
        if(savedInstanceState!=null){
            loggedIn=savedInstanceState.getBoolean("demoLoggedIn",false)
            screen=savedInstanceState.getString("screen",screen)
            amount=savedInstanceState.getLong("amount",15000L)
            savedInstanceState.getString("state")?.let{state=JSONObject(it)}
            savedInstanceState.getString("current")?.let{current=JSONObject(it)}
            savedInstanceState.getString("cancelTarget")?.let{cancelTarget=JSONObject(it)}
            savedInstanceState.getString("cart")?.let{val json=JSONObject(it);json.keys().forEach{k->cart[k]=json.getInt(k)}}
            if(loggedIn&&prefs.getString("pending",null)!=null)screen="pending"
        }
        draw()
    }
    override fun onSaveInstanceState(out:Bundle){
        out.putBoolean("demoLoggedIn",loggedIn);out.putString("screen",screen);out.putLong("amount",amount)
        state?.let{out.putString("state",it.toString())};current?.let{out.putString("current",it.toString())}
        cancelTarget?.let{out.putString("cancelTarget",it.toString())};out.putString("cart",JSONObject(cart as Map<*,*>).toString())
        super.onSaveInstanceState(out)
    }
    private fun panel(parent: LinearLayout): LinearLayout {
        val box = LinearLayout(this); box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(22),dp(20),dp(22),dp(20))
        box.background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius=dp(14).toFloat(); setStroke(dp(1),Color.rgb(225,233,226)) }
        parent.addView(box,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(16) }); return box
    }
    private fun text(parent: LinearLayout, value:String, size:Float=16f, color:Int=Color.rgb(39,57,45),bold:Boolean=false):TextView {
        val v=TextView(this);v.text=value;v.textSize=size;v.setTextColor(color);v.setPadding(0,dp(5),0,dp(8));if(bold)v.setTypeface(null,Typeface.BOLD); parent.addView(v);return v
    }
    private fun button(parent:LinearLayout,title:String,enabled:Boolean=true,action:()->Unit):Button {
        val b=Button(this); b.text=title;b.isAllCaps=false;b.textSize=15f;b.setTextColor(green);b.isEnabled=enabled&&!busy
        parent.addView(b,LinearLayout.LayoutParams(-1,dp(56)).apply { topMargin=dp(7) });b.setOnClickListener { action() }; return b
    }
    private fun input(parent:LinearLayout,hint:String,value:String="",numeric:Boolean=false):EditText {
        val v=EditText(this);v.hint=hint;v.setText(value);v.setSingleLine();if(numeric)v.inputType=android.text.InputType.TYPE_CLASS_NUMBER
        parent.addView(v,LinearLayout.LayoutParams(-1,dp(58)));return v
    }
    private fun go(s:String) { if(!busy) {screen=s;draw()} }
    private fun draw() {
        root=LinearLayout(this);root.orientation=LinearLayout.VERTICAL;root.setPadding(dp(20),dp(26),dp(20),dp(12));root.setBackgroundColor(Color.rgb(246,248,245));setContentView(root)
        text(root,"이음 pay     /     성수점 테스트 POS",22f,green,true)
        text(root,"개발 데모 · 실제 청구·충전·정산은 발생하지 않습니다",12f,Color.rgb(145,107,41))
        if(loggedIn){
            val nav=LinearLayout(this);root.addView(nav)
            listOf("home" to "홈","order" to "새 결제","history" to "거래 내역","device" to "장비 상태").forEach { (s,label)->
                val b=Button(this);b.text=label;b.isAllCaps=false;b.isEnabled=!busy&&prefs.getString("pending",null)==null;nav.addView(b,LinearLayout.LayoutParams(0,dp(52),1f));b.setOnClickListener{go(s)}
            }
        }
        val scroll=ScrollView(this);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f));body=LinearLayout(this);body.orientation=LinearLayout.VERTICAL;body.setPadding(0,dp(16),0,0);scroll.addView(body)
        if(busy)text(body,"서버에서 확인 중입니다…",16f,green)
        when(screen){"register"->register();"login"->login();"home"->home();"order"->order();"method"->methods();"pending"->pending();"result"->result();"history"->history();"cancel"->cancel();"device"->device()}
    }
    private fun register(){val p=panel(body);text(p,"T01 · 단말 등록",26f,green,true);text(p,"개발 서버 주소를 설정하고 데모 매장에 연결합니다. 실제 단말 자격 발급이 아닙니다.",14f,muted)
        val endpoint=input(p,"서버 주소",prefs.getString("endpoint","http://10.0.2.2:3000")!!)
        text(p,"에뮬레이터: 10.0.2.2 / 실기기: adb reverse 후 127.0.0.1",12f,muted)
        button(p,"테스트 서버 확인 및 데모 등록") {
            val url=endpoint.text.toString().trim().trimEnd('/')
            try{val uri=URI(url);require(uri.scheme in listOf("http","https")&&!uri.host.isNullOrBlank()&&uri.userInfo==null&&uri.query==null&&uri.fragment==null&&(uri.path==""||uri.path=="/"))}catch(e:Exception){alert("올바른 HTTP(S) 서버 주소를 입력하세요.");return@button}
            prefs.edit().putString("endpoint",url).apply()
            work({request("/api/health")},{ data->require(data.optBoolean("ok")&&data.optString("mode")=="demo");prefs.edit().putBoolean("registered",true).apply();go("login")})
        }
    }
    private fun login(){val p=panel(body);text(p,"T02 · 직원 진입",26f,green,true);text(p,"성수점 · 데모 직원",18f);text(p,"인증 서버 미연동: 비밀번호를 입력·저장하지 않습니다. 아래 버튼은 데모 진입이며 실제 로그인이 아닙니다.",14f,muted)
        button(p,"데모 직원으로 시작") { loggedIn=true;if(prefs.getString("pending",null)!=null){go("pending")}else load("home") }
        if(prefs.getString("pending",null)==null)button(p,"서버 연결 설정") {go("register")}
    }
    private fun load(next:String){work({request("/api/state")},{state=it;screen=next;draw()})}
    private fun home(){text(body,"오늘도, 매끄러운 결제",28f,green,true);text(body,"서버 저장 기준 테스트 거래 현황",13f,muted)
        val p=panel(body);val payments=state?.optJSONArray("payments");var gross=0L;var count=0
        if(payments!=null)for(i in 0 until payments.length()){val t=payments.getJSONObject(i);if(t.getString("status")=="approved"){gross+=t.getLong("amount");count++}}
        text(p,"전체 테스트 승인 금액",14f,muted);text(p,"${won(gross)} 원 · ${count}건",32f,green,true)
        state?.optJSONObject("wallet")?.let{ text(p,"테스트 지갑  ${won(it.getLong("paid")+it.getLong("benefit"))} C",18f) }
        button(p,"새 결제 시작 →"){cart.clear();go("order")};button(p,"현황 새로고침"){load("home")}
        val note=panel(body);text(note,"연동 준비 상태",18f,green,true);text(note,"코인: 서버 테스트 차감·복원 가능\n고객 QR 확인: 미구현\n카드 · Apple Pay · 삼성페이: 리더기 미연동\n카카오페이 · 네이버페이: 계약·모듈 미연동",14f,muted)
    }
    private fun order(){val p=panel(body);text(p,"T04 · 주문 만들기",26f,green,true)
        text(p,"상품 선택 (샘플 가격)",14f,muted)
        products.forEach{(name,price)->button(p,"$name   ${won(price.toLong())}원   × ${cart[name]?:0}"){cart[name]=(cart[name]?:0)+1;amount=cart.entries.sumOf{products[it.key]!!.toLong()*it.value};draw()}}
        val value=input(p,"직접 결제 금액 (원)",amount.toString(),true)
        text(p,"직접 입력 금액은 상품 주문과 별도입니다. 한 번에 한 방식만 선택하세요.",12f,muted)
        button(p,"직접 입력 금액으로 진행") {val n=value.text.toString().toLongOrNull();if(n==null||n !in 1..1000000){alert("1~1,000,000원의 정수 금액을 입력하세요.");return@button};amount=n;cart.clear();go("method")}
        button(p,"선택 상품 합계로 진행",cart.isNotEmpty()){amount=cart.entries.sumOf{products[it.key]!!.toLong()*it.value};if(amount !in 1..1000000)alert("결제 한도를 초과했습니다.") else go("method")}
        button(p,"주문 초기화"){AlertDialog.Builder(this).setMessage("주문을 초기화할까요?").setNegativeButton("유지",null).setPositiveButton("초기화"){_,_->cart.clear();amount=0;draw()}.show()}
    }
    private fun methods(){val p=panel(body);text(p,"T05 · 결제수단",26f,green,true);text(p,"${won(amount)} 원",36f,green,true)
        text(p,"코인 테스트는 고객 인증 없이 데모 지갑에서 차감합니다. 실서비스의 고객 동의를 대체하지 않습니다.",14f,muted)
        button(p,"이음 코인 · 테스트 승인 확인") {AlertDialog.Builder(this).setTitle("테스트 코인 결제").setMessage("데모 지갑에서 ${won(amount)} C를 차감할까요? 실제 고객 결제가 아닙니다.").setNegativeButton("돌아가기",null).setPositiveButton("테스트 승인"){_,_->startPayment()}.show()}
        listOf("카드 / Apple Pay / 삼성페이 · 리더기 연결 필요","카카오페이 · 미연동","네이버페이 · 미연동").forEach{button(p,it,false){}}
        button(p,"금액 수정"){go("order")}
    }
    private fun startPayment(){
        if(busy || prefs.getString("pending",null)!=null) return
        val payload=JSONObject().put("amount",amount).put("method","coin").put("requestKey",UUID.randomUUID().toString())
        if(!prefs.edit().putString("pending",payload.toString()).commit()){alert("복구 정보를 저장하지 못해 결제를 시작하지 않았습니다.");return}
        screen="pending";draw();retry()
    }
    private fun pending(){val p=panel(body);text(p,"T06 · 결제 결과 확인 필요",26f,green,true);text(p,"통신 오류는 결제 실패를 뜻하지 않습니다. 기존 요청 키로 결과를 복구합니다. 확인 전 새 결제를 시작하지 마세요.",16f,muted)
        button(p,"기존 결제 요청으로 확인",!busy){retry()}
    }
    private fun retry(){val raw=prefs.getString("pending",null)?:return;amount=JSONObject(raw).getLong("amount");work({request("/api/pay",JSONObject(raw))},{current=it;prefs.edit().remove("pending").commit();screen="result";draw()})}
    private fun result(){val p=panel(body);val t=current?:return;text(p,"T07 · 테스트 결제 완료",27f,green,true);text(p,"${won(t.getLong("amount"))} 원",38f,green,true);text(p,"이음 코인 · ${t.getString("status")}\n유료 ${won(t.getLong("paid"))} C / 혜택 ${won(t.getLong("benefit"))} C\n거래번호 ${t.getString("id")}\n${time(t.getString("created"))}",15f)
        text(p,"실제 영수증이 아닌 테스트 거래 명세입니다.",12f,muted);button(p,"거래 내역 보기"){load("history")};button(p,"새 결제"){amount=0;cart.clear();go("order")}
    }
    private fun history(){text(body,"T08 · 거래 내역",27f,green,true);button(body,"새로고침"){load("history")};val a=state?.optJSONArray("payments");if(a==null||a.length()==0)text(body,"아직 테스트 거래가 없습니다.")
        if(a!=null)for(i in 0 until a.length()){val t=a.getJSONObject(i);val p=panel(body);text(p,"${won(t.getLong("amount"))} 원 · ${if(t.getString("status")=="cancelled")"취소 완료" else "테스트 승인"}",20f,green,true);text(p,"${time(t.getString("created"))} · ${t.getString("method")}\n${t.getString("id")}",12f,muted)
            if(t.getString("status")=="approved"&&t.getString("method")=="coin")button(p,"전체 취소 확인"){cancelTarget=t;go("cancel")}
        }
    }
    private fun cancel(){val t=cancelTarget?:return;val p=panel(body);text(p,"T09 · 전체 취소",26f,green,true);text(p,"${won(t.getLong("amount"))} 원 · 원거래의 유료/혜택 코인을 복원합니다.",17f);val field=input(p,"취소 사유 (필수)",reason)
        button(p,"전체 취소 요청"){reason=field.text.toString().trim();if(reason.length !in 2..200){alert("취소 사유를 2~200자로 입력하세요.");return@button};work({request("/api/cancel",JSONObject().put("id",t.getString("id")).put("reason",reason))},{reason="";load("history")})}
        button(p,"거래 내역으로"){go("history")}
    }
    private fun device(){val p=panel(body);text(p,"T10 · 장비와 앱 상태",26f,green,true);val cm=getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        text(p,"기기: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\nAndroid ${android.os.Build.VERSION.RELEASE}\n앱: 0.2.0-demo\n네트워크: ${if(cm.activeNetwork!=null)"연결 감지 (서버 통신 별도 확인)" else "연결 없음"}\n리더기: 미연동\n단말 자격·원격 차단·업데이트: 미구현",16f)
        button(p,"서버 응답 확인"){work({request("/api/health")},{alert("테스트 서버 정상 응답")})};button(p,"데모 직원 나가기"){loggedIn=false;go("login")}
    }
    private fun time(s:String):String=try{DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss").withZone(ZoneId.of("Asia/Seoul")).format(Instant.parse(s))}catch(e:Exception){s}
    private fun alert(s:String){if(!isFinishing)AlertDialog.Builder(this).setMessage(s).setPositiveButton("확인",null).show()}
    private class Rejected(message:String):Exception(message)
    private fun work(task:()->JSONObject,done:(JSONObject)->Unit){if(busy)return;busy=true;draw();pool.execute{try{val result=task();runOnUiThread{if(!isDestroyed){busy=false;try{done(result)}catch(e:Exception){draw();alert(e.message?:"응답 확인 실패")}}}}catch(e:Exception){runOnUiThread{if(!isDestroyed){busy=false;if(e is Rejected && prefs.getString("pending",null)!=null){prefs.edit().remove("pending").commit();screen="method"};draw();alert(e.message?:"서버 연결을 확인하세요.")}}}}}
    private fun request(path:String,payload:JSONObject?=null):JSONObject{
        val c=URI(prefs.getString("endpoint","http://10.0.2.2:3000")+path).toURL().openConnection() as HttpURLConnection
        try{c.connectTimeout=7000;c.readTimeout=10000;c.setRequestProperty("Accept","application/json")
            if(payload!=null){c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(payload.toString().toByteArray(Charsets.UTF_8))}}
            val code=c.responseCode;val result=JSONObject((if(code in 200..299)c.inputStream else c.errorStream).bufferedReader().use{it.readText()})
            if(code==400)throw Rejected(result.optString("error","서버가 요청을 거절했습니다."));if(code !in 200..299)throw Exception("서버 오류 $code · 결과를 다시 확인하세요.");return result
        }finally{c.disconnect()}
    }
    @Deprecated("Deprecated in Java") override fun onBackPressed(){if(busy||prefs.getString("pending",null)!=null){alert("기존 결제의 결과를 먼저 확인하세요.");return};if(loggedIn)go("home")else super.onBackPressed()}
    override fun onDestroy(){pool.shutdown();super.onDestroy()}
}
