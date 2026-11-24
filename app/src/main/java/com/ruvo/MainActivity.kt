package com.ruvo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ruvo.data.*
import java.text.NumberFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Forest = Color(0xFF173F32)
private val Sage = Color(0xFFA9C6A5)
private val Ivory = Color(0xFFF3F1E9)
private val Muted = Color(0xFF7C887F)

class MainActivity : ComponentActivity() {
    private val vm: RuvoViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RuvoApp(vm, intent?.action == ACTION_QUICK_ADD) }
    }
    companion object { const val ACTION_QUICK_ADD = "com.ruvo.QUICK_ADD" }
}

private fun cash(paise: Long): String = NumberFormat.getCurrencyInstance(Locale("en","IN")).apply { maximumFractionDigits=0; minimumFractionDigits=0 }.format(paise/100.0)
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
    var budgetDialog by remember { mutableStateOf(false) }
    val prefs = remember { androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("ruvo_preferences", 0) }
    var budget by remember { mutableStateOf(prefs.getString("monthly_budget", "15000") ?: "15000") }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark_mode", false)) }
    var search by remember { mutableStateOf("") }
    val colors=if(dark) darkColorScheme(primary=Sage,background=Color(0xFF111A16),surface=Color(0xFF1C2821)) else lightColorScheme(primary=Forest,background=Ivory,surface=Color.White)
    MaterialTheme(colorScheme=colors) {
        Scaffold(containerColor=colors.background, bottomBar={
            NavigationBar(containerColor=colors.surface) {
                val icons=listOf(Icons.Default.SpaceDashboard,Icons.Default.CalendarMonth,Icons.Default.ReceiptLong,Icons.Default.Tune)
                listOf("Home","Calendar","History","More").forEachIndexed { i,s ->
                    NavigationBarItem(selected=page==i,onClick={page=i},icon={Icon(icons[i],null)},label={Text(s,fontSize=10.sp)})
                }
            }
        }, floatingActionButton={
            FloatingActionButton(onClick={editing=null;dialog=true},containerColor=Forest,contentColor=Color.White,shape=RoundedCornerShape(20.dp)){Icon(Icons.Default.Add,null)}
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when(page) {
                    0 -> Home(rows,cats,budget.toLongOrNull()?.times(100)?:1500000L,onBudget={budgetDialog=true},onEdit={editing=it;dialog=true})
                    1 -> CalendarPage(rows,onEdit={editing=it;dialog=true})
                    2 -> History(rows,cats,search,{search=it},onEdit={editing=it;dialog=true},onDelete={vm.delete(it)})
                    else -> More(cats,dark,{dark=it;prefs.edit().putBoolean("dark_mode",it).apply()},{categoryDialog=true},{budgetDialog=true},budget)
                }
            }
        }
        if(dialog) EntryDialog(editing,cats,{dialog=false;editing=null},{vm.save(it);dialog=false;editing=null},{categoryDialog=true})
        if(categoryDialog) CategoryDialog({categoryDialog=false},{n,i,c,inc->vm.addCategory(n,i,c,inc);categoryDialog=false})
        if(budgetDialog) BudgetDialog(budget,{budget=it;prefs.edit().putString("monthly_budget",it).apply();budgetDialog=false},{budgetDialog=false})
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
    val previous=spent(rows.filter{between(it,previousStart,now.withDayOfMonth(1).minusDays(1))})
    val current=spent(thisMonth)
    val change=if(previous==0L) 0 else ((current-previous)*100/previous).toInt()
    LazyColumn(contentPadding=PaddingValues(bottom=90.dp),verticalArrangement=Arrangement.spacedBy(15.dp)) {
        item { Heading("ruvo","A little more clarity with every day.") }
        item {
            Column(Modifier.padding(horizontal=18.dp).clip(RoundedCornerShape(28.dp)).background(Forest).padding(21.dp)) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("TOTAL SPENT",color=Sage,fontSize=10.sp,letterSpacing=1.5.sp);Icon(Icons.Default.Wallet,null,tint=Sage)}
                AnimatedContent(spent(shown),label="amount"){Text(cash(it),fontSize=34.sp,color=Color.White,fontWeight=FontWeight.SemiBold)}
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Day","Week","Month","Year").forEach{v->FilterChip(selected=range==v,onClick={range=v},label={Text(v,fontSize=11.sp)},colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Color(0xFFDAE9D3),selectedLabelColor=Forest))}}
                Spacer(Modifier.height(15.dp))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    Column{Text("MONTHLY DAILY AVG.",color=Sage,fontSize=9.sp);Text(cash(average),color=Color.White,fontSize=18.sp,fontWeight=FontWeight.SemiBold)}
                    Column(horizontalAlignment=Alignment.End){Text("VS LAST MONTH",color=Sage,fontSize=9.sp);Text((if(change>0)"+" else "")+change+"%",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.SemiBold)}
                }
            }
        }
        item { Section("Category breakdown","This month") }
        item {
            val grouped=thisMonth.filter{it.type=="EXPENSE"}.groupBy{it.categoryId}.mapValues{it.value.sumOf{t->t.amountPaise}}.entries.sortedByDescending{it.value}.take(6)
            Column(Modifier.padding(horizontal=18.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
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
            Column(Modifier.padding(horizontal=18.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Remaining",color=Muted,fontSize=12.sp);Text(cash((budget-current).coerceAtLeast(0)),fontWeight=FontWeight.SemiBold)}
                Spacer(Modifier.height(9.dp));Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color(0xFFE6EAE3))){Box(Modifier.fillMaxWidth(p).fillMaxHeight().background(if(p>=1)Color(0xFFB45E52) else Forest))}
                Text((p*100).toInt().toString()+"% used",fontSize=10.sp,color=Muted,modifier=Modifier.padding(top=6.dp))
            }
        }
        item { Section("Recent activity","Latest entries") }
        item {
            Column(Modifier.padding(horizontal=18.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal=14.dp)){
                if(rows.isEmpty()) Empty("Add your first expense to get started.")
                rows.take(5).forEach{TransactionLine(it,cats,onClick={onEdit(it)})}
            }
        }
    }
}
@Composable private fun Section(title:String,sub:String){Column(Modifier.padding(horizontal=22.dp)){Text(title,fontWeight=FontWeight.SemiBold,fontSize=17.sp);Text(sub,fontSize=11.sp,color=Muted)}}
@Composable private fun Empty(s:String){Text(s,color=Muted,fontSize=12.sp,modifier=Modifier.fillMaxWidth().padding(vertical=22.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center)}
@Composable private fun TransactionLine(t:RuvoTransaction,cats:List<RuvoCategory>,onClick:()->Unit){
    Row(Modifier.fillMaxWidth().clickable{onClick()}.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFE8EEE5)),contentAlignment=Alignment.Center){Icon(Icons.Default.Receipt,null,tint=Forest,modifier=Modifier.size(18.dp))}
        Column(Modifier.weight(1f).padding(start=10.dp)){Text(cats.find{it.id==t.categoryId}?.name?:"Entry",fontSize=13.sp,fontWeight=FontWeight.Medium);Text(t.note.ifBlank{Instant.ofEpochMilli(t.timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM"))},fontSize=10.sp,color=Muted)}
        Text((if(t.type=="INCOME")"+" else "−")+cash(t.amountPaise),fontSize=13.sp,fontWeight=FontWeight.SemiBold,color=if(t.type=="INCOME")Forest else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable private fun CalendarPage(rows:List<RuvoTransaction>,onEdit:(RuvoTransaction)->Unit){
    var month by remember{mutableStateOf(YearMonth.now())}
    var selected by remember{mutableStateOf(LocalDate.now())}
    var monthDrag by remember{mutableFloatStateOf(0f)}
    val daily=(1..month.lengthOfMonth()).associateWith{d->spent(rows.filter{between(it,month.atDay(d),month.atDay(d))})}
    val maxSpend=maxOf(1L,daily.values.maxOrNull()?:1L)
    val chosen=rows.filter{between(it,selected,selected)}
    Column(Modifier.fillMaxSize()){
        Heading("Calendar","Your spending, day by day.")
        Column(Modifier.padding(horizontal=18.dp).animateContentSize().pointerInput(month){detectHorizontalDragGestures(onHorizontalDrag={_,dx->monthDrag+=dx},onDragEnd={if(monthDrag < -60f){month=month.plusMonths(1);selected=month.atDay(1)}else if(monthDrag > 60f){month=month.minusMonths(1);selected=month.atDay(1)};monthDrag=0f})}.clip(RoundedCornerShape(25.dp)).background(MaterialTheme.colorScheme.surface).padding(15.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick={month=month.minusMonths(1);selected=month.atDay(1)}){Icon(Icons.Default.ChevronLeft,null)}
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),fontWeight=FontWeight.SemiBold,fontSize=17.sp)
                IconButton(onClick={month=month.plusMonths(1);selected=month.atDay(1)}){Icon(Icons.Default.ChevronRight,null)}
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
                            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(9.dp)).background(if(date==selected)Forest else bg).clickable{selected=date},contentAlignment=Alignment.Center){Text(d.toString(),fontSize=12.sp,color=if(date==selected||(spend>0&&ratio>=.75f))Color.White else Color(0xFF202923))}
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text("Less",fontSize=10.sp,color=Muted);Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf(Color(0xFFF0F1EB),Color(0xFFDCE8D8),Color(0xFFB5CEB0),Color(0xFF7FA17E),Forest).forEach{Box(Modifier.size(13.dp).clip(RoundedCornerShape(3.dp)).background(it))}};Text("More",fontSize=10.sp,color=Muted)}
        }
        Column(Modifier.padding(18.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(15.dp)){
            Text(selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),fontSize=12.sp,color=Muted)
            Text(cash(spent(chosen)),fontSize=26.sp,fontWeight=FontWeight.SemiBold,color=Forest)
            if(chosen.isEmpty()) Empty("Nothing recorded on this day.") else chosen.forEach{TransactionLine(it,emptyList()){onEdit(it)}}
        }
    }
}

@Composable private fun History(rows:List<RuvoTransaction>,cats:List<RuvoCategory>,query:String,onQuery:(String)->Unit,onEdit:(RuvoTransaction)->Unit,onDelete:(RuvoTransaction)->Unit){
    var filter by remember{mutableStateOf("All")}
    var pending by remember{mutableStateOf<RuvoTransaction?>(null)}
    val shown=rows.filter{(filter=="All"||it.type==filter)&&(query.isBlank()||it.note.contains(query,true)||cats.find{c->c.id==it.categoryId}?.name?.contains(query,true)==true||cash(it.amountPaise).contains(query,true))}
    Column(Modifier.fillMaxSize()){
        Heading("Activity","Every entry, all in one place.")
        OutlinedTextField(query,onQuery,Modifier.fillMaxWidth().padding(horizontal=18.dp),placeholder={Text("Search transactions")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true,shape=RoundedCornerShape(15.dp))
        Row(Modifier.padding(18.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("All","EXPENSE","INCOME").forEach{FilterChip(selected=filter==it,onClick={filter=it},label={Text(when(it){"EXPENSE"->"Expenses";"INCOME"->"Income";else->it})})}}
        LazyColumn(contentPadding=PaddingValues(horizontal=18.dp,vertical=4.dp)){
            items(shown,key={it.id}){t->
                var dx by remember{mutableFloatStateOf(0f)}
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).pointerInput(t.id){detectHorizontalDragGestures(onHorizontalDrag={_,d->dx=(dx+d).coerceIn(-100f,100f)},onDragEnd={if(dx < -55)pending=t else if(dx>55)onEdit(t);dx=0f})}.clickable{onEdit(t)}.padding(13.dp),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text(cats.find{it.id==t.categoryId}?.name?:"Entry",fontSize=13.sp,fontWeight=FontWeight.Medium);Text(t.note.ifBlank{"Tap to edit"},fontSize=10.sp,color=Muted)}
                    Text((if(t.type=="INCOME")"+" else "−")+cash(t.amountPaise),fontSize=13.sp,fontWeight=FontWeight.SemiBold)
                }
            }
        }
    }
    pending?.let{t->AlertDialog(onDismissRequest={pending=null},title={Text("Delete entry?")},text={Text("This transaction will be removed.")},confirmButton={TextButton(onClick={onDelete(t);pending=null}){Text("Delete")}},dismissButton={TextButton(onClick={pending=null}){Text("Cancel")}})}
}

@Composable private fun EntryDialog(existing:RuvoTransaction?,cats:List<RuvoCategory>,onDismiss:()->Unit,onSave:(RuvoTransaction)->Unit,onAddCategory:()->Unit){
    var amount by remember(existing){mutableStateOf(if(existing==null)"" else (existing.amountPaise/100.0).toString())}
    var note by remember(existing){mutableStateOf(existing?.note?:"")}
    var type by remember(existing){mutableStateOf(existing?.type?:"EXPENSE")}
    var category by remember(existing,cats){mutableLongStateOf(existing?.categoryId?:cats.firstOrNull{it.isIncome==(type=="INCOME")}?.id?:0L)}
    var date by remember(existing){mutableStateOf(existing?.timestamp?:System.currentTimeMillis())}
    val options=cats.filter{it.isIncome==(type=="INCOME")}
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(existing==null)"New entry" else "Edit entry",fontWeight=FontWeight.SemiBold)},text={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){listOf("EXPENSE","INCOME").forEach{v->FilterChip(selected=type==v,onClick={type=v;category=cats.firstOrNull{it.isIncome==(v=="INCOME")}?.id?:0L},label={Text(if(v=="EXPENSE")"Expense" else "Income")})}}
            OutlinedTextField(amount,{amount=it},label={Text("Amount (₹)")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth())
            Text("CATEGORY",fontSize=10.sp,color=Muted,fontWeight=FontWeight.SemiBold)
            options.chunked(3).forEach{group->Row(horizontalArrangement=Arrangement.spacedBy(5.dp)){group.forEach{c->FilterChip(selected=category==c.id,onClick={category=c.id},label={Text(c.name,fontSize=10.sp)})}}}
            TextButton(onClick=onAddCategory){Icon(Icons.Default.Add,null);Text("New category")}
            OutlinedTextField(note,{note=it},label={Text("Note (optional)")},singleLine=true,modifier=Modifier.fillMaxWidth())
            TextButton(onClick={date=System.currentTimeMillis()}){Icon(Icons.Default.Today,null);Text("Date · "+Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy")))}
        }
    },confirmButton={TextButton(onClick={val v=amount.toDoubleOrNull()?:0.0;if(v>0&&category!=0L)onSave(RuvoTransaction(id=existing?.id?:0,amountPaise=(v*100).toLong(),categoryId=category,note=note.trim(),timestamp=date,type=type))}){Text("Save")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

@Composable private fun CategoryDialog(onDismiss:()->Unit,onSave:(String,String,Long,Boolean)->Unit){
    var name by remember{mutableStateOf("")};var income by remember{mutableStateOf(false)}
    val palette=listOf(0xFF548B69,0xFFE3A35C,0xFF6F9DB8,0xFFB18AB8,0xFFC78C69,0xFF8A91A8)
    var color by remember{mutableLongStateOf(palette[0])}
    AlertDialog(onDismissRequest=onDismiss,title={Text("New category")},text={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            OutlinedTextField(name,{name=it},label={Text("Category name")},singleLine=true)
            Row{FilterChip(selected=!income,onClick={income=false},label={Text("Expense")});Spacer(Modifier.width(8.dp));FilterChip(selected=income,onClick={income=true},label={Text("Income")})}
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){palette.forEach{c->Box(Modifier.size(27.dp).clip(CircleShape).background(Color(c)).clickable{color=c},contentAlignment=Alignment.Center){if(c==color)Icon(Icons.Default.Check,null,tint=Color.White)}}}
        }
    },confirmButton={TextButton(onClick={onSave(name,"category",color,income)}){Text("Create")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
@Composable private fun BudgetDialog(current:String,onSave:(String)->Unit,onDismiss:()->Unit){
    var value by remember{mutableStateOf(current)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Monthly budget")},text={OutlinedTextField(value,{value=it},label={Text("Budget in ₹")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))},confirmButton={TextButton(onClick={onSave(value)}){Text("Save")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}
@Composable private fun More(cats:List<RuvoCategory>,dark:Boolean,onDark:(Boolean)->Unit,onAdd:()->Unit,onBudget:()->Unit,budget:String){
    LazyColumn(contentPadding=PaddingValues(bottom=90.dp)){
        item{Heading("More","Make RUVO feel like yours.")}
        item{Column(Modifier.padding(horizontal=18.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Column{Text("Dark appearance",fontWeight=FontWeight.Medium);Text("A quieter look after dark",fontSize=11.sp,color=Muted)};Switch(dark,onDark)}
            HorizontalDivider(Modifier.padding(vertical=10.dp))
            Row(Modifier.fillMaxWidth().clickable{onBudget()}.padding(vertical=7.dp),horizontalArrangement=Arrangement.SpaceBetween){Text("Monthly budget");Text("₹$budget",color=Forest)}
        }}
        item{Row(Modifier.fillMaxWidth().padding(horizontal=22.dp,vertical=15.dp),horizontalArrangement=Arrangement.SpaceBetween){Column{Text("Your categories",fontSize=17.sp,fontWeight=FontWeight.SemiBold);Text("Personalise your spending",fontSize=11.sp,color=Muted)};TextButton(onClick=onAdd){Text("Add")}}}
        items(cats){c->Row(Modifier.padding(horizontal=22.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(11.dp).clip(CircleShape).background(Color(c.color)));Text(c.name,Modifier.weight(1f).padding(start=10.dp),fontSize=13.sp);Text(if(c.isIncome)"Income" else "Expense",fontSize=10.sp,color=Muted)}}
        item{Text("All information stays on this device. RUVO does not connect to the internet or send your data anywhere.",fontSize=11.sp,color=Muted,modifier=Modifier.padding(22.dp))}
    }
}
