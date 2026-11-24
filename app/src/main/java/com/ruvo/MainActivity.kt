package com.ruvo

import android.os.Bundle
import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Offset
import androidx.compose.ui.graphics.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ruvo.data.*
import java.text.NumberFormat
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.security.MessageDigest

private val Forest = Color(0xFF173F32)
private val Sage = Color(0xFFA9C6A5)
private val Ivory = Color(0xFFF3F1E9)
private val Muted = Color(0xFF7C887F)

@Composable
private fun Modifier.glass(shape: RoundedCornerShape = RoundedCornerShape(22.dp)): Modifier {
    val surface = MaterialTheme.colorScheme.surface
    return this
        .shadow(12.dp, shape, ambientColor = Forest.copy(alpha = 0.10f), spotColor = Forest.copy(alpha = 0.12f))
        .clip(shape)
        .background(Brush.verticalGradient(listOf(surface.copy(alpha = 0.94f), surface.copy(alpha = 0.78f))))
        .border(1.dp, Color.White.copy(alpha = if (MaterialTheme.colorScheme.background == Ivory) 0.72f else 0.10f), shape)
}

private fun categoryGlyph(name: String?): ImageVector = when(name) {
    "restaurant" -> Icons.Default.Restaurant
    "directions_bus" -> Icons.Default.DirectionsBus
    "shopping_bag" -> Icons.Default.ShoppingBag
    "school" -> Icons.Default.School
    "coffee" -> Icons.Default.Coffee
    "receipt" -> Icons.Default.ReceiptLong
    "payments" -> Icons.Default.Payments
    else -> Icons.Default.Category
}

private fun pinDigest(pin:String)=MessageDigest.getInstance("SHA-256").digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }

class MainActivity : ComponentActivity() {
    private val vm: RuvoViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RuvoApp(vm, intent?.action == ACTION_QUICK_ADD) }
    }
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_QUICK_ADD) setContent { RuvoApp(vm, true) }
    }
    companion object { const val ACTION_QUICK_ADD = "com.ruvo.QUICK_ADD" }
}

private fun cash(paise: Long): String = NumberFormat.getCurrencyInstance(Locale("en","IN")).apply { maximumFractionDigits=2; minimumFractionDigits=if(paise % 100L == 0L) 0 else 2 }.format(paise/100.0)
private fun parsePaise(input:String):Long? = runCatching { val amount=BigDecimal(input.trim()); if(amount<=BigDecimal.ZERO || amount.scale()>2) null else amount.movePointRight(2).setScale(0,RoundingMode.HALF_UP).longValueExact() }.getOrNull()
private fun midnight(d: LocalDate) = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
private fun between(t:RuvoTransaction,a:LocalDate,b:LocalDate)=t.timestamp>=midnight(a) && t.timestamp<midnight(b.plusDays(1))
private fun spent(rows:List<RuvoTransaction>)=rows.filter{it.type=="EXPENSE"}.sumOf{it.amountPaise}

@Composable
fun RuvoApp(vm:RuvoViewModel, quick:Boolean) {
    val rows by vm.transactions.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    var page by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf(quick) }
    var editing by remember { mutableStateOf<RuvoTransaction?>(null) }
    var categoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<RuvoCategory?>(null) }
    var budgetDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("ruvo_preferences", 0) }
    var budget by remember { mutableStateOf(prefs.getString("monthly_budget", "15000") ?: "15000") }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
    var pinHash by remember { mutableStateOf(prefs.getString("app_pin_hash", null)) }
    var locked by remember { mutableStateOf(pinHash != null) }
    var pinEntry by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var pinSetup by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val colors=if(dark) darkColorScheme(primary=Sage,background=Color(0xFF111A16),surface=Color(0xFF1C2821)) else lightColorScheme(primary=Forest,background=Ivory,surface=Color.White)
    BackHandler(enabled=!dialog && !categoryDialog && !budgetDialog && !pinSetup && !locked) {
        if (page != 0) page = 0
        else (context as? Activity)?.moveTaskToBack(true)
    }
    MaterialTheme(colorScheme=colors) {
        Scaffold(containerColor=colors.background, bottomBar={
            NavigationBar(containerColor=colors.surface.copy(alpha=0.92f), tonalElevation=8.dp) {
                val icons=listOf(Icons.Default.SpaceDashboard,Icons.Default.CalendarMonth,Icons.Default.ReceiptLong,Icons.Default.Tune)
                listOf("Home","Calendar","History","More").forEachIndexed { i,s ->
                    NavigationBarItem(selected=page==i,onClick={page=i},icon={Icon(icons[i],null)},label={Text(s,fontSize=10.sp)})
                }
            }
        }, floatingActionButton={
            FloatingActionButton(onClick={androidx.compose.ui.platform.LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress);editing=null;dialog=true},containerColor=Forest,contentColor=Color.White,shape=RoundedCornerShape(20.dp)){Icon(Icons.Default.Add,null)}
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                AnimatedContent(targetState=page,transitionSpec={fadeIn(tween(220))+slideInHorizontally(initialOffsetX={it/24}) togetherWith fadeOut(tween(140))},label="page-transition") { activePage ->
                    when(activePage) {
                        0 -> Home(rows,cats,parsePaise(budget)?:1500000L,onBudget={budgetDialog=true},onEdit={editing=it;dialog=true})
                        1 -> CalendarPage(rows,cats,onEdit={editing=it;dialog=true})
                        2 -> History(rows,cats,search,{search=it},onEdit={editing=it;dialog=true},onDelete={vm.delete(it)},onRestore={vm.restore(it)})
                        else -> More(cats,dark,{dark=it;prefs.edit().putBoolean("dark_mode",it).apply()},{editingCategory=null;categoryDialog=true},{budgetDialog=true},budget,onEditCategory={editingCategory=it;categoryDialog=true},pinEnabled=pinHash!=null,onSetPin={pinSetup=true},onDisablePin={pinHash=null;prefs.edit().remove("app_pin_hash").apply();locked=false})
                    }
                }
            }
        }
        if(dialog) EntryDialog(editing,cats,{dialog=false;editing=null},{vm.save(it);dialog=false;editing=null},{categoryDialog=true})
        if(categoryDialog) CategoryDialog(editingCategory,{categoryDialog=false;editingCategory=null},{item->if(item.id==0L) vm.addCategory(item.name,item.icon,item.color,item.isIncome) else vm.updateCategory(item);categoryDialog=false;editingCategory=null},{item->vm.deleteCategory(item);categoryDialog=false;editingCategory=null})
        if(budgetDialog) BudgetDialog(budget,{budget=it;prefs.edit().putString("monthly_budget",it).apply();budgetDialog=false},{budgetDialog=false})
        if(pinSetup) PinSetupDialog({pinSetup=false},{pin->pinHash=pinDigest(pin);prefs.edit().putString("app_pin_hash",pinHash).apply();locked=false;pinSetup=false})
        if(locked) AlertDialog(onDismissRequest={},title={Text("RUVO is locked")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Enter your app PIN to continue.",fontSize=12.sp,color=Muted);OutlinedTextField(pinEntry,{pinEntry=it.filter(Char::isDigit).take(6);pinError=false},label={Text("PIN")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),singleLine=true,isError=pinError);if(pinError)Text("Incorrect PIN",color=Color(0xFFB45E52),fontSize=11.sp)}},confirmButton={TextButton(onClick={if(pinHash!=null && pinDigest(pinEntry)==pinHash){locked=false;pinEntry="";pinError=false}else pinError=true}){Text("Unlock")}})
    }
}

@Composable private fun Heading(title:String,sub:String) {
    Column(Modifier.padding(horizontal=22.dp,vertical=18.dp)) {
        Text(title,fontSize=28.sp,fontWeight=FontWeight.SemiBold,letterSpacing=(-.7).sp)
        Text(sub,color=Muted,fontSize=12.sp)
    }
}
@Composable private fun Home(rows:List<RuvoTransaction>,cats:List<RuvoCategory>,budget:Long,onBudget:()->Unit,onEdit:(RuvoTransaction)->Unit) {
    var range by remember { mutableStateOf("Month") }
    val now=LocalDate.now()
    val start=when(range){"Day"->now;"Week"->now.with(DayOfWeek.MONDAY);"Year"->now.withDayOfYear(1);else->now.withDayOfMonth(1)}
    val end=when(range){"Day"->now;"Week"->start.plusDays(6);"Year"->now.withDayOfYear(now.lengthOfYear());else->now.withDayOfMonth(now.lengthOfMonth())}
    val shown=rows.filter{between(it,start,end)}
    val thisMonth=rows.filter{between(it,now.withDayOfMonth(1),now)}
    val average=spent(thisMonth)/now.dayOfMonth
    val previousStart=now.withDayOfMonth(1).minusMonths(1)
    val previousEnd=previousStart.withDayOfMonth(minOf(now.dayOfMonth,previousStart.lengthOfMonth()))
    val previous=spent(rows.filter{between(it,previousStart,previousEnd)})
    val current=spent(thisMonth)
    val changeLabel=if(previous==0L){if(current==0L)"0%" else "New"}else{val pct=((current.toDouble()-previous.toDouble())/previous.toDouble()*100.0).toInt();(if(pct>0)"+" else "")+pct+"%"}
    LazyColumn(contentPadding=PaddingValues(bottom=90.dp),verticalArrangement=Arrangement.spacedBy(15.dp)) {
        item { Heading("ruvo","A little more clarity with every day.") }
        item {
            Column(Modifier.padding(horizontal=18.dp).shadow(16.dp,RoundedCornerShape(28.dp),ambientColor=Forest.copy(alpha=.16f),spotColor=Forest.copy(alpha=.22f)).clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFF214D3E),Forest,Color(0xFF102F26)))).border(1.dp,Color.White.copy(alpha=.20f),RoundedCornerShape(28.dp)).padding(21.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("TOTAL SPENT",color=Sage,fontSize=10.sp,letterSpacing=1.5.sp);Icon(Icons.Default.Wallet,null,tint=Sage)}
                AnimatedContent(spent(shown),label="amount"){Text(cash(it),fontSize=34.sp,color=Color.White,fontWeight=FontWeight.SemiBold)}
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Day","Week","Month","Year").forEach{v->FilterChip(selected=range==v,onClick={range=v},label={Text(v,fontSize=11.sp)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Color(0xFFDAE9D3),selectedLabelColor=Forest))}}
                Spacer(Modifier.height(15.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    Column{Text("MONTHLY DAILY AVG.",color=Sage,fontSize=9.sp);Text(cash(average),color=Color.White,fontSize=18.sp,fontWeight=FontWeight.SemiBold)}
                    Column(horizontalAlignment=Alignment.End){Text("VS LAST MONTH",color=Sage,fontSize=9.sp);Text(changeLabel,color=Color.White,fontSize=18.sp,fontWeight=FontWeight.SemiBold)}
                }
            }
        }
        item { Section("Spending analytics","This month at a glance") }
        item {
            val income=thisMonth.filter{it.type=="INCOME"}.sumOf{it.amountPaise}
            val expense=current
            val days=(1..now.dayOfMonth).map{d->spent(rows.filter{between(it,now.withDayOfMonth(d),now.withDayOfMonth(d))})}
            val peak=days.maxOrNull()?:0L
            val peakDay=(days.indexOf(peak)+1).coerceAtLeast(1)
            val top=cats.filter{!it.isIncome}.map{c->c to thisMonth.filter{it.type=="EXPENSE"&&it.categoryId==c.id}.sumOf{it.amountPaise}}.maxByOrNull{it.second}
            Column(Modifier.padding(horizontal=18.dp).glass(RoundedCornerShape(22.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(13.dp)){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    Column { Text("Income",fontSize=10.sp,color=Muted);Text(cash(income),fontSize=16.sp,fontWeight=FontWeight.SemiBold,color=Forest) }
                    Column(horizontalAlignment=Alignment.End) { Text("Expenses",fontSize=10.sp,color=Muted);Text(cash(expense),fontSize=16.sp,fontWeight=FontWeight.SemiBold) }
                }
                HorizontalDivider(color=Muted.copy(alpha=.18f))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    Column { Text("Highest spending day",fontSize=10.sp,color=Muted);Text(if(peak>0) "${peakDay} ${now.month.getDisplayName(java.time.format.TextStyle.SHORT,Locale.ENGLISH)}" else "—",fontSize=14.sp,fontWeight=FontWeight.SemiBold) }
                    Column(horizontalAlignment=Alignment.End) { Text("Net cash flow",fontSize=10.sp,color=Muted);Text(cash(income-expense),fontSize=14.sp,fontWeight=FontWeight.SemiBold,color=if(income>=expense)Forest else Color(0xFFB45E52)) }
                }
                Text("Daily spending trend",fontSize=12.sp,fontWeight=FontWeight.Medium)
                DailySpendChart(days)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("1 ${now.month.getDisplayName(java.time.format.TextStyle.SHORT,Locale.ENGLISH)}",fontSize=9.sp,color=Muted);Text("Today",fontSize=9.sp,color=Muted)}
            }
        }
        item { Section("Category breakdown","This month") }
        item {
            val grouped=thisMonth.filter{it.type=="EXPENSE"}.groupBy{it.categoryId}.mapValues{it.value.sumOf{t->t.amountPaise}}.entries.sortedByDescending{it.value}.take(6)
            Column(Modifier.padding(horizontal=18.dp).glass(RoundedCornerShape(22.dp)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                if(grouped.isEmpty()) Empty("Your category insights will appear here.")
                grouped.forEach{e->
                    val c=cats.find{it.id==e.key}
                    Column(verticalArrangement=Arrangement.spacedBy(5.dp)){
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(c?.name?:"Other",fontSize=13.sp);Text(cash(e.value),fontSize=13.sp,fontWeight=FontWeight.SemiBold)}
                        val f=(e.value.toFloat()/maxOf(1L,current)).coerceIn(0f,1f)
                        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0xFFE6EAE3))){Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(Color(c?.color?:0xFF548B69)))}
                    }
                }
            }
        }
        item { Row(Modifier.fillMaxWidth().padding(horizontal=22.dp),horizontalArrangement=Arrangement.SpaceBetween){Section("Monthly budget", "Your spending limit");Text("Edit",color=Forest,fontSize=12.sp,modifier=Modifier.clickable{onBudget()}.padding(5.dp))} }
        item {
            val p=(current.toFloat()/budget.coerceAtLeast(1)).coerceIn(0f,1f)
            Column(Modifier.padding(horizontal=18.dp).glass(RoundedCornerShape(22.dp)).padding(16.dp)){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    Text(if(current>budget) "Over budget" else "Remaining",color=Muted,fontSize=12.sp)
                    Text(if(current>budget) cash(current-budget) else cash(budget-current),fontWeight=FontWeight.SemiBold,color=if(current>budget) Color(0xFFB45E52) else MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.height(9.dp));Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color(0xFFE6EAE3))){Box(Modifier.fillMaxWidth(p).fillMaxHeight().background(if(p>=1)Color(0xFFB45E52) else Forest))}
                Text((p*100).toInt().toString()+"% used",fontSize=10.sp,color=Muted,modifier=Modifier.padding(top=6.dp))
            }
        }
        item { Section("Smart insights","Patterns from your local data") }
        item {
            val top=cats.filter{!it.isIncome}.map{c->c to thisMonth.filter{it.type=="EXPENSE"&&it.categoryId==c.id}.sumOf{it.amountPaise}}.maxByOrNull{it.second}
            val income=thisMonth.filter{it.type=="INCOME"}.sumOf{it.amountPaise}
            val yesterday=now.minusDays(1)
            val todaySpend=spent(rows.filter{between(it,now,now)})
            val previousMonthTotal=spent(rows.filter{between(it,now.minusMonths(1).withDayOfMonth(1),now.withDayOfMonth(1).minusDays(1))})
            val delta=current-previousMonthTotal
            Column(Modifier.padding(horizontal=18.dp).glass(RoundedCornerShape(22.dp)).padding(15.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
                InsightLine(Icons.Default.AutoAwesome,when{current==0L->"Add a few expenses to unlock spending insights.";previousMonthTotal==0L->"You have spent ${cash(current)} so far this month.";delta>0->"Spending is ${cash(delta)} higher than the previous full month.";delta<0->"Spending is ${cash(-delta)} lower than the previous full month.";else->"Your spending matches the previous month."})
                if(top!=null&&top.second>0) InsightLine(Icons.Default.Leaderboard,"${top.first.name} is your highest-spending category at ${cash(top.second)}.")
                if(todaySpend>0) InsightLine(Icons.Default.Today,"You have spent ${cash(todaySpend)} today.")
                if(budget>0 && current>budget) InsightLine(Icons.Default.Warning,"You are ${cash(current-budget)} over your monthly budget.")
                else if(budget>0 && current.toDouble()/budget>=.75) InsightLine(Icons.Default.NotificationsActive,"You have used ${(current.toDouble()/budget*100).toInt()}% of your monthly budget.")
                if(income>0) InsightLine(Icons.Default.AccountBalanceWallet,"Your recorded income this month is ${cash(income)}.")
            }
        }
        item { Section("Recent activity","Latest entries") }
        item {
            Column(Modifier.padding(horizontal=18.dp).glass(RoundedCornerShape(22.dp)).padding(horizontal=14.dp)){
                if(rows.isEmpty()) Empty("Add your first expense to get started.")
                rows.take(5).forEach{TransactionLine(it,cats,onClick={onEdit(it)})}
            }
        }
    }
}
@Composable private fun DailySpendChart(values:List<Long>){
    Canvas(Modifier.fillMaxWidth().height(104.dp)){
        if(values.isEmpty()) return@Canvas
        val max=values.maxOrNull()?.coerceAtLeast(1L)?:1L
        val gap=3.dp.toPx()
        val barWidth=((size.width-gap*(values.size-1))/values.size).coerceAtLeast(1f)
        values.forEachIndexed{index,value->
            val height=(value.toFloat()/max*size.height*.88f).coerceAtLeast(if(value>0)3.dp.toPx() else 1.dp.toPx())
            drawRoundRect(color=if(value==max&&value>0) Forest else Sage.copy(alpha=if(value>0).9f else .22f),topLeft=Offset(index*(barWidth+gap),size.height-height),size=Size(barWidth,height),cornerRadius=CornerRadius(4.dp.toPx(),4.dp.toPx()))
        }
    }
}
@Composable private fun InsightLine(icon:ImageVector,message:String){
    Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(10.dp)){
        Box(Modifier.size(30.dp).clip(CircleShape).background(Sage.copy(alpha=.24f)),contentAlignment=Alignment.Center){Icon(icon,null,tint=Forest,modifier=Modifier.size(16.dp))}
        Text(message,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface,modifier=Modifier.weight(1f))
    }
}
@Composable private fun Section(title:String,sub:String){Column(Modifier.padding(horizontal=22.dp)){Text(title,fontWeight=FontWeight.SemiBold,fontSize=17.sp);Text(sub,fontSize=11.sp,color=Muted)}}
@Composable private fun Empty(s:String){Text(s,color=Muted,fontSize=12.sp,modifier=Modifier.fillMaxWidth().padding(vertical=22.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center)}
@Composable private fun TransactionLine(t:RuvoTransaction,cats:List<RuvoCategory>,onClick:()->Unit){
    Row(Modifier.fillMaxWidth().clickable{onClick()}.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFFE8EEE5)),contentAlignment=Alignment.Center){Icon(categoryGlyph(cats.find{it.id==t.categoryId}?.icon),null,tint=Forest,modifier=Modifier.size(19.dp))}
        Column(Modifier.weight(1f).padding(start=10.dp)){Text(cats.find{it.id==t.categoryId}?.name?:"Entry",fontSize=13.sp,fontWeight=FontWeight.Medium);Text(t.note.ifBlank{Instant.ofEpochMilli(t.timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM"))},fontSize=10.sp,color=Muted)}
        Text((if(t.type=="INCOME")"+" else "−")+cash(t.amountPaise),fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=if(t.type=="INCOME")Forest else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable private fun CalendarPage(rows:List<RuvoTransaction>,cats:List<RuvoCategory>,onEdit:(RuvoTransaction)->Unit){
    var month by remember{mutableStateOf(YearMonth.now())}
    var selected by remember{mutableStateOf(LocalDate.now())}
    var monthDrag by remember{mutableFloatStateOf(0f)}
    val daily=(1..month.lengthOfMonth()).associateWith{d->spent(rows.filter{between(it,month.atDay(d),month.atDay(d))})}
    val maxSpend=maxOf(1L,daily.values.maxOrNull()?:1L)
    val chosen=rows.filter{between(it,selected,selected)}
    val monthRows=rows.filter{between(it,month.atDay(1),month.atEndOfMonth())}
    val monthExpense=spent(monthRows)
    val monthIncome=monthRows.filter{it.type=="INCOME"}.sumOf{it.amountPaise}
    val monthAverage=if(month.lengthOfMonth()>0)monthExpense/month.lengthOfMonth() else 0L
    val selectedIncome=chosen.filter{it.type=="INCOME"}.sumOf{it.amountPaise}
    val haptic=LocalHapticFeedback.current
    Column(Modifier.fillMaxSize()){
        Heading("Calendar","Your spending, day by day.")
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),horizontalArrangement=Arrangement.SpaceBetween){
            Column { Text("Monthly spending",fontSize=10.sp,color=Muted);Text(cash(monthExpense),fontSize=21.sp,fontWeight=FontWeight.SemiBold,color=Forest) }
            Column(horizontalAlignment=Alignment.End) { Text("Daily average",fontSize=10.sp,color=Muted);Text(cash(monthAverage),fontSize=16.sp,fontWeight=FontWeight.SemiBold) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("Income  ${cash(monthIncome)}",fontSize=11.sp,color=Forest);Text("Net  ${cash(monthIncome-monthExpense)}",fontSize=11.sp,color=if(monthIncome>=monthExpense)Forest else Color(0xFFB45E52))}
        Column(Modifier.padding(horizontal=18.dp).animateContentSize().pointerInput(month){detectHorizontalDragGestures(onHorizontalDrag={_,dx->monthDrag+=dx},onDragEnd={if(monthDrag < -60f){month=month.plusMonths(1);selected=month.atDay(1)}else if(monthDrag > 60f){month=month.minusMonths(1);selected=month.atDay(1)};monthDrag=0f})}.glass(RoundedCornerShape(25.dp)).padding(15.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick={haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);month=month.minusMonths(1);selected=month.atDay(1)}){Icon(Icons.Default.ChevronLeft,null)}
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),fontWeight=FontWeight.SemiBold,fontSize=17.sp)
                IconButton(onClick={haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);month=month.plusMonths(1);selected=month.atDay(1)}){Icon(Icons.Default.ChevronRight,null)}
            }
            Row(Modifier.fillMaxWidth()){listOf("M","T","W","T","F","S","S").forEach{Box(Modifier.weight(1f),contentAlignment=Alignment.Center){Text(it,fontSize=10.sp,color=Muted)}}}
            val offset=month.atDay(1).dayOfWeek.value-1
            for(w in 0..5) Row(Modifier.fillMaxWidth()){
                for(c in 0..6){
                    val d=w*7+c-offset+1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp)){
                        if(d in 1..month.lengthOfMonth()){
                            val spend=daily[d]?:0L
                            val ratio=spend.toFloat()/maxSpend
                            val bg=when{spend==0L->Color(0xFFF0F1EB);ratio<.25f->Color(0xFFDCE8D8);ratio<.5f->Color(0xFFB5CEB0);ratio<.75f->Color(0xFF7FA17E);else->Forest}
                            val date=month.atDay(d)
                            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(9.dp)).background(if(date==selected)Forest else bg).clickable{haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);selected=date},contentAlignment=Alignment.Center){Text(d.toString(),fontSize=12.sp,color=if(date==selected||(spend>0&&ratio>=.75f))Color.White else Color(0xFF202923))}
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("Less",fontSize=10.sp,color=Muted);Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf(Color(0xFFF0F1EB),Color(0xFFDCE8D8),Color(0xFFB5CEB0),Color(0xFF7FA17E),Forest).forEach{Box(Modifier.size(13.dp).clip(RoundedCornerShape(3.dp)).background(it))}};Text("More",fontSize=10.sp,color=Muted)}
        }
        Column(Modifier.padding(18.dp).glass(RoundedCornerShape(22.dp)).padding(15.dp)){
            Text(selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),fontSize=12.sp,color=Muted)
            Text(cash(spent(chosen)),fontSize=26.sp,fontWeight=FontWeight.SemiBold,color=Forest)
            if(selectedIncome>0) Text("Income  +${cash(selectedIncome)}",fontSize=11.sp,color=Forest)
            if(chosen.isEmpty()) Empty("Nothing recorded on this day.") else chosen.forEach{TransactionLine(it,cats){onEdit(it)}}
        }
    }
}

@Composable private fun History(rows:List<RuvoTransaction>,cats:List<RuvoCategory>,query:String,onQuery:(String)->Unit,onEdit:(RuvoTransaction)->Unit,onDelete:(RuvoTransaction)->Unit,onRestore:(RuvoTransaction)->Unit){
    val snackbar = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    var deleted by remember { mutableStateOf<RuvoTransaction?>(null) }
    var filter by remember{mutableStateOf("All")}
    var fromDate by remember{mutableStateOf<LocalDate?>(null)}
    var toDate by remember{mutableStateOf<LocalDate?>(null)}
    var datePickerTarget by remember{mutableStateOf<String?>(null)}
    var pending by remember{mutableStateOf<RuvoTransaction?>(null)}
    val shown=rows.filter{(filter=="All"||it.type==filter)&&(fromDate==null||it.timestamp>=midnight(fromDate!!))&&(toDate==null||it.timestamp<midnight(toDate!!.plusDays(1)))&&(query.isBlank()||it.note.contains(query,true)||cats.find{c->c.id==it.categoryId}?.name?.contains(query,true)==true||cash(it.amountPaise).contains(query,true))}
    LaunchedEffect(deleted) {
        val item = deleted
        if (item != null) {
            val result = snackbar.showSnackbar("Entry deleted", actionLabel="Undo", withDismissAction=true)
            if (result == SnackbarResult.ActionPerformed) onRestore(item)
            deleted = null
        }
    }
    Box(Modifier.fillMaxSize()){
        Column(Modifier.fillMaxSize()){
            Heading("Activity","Every entry, all in one place.")
            OutlinedTextField(query,onQuery,Modifier.fillMaxWidth().padding(horizontal=18.dp),placeholder={Text("Search transactions")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true,shape=RoundedCornerShape(15.dp))
            Row(Modifier.padding(horizontal=18.dp, vertical=10.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("All","EXPENSE","INCOME").forEach{FilterChip(selected=filter==it,onClick={filter=it},label={Text(when(it){"EXPENSE"->"Expenses";"INCOME"->"Income";else->it})})}}
            Row(Modifier.fillMaxWidth().padding(horizontal=18.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
                OutlinedButton(onClick={datePickerTarget="from"},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=8.dp,vertical=7.dp)){Icon(Icons.Default.CalendarMonth,null,modifier=Modifier.size(16.dp));Spacer(Modifier.width(5.dp));Text(fromDate?.format(DateTimeFormatter.ofPattern("d MMM yy"))?:"From date",fontSize=11.sp)}
                OutlinedButton(onClick={datePickerTarget="to"},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=8.dp,vertical=7.dp)){Icon(Icons.Default.CalendarMonth,null,modifier=Modifier.size(16.dp));Spacer(Modifier.width(5.dp));Text(toDate?.format(DateTimeFormatter.ofPattern("d MMM yy"))?:"To date",fontSize=11.sp)}
                if(fromDate!=null||toDate!=null) TextButton(onClick={fromDate=null;toDate=null}){Text("Clear",fontSize=11.sp)}
            }
            if(fromDate!=null&&toDate!=null&&toDate!!.isBefore(fromDate!!)) Text("End date must be on or after the start date.",color=Color(0xFFB45E52),fontSize=11.sp,modifier=Modifier.padding(horizontal=20.dp,vertical=4.dp))
            LazyColumn(contentPadding=PaddingValues(horizontal=18.dp,vertical=4.dp)){
                items(shown,key={it.id}){t->
                    var dx by remember{mutableFloatStateOf(0f)}
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val swipeOffset by animateDpAsState(with(density) { dx.toDp() }, animationSpec=spring(dampingRatio=0.72f, stiffness=420f), label="swipe-row")
                    Row(Modifier.fillMaxWidth().offset(x=swipeOffset).clip(RoundedCornerShape(14.dp)).glass(RoundedCornerShape(14.dp)).pointerInput(t.id){detectHorizontalDragGestures(onHorizontalDrag={_,d->dx=(dx+d).coerceIn(-92f,92f)},onDragEnd={if(dx < -55){haptic.performHapticFeedback(HapticFeedbackType.LongPress);pending=t}else if(dx>55){haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);onEdit(t)};dx=0f})}.clickable{haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove);onEdit(t)}.padding(13.dp),verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){Text(cats.find{it.id==t.categoryId}?.name?:"Entry",fontSize=13.sp,fontWeight=FontWeight.Medium);Text(t.note.ifBlank{"Tap to edit"},fontSize=10.sp,color=Muted)}
                        Text((if(t.type=="INCOME")"+" else "−")+cash(t.amountPaise),fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                    }
                }
            }
        }
        SnackbarHost(snackbar,Modifier.align(Alignment.BottomCenter))
    }
    datePickerTarget?.let { target ->
        val current = if(target=="from") fromDate else toDate
        val pickerState = rememberDatePickerState(initialSelectedDateMillis=(current?:LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest={datePickerTarget=null},confirmButton={TextButton(onClick={pickerState.selectedDateMillis?.let{millis->val picked=Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate();if(target=="from")fromDate=picked else toDate=picked};datePickerTarget=null}){Text("Apply")}},dismissButton={TextButton(onClick={datePickerTarget=null}){Text("Cancel")}}){DatePicker(state=pickerState)}
    }
    pending?.let{t->AlertDialog(onDismissRequest={pending=null},title={Text("Delete entry?")},text={Text("This transaction will be removed.")},confirmButton={TextButton(onClick={onDelete(t);deleted=t;pending=null}){Text("Delete")}},dismissButton={TextButton(onClick={pending=null}){Text("Cancel")}})}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun EntryDialog(existing:RuvoTransaction?,cats:List<RuvoCategory>,onDismiss:()->Unit,onSave:(RuvoTransaction)->Unit,onAddCategory:()->Unit){
    var amount by remember(existing){mutableStateOf(if(existing==null)"" else (existing.amountPaise/100.0).toString())}
    var note by remember(existing){mutableStateOf(existing?.note?:"")}
    var type by remember(existing){mutableStateOf(existing?.type?:"EXPENSE")}
    var category by remember(existing,cats){mutableLongStateOf(existing?.categoryId?:cats.firstOrNull{it.isIncome==(type=="INCOME")}?.id?:0L)}
    var date by remember(existing){mutableStateOf(existing?.timestamp?:System.currentTimeMillis())}
    var showDatePicker by remember { mutableStateOf(false) }
    val options=cats.filter{it.isIncome==(type=="INCOME")}
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(existing==null)"New entry" else "Edit entry",fontWeight=FontWeight.SemiBold)},text={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("EXPENSE","INCOME").forEach{v->FilterChip(selected=type==v,onClick={type=v;category=cats.firstOrNull{it.isIncome==(v=="INCOME")}?.id?:0L},label={Text(if(v=="EXPENSE")"Expense" else "Income")})}}
            OutlinedTextField(amount,{amount=it},label={Text("Amount (₹)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth())
            Text("CATEGORY",fontSize=10.sp,color=Muted,fontWeight=FontWeight.SemiBold)
            options.chunked(3).forEach{group->Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){group.forEach{c->FilterChip(selected=category==c.id,onClick={category=c.id},label={Text(c.name,fontSize=10.sp)})}}}
            TextButton(onClick=onAddCategory){Icon(Icons.Default.Add,null);Text("New category")}
            OutlinedTextField(note,{note=it},label={Text("Note (optional)")},singleLine=true,modifier=Modifier.fillMaxWidth())
            TextButton(onClick={showDatePicker=true}){Icon(Icons.Default.Today,null);Text("Date · "+Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy")))}
        }
    },confirmButton={TextButton(onClick={val paise=parsePaise(amount);if(paise!=null&&category!=0L)onSave(RuvoTransaction(id=existing?.id?:0,amountPaise=paise,categoryId=category,note=note.trim(),timestamp=date,type=type))}){Text("Save")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
    if(showDatePicker){
        val picker=rememberDatePickerState(initialSelectedDateMillis=Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest={showDatePicker=false},confirmButton={TextButton(onClick={picker.selectedDateMillis?.let{date=Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()};showDatePicker=false}){Text("Select")}},dismissButton={TextButton(onClick={showDatePicker=false}){Text("Cancel")}}){DatePicker(state=picker)}
    }
}

@Composable private fun CategoryDialog(existing:RuvoCategory?,onDismiss:()->Unit,onSave:(RuvoCategory)->Unit,onDelete:(RuvoCategory)->Unit){
    var confirmDelete by remember(existing){mutableStateOf(false)}
    var name by remember(existing){mutableStateOf(existing?.name?:"")}
    var income by remember(existing){mutableStateOf(existing?.isIncome?:false)}
    var icon by remember(existing){mutableStateOf(existing?.icon?:"category")}
    val palette=listOf(0xFF548B69,0xFFE3A35C,0xFF6F9DB8,0xFFB18AB8,0xFFC78C69,0xFF8A91A8)
    var color by remember(existing){mutableLongStateOf(existing?.color?:palette[0])}
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(existing==null)"New category" else "Edit category")},text={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            OutlinedTextField(name,{name=it},label={Text("Category name")},singleLine=true)
            if(existing==null) {
                Row{FilterChip(selected=!income,onClick={income=false},label={Text("Expense")});Spacer(Modifier.width(8.dp));FilterChip(selected=income,onClick={income=true},label={Text("Income")})}
            } else {
                Text(if(income) "Income category" else "Expense category",fontSize=12.sp,color=Muted)
            }
            Text("ICON",fontSize=10.sp,color=Muted,fontWeight=FontWeight.SemiBold)
            listOf("category","restaurant","directions_bus","shopping_bag","school","coffee","receipt","payments").chunked(4).forEach { group -> Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) { group.forEach { key -> FilterChip(selected=icon==key,onClick={icon=key},label={Icon(categoryGlyph(key),null,modifier=Modifier.size(16.dp))}) } } }
            Text("COLOUR",fontSize=10.sp,color=Muted,fontWeight=FontWeight.SemiBold)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){palette.forEach{c->Box(Modifier.size(27.dp).clip(CircleShape).background(Color(c)).clickable{color=c},contentAlignment=Alignment.Center){if(c==color)Icon(Icons.Default.Check,null,tint=Color.White)}}}
        }
    },confirmButton={TextButton(onClick={if(name.isNotBlank())onSave(RuvoCategory(id=existing?.id?:0L,name=name.trim(),icon=icon,color=color,isIncome=income))}){Text(if(existing==null)"Create" else "Save")}},dismissButton={Row(verticalAlignment=Alignment.CenterVertically){if(existing!=null)TextButton(onClick={confirmDelete=true}){Text("Delete",color=Color(0xFFB45E52))};TextButton(onClick=onDismiss){Text("Cancel")}}})
    if(confirmDelete && existing!=null) AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Delete category?")},text={Text("Existing transactions will be moved to another category of the same type.")},confirmButton={TextButton(onClick={onDelete(existing)}){Text("Delete")}},dismissButton={TextButton(onClick={confirmDelete=false}){Text("Cancel")}})
}

@Composable private fun PinSetupDialog(onDismiss:()->Unit,onSave:(String)->Unit){
    var first by remember{mutableStateOf("")}
    var second by remember{mutableStateOf("")}
    var error by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Set app PIN")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("Choose a 4–6 digit PIN. It stays on this device.",fontSize=12.sp,color=Muted);OutlinedTextField(first,{first=it.filter(Char::isDigit).take(6)},label={Text("New PIN")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),singleLine=true);OutlinedTextField(second,{second=it.filter(Char::isDigit).take(6)},label={Text("Confirm PIN")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),singleLine=true);if(error)Text("PINs must match and contain 4–6 digits.",color=Color(0xFFB45E52),fontSize=11.sp)}},confirmButton={TextButton(onClick={if(first.length in 4..6 && first==second)onSave(first) else error=true}){Text("Save")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
@Composable private fun BudgetDialog(current:String,onSave:(String)->Unit,onDismiss:()->Unit){
    var value by remember{mutableStateOf(current)}
    var error by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Monthly budget")},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){OutlinedTextField(value,{value=it;error=false},label={Text("Budget in ₹")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),isError=error,singleLine=true);if(error)Text("Enter a valid positive amount.",color=Color(0xFFB45E52),fontSize=11.sp)}},confirmButton={TextButton(onClick={if(parsePaise(value)!=null)onSave(value) else error=true}){Text("Save")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
@Composable private fun More(cats:List<RuvoCategory>,dark:Boolean,onDark:(Boolean)->Unit,onAdd:()->Unit,onBudget:()->Unit,budget:String,onEditCategory:(RuvoCategory)->Unit,pinEnabled:Boolean,onSetPin:()->Unit,onDisablePin:()->Unit){
    val context=androidx.compose.ui.platform.LocalContext.current
    val prefs=remember(context){context.getSharedPreferences("ruvo_preferences",0)}
    var order by remember { mutableStateOf<List<Long>>(prefs.getString("category_order","")?.split(",")?.mapNotNull{it.toLongOrNull()} ?: emptyList()) }
    val orderedCats=cats.sortedBy { val index=order.indexOf(it.id); if(index<0) Int.MAX_VALUE else index }
    LazyColumn(contentPadding=PaddingValues(bottom=90.dp)){
        item{Heading("More","Make RUVO feel like yours.")}
        item{Column(Modifier.padding(horizontal=18.dp).glass(RoundedCornerShape(22.dp)).padding(16.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Column{Text("Dark appearance",fontWeight=FontWeight.Medium);Text("A quieter look after dark",fontSize=11.sp,color=Muted)};Switch(dark,onDark)}
            HorizontalDivider(Modifier.padding(vertical=10.dp))
            Row(Modifier.fillMaxWidth().clickable{onSetPin()}.padding(vertical=7.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("App PIN lock");Text(if(pinEnabled)"Change PIN" else "Set PIN",color=Forest)}
            if(pinEnabled) TextButton(onClick=onDisablePin){Text("Turn off app lock",color=Color(0xFFB45E52))}
            HorizontalDivider(Modifier.padding(vertical=10.dp))
            Row(Modifier.fillMaxWidth().clickable{onBudget()}.padding(vertical=7.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("Monthly budget");Text("₹$budget",color=Forest)}
        }}
        item{Row(Modifier.fillMaxWidth().padding(horizontal=22.dp,vertical=15.dp),horizontalArrangement=Arrangement.SpaceBetween){Column{Text("Your categories",fontSize=17.sp,fontWeight=FontWeight.SemiBold);Text("Personalise your spending",fontSize=11.sp,color=Muted)};TextButton(onClick=onAdd){Text("Add")}}}
        items(orderedCats,key={it.id}){c->
            val index=orderedCats.indexOfFirst{it.id==c.id}
            Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically){
                Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable{onEditCategory(c)}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(Color(c.color).copy(alpha=.16f)),contentAlignment=Alignment.Center){Icon(categoryGlyph(c.icon),null,tint=Color(c.color),modifier=Modifier.size(18.dp))}
                    Column(Modifier.weight(1f).padding(start=10.dp)){Text(c.name,fontSize=13.sp);Text(if(c.isIncome)"Income" else "Expense",fontSize=10.sp,color=Muted)}
                    Icon(Icons.Default.Edit,null,tint=Muted,modifier=Modifier.size(16.dp))
                }
                IconButton(onClick={if(index>0){val ids=orderedCats.map{it.id}.toMutableList();val tmp=ids[index-1];ids[index-1]=ids[index];ids[index]=tmp;order=ids;prefs.edit().putString("category_order",ids.joinToString(",")).apply()}},enabled=index>0){Icon(Icons.Default.KeyboardArrowUp,null)}
                IconButton(onClick={if(index<orderedCats.lastIndex){val ids=orderedCats.map{it.id}.toMutableList();val tmp=ids[index+1];ids[index+1]=ids[index];ids[index]=tmp;order=ids;prefs.edit().putString("category_order",ids.joinToString(",")).apply()}},enabled=index<orderedCats.lastIndex){Icon(Icons.Default.KeyboardArrowDown,null)}
            }
        }
        item{Text("All information stays on this device. RUVO does not connect to the internet or send your data anywhere.",fontSize=11.sp,color=Muted,modifier=Modifier.padding(22.dp))}
    }
}
