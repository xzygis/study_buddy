package com.xzygis.studybuddy.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xzygis.studybuddy.PlanViewModel
import com.xzygis.studybuddy.data.PlanRecord
import com.xzygis.studybuddy.data.StudyPlan
import com.xzygis.studybuddy.data.StudyReminder
import com.xzygis.studybuddy.data.Weekday
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private enum class MainScreen { TODAY, PLANS }

@Composable
fun StudyBuddyApp(
    viewModel: PlanViewModel,
    hasNotificationPermission: Boolean,
    canScheduleExactAlarms: Boolean,
    canUseFullScreenIntent: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    needsAutostartSetup: Boolean,
    onEnablePlan: (String) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAutostartSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(MainScreen.TODAY) }
    var editingPlan by remember { mutableStateOf<StudyPlan?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    editingPlan?.let { plan ->
        PlanEditorScreen(
            plan = plan,
            isExisting = state.database.records.any { it.plan.id == plan.id },
            isBusy = state.isBusy,
            onBack = { editingPlan = null },
            onSave = { viewModel.save(it) { editingPlan = null } },
            onDelete = {
                viewModel.delete(plan.id) { editingPlan = null }
            },
        )
        return
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 700.dp) {
            TabletLayout(
                viewModel = viewModel,
                records = state.database.records,
                isBusy = state.isBusy,
                hasNotificationPermission = hasNotificationPermission,
                canScheduleExactAlarms = canScheduleExactAlarms,
                canUseFullScreenIntent = canUseFullScreenIntent,
                isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                needsAutostartSetup = needsAutostartSetup,
                onEnablePlan = onEnablePlan,
                onOpenNotificationSettings = onOpenNotificationSettings,
                onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                onOpenFullScreenSettings = onOpenFullScreenSettings,
                onOpenBatterySettings = onOpenBatterySettings,
                onOpenAutostartSettings = onOpenAutostartSettings,
                onEdit = { editingPlan = it },
                snackbar = snackbar,
            )
        } else {
            Scaffold(
                containerColor = AppBackground,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = screen == MainScreen.TODAY,
                            onClick = { screen = MainScreen.TODAY },
                            icon = { Icon(Icons.Default.CalendarMonth, null) },
                            label = { Text("今天") },
                        )
                        NavigationBarItem(
                            selected = screen == MainScreen.PLANS,
                            onClick = { screen = MainScreen.PLANS },
                            icon = { Icon(Icons.Default.GridView, null) },
                            label = { Text("计划") },
                        )
                    }
                },
                floatingActionButton = {
                    if (screen == MainScreen.PLANS) {
                        FloatingActionButton(
                            onClick = { editingPlan = StudyPlan.draft() },
                            containerColor = StudyGreen,
                            contentColor = Color.White,
                        ) {
                            Icon(Icons.Default.Add, "新建计划")
                        }
                    }
                },
            ) { padding ->
                when (screen) {
                    MainScreen.TODAY -> TodayScreen(
                        modifier = Modifier.padding(padding),
                        viewModel = viewModel,
                        records = state.database.records,
                        hasNotificationPermission = hasNotificationPermission,
                        canScheduleExactAlarms = canScheduleExactAlarms,
                        canUseFullScreenIntent = canUseFullScreenIntent,
                        isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                        needsAutostartSetup = needsAutostartSetup,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                        onOpenFullScreenSettings = onOpenFullScreenSettings,
                        onOpenBatterySettings = onOpenBatterySettings,
                        onOpenAutostartSettings = onOpenAutostartSettings,
                    )
                    MainScreen.PLANS -> PlansScreen(
                        modifier = Modifier.padding(padding),
                        viewModel = viewModel,
                        records = state.database.records,
                        isBusy = state.isBusy,
                        onEnablePlan = onEnablePlan,
                        onEdit = { editingPlan = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayScreen(
    modifier: Modifier,
    viewModel: PlanViewModel,
    records: List<PlanRecord>,
    hasNotificationPermission: Boolean,
    canScheduleExactAlarms: Boolean,
    canUseFullScreenIntent: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    needsAutostartSetup: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAutostartSettings: () -> Unit,
) {
    val today = LocalDate.now()
    val weekday = Weekday.entries[today.dayOfWeek.value - 1]
    var currentMinute by remember { mutableIntStateOf(LocalTime.now().toSecondOfDay() / 60) }
    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalTime.now()
            currentMinute = now.toSecondOfDay() / 60
            delay((60 - now.second).coerceAtLeast(1) * 1_000L)
        }
    }
    val entries = records
        .filter { viewModel.isEnabled(it) && weekday in it.plan.weekdays }
        .flatMap { record -> record.plan.reminders.map { record.plan.name to it } }
        .sortedBy { it.second.minutesSinceMidnight }
    val statuses = timelineStatuses(
        reminderMinutes = entries.map { it.second.minutesSinceMidnight },
        currentMinute = currentMinute,
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("今日计划", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item {
            DateSummary(today, entries.size)
        }
        if (!hasNotificationPermission) {
            item {
                PermissionBanner(
                    text = "通知权限未开启，闹钟无法响铃",
                    actionLabel = "通知设置",
                    onClick = onOpenNotificationSettings,
                )
            }
        }
        if (!canScheduleExactAlarms) {
            item {
                PermissionBanner(
                    text = "精确闹钟权限未开启，计划不会准时触发",
                    actionLabel = "闹钟设置",
                    onClick = onOpenExactAlarmSettings,
                )
            }
        }
        if (!canUseFullScreenIntent) {
            item {
                PermissionBanner(
                    text = "全屏提醒未开启，锁屏时只显示通知",
                    actionLabel = "全屏设置",
                    onClick = onOpenFullScreenSettings,
                )
            }
        }
        if (!isIgnoringBatteryOptimizations) {
            item {
                PermissionBanner(
                    text = "电池优化可能导致闹钟延迟，建议设为不受限",
                    actionLabel = "电池设置",
                    onClick = onOpenBatterySettings,
                )
            }
        }
        if (needsAutostartSetup) {
            item {
                PermissionBanner(
                    text = "请在系统管家中允许 StudyBuddy 自启动和后台运行",
                    actionLabel = "自启动设置",
                    onClick = onOpenAutostartSettings,
                )
            }
        }
        item {
            Text(
                "今日时间轴",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        if (entries.isEmpty()) {
            item {
                EmptyToday()
            }
        } else {
            itemsIndexed(entries, key = { _, entry -> entry.second.id }) { index, (planName, reminder) ->
                TimelineRow(planName, reminder, statuses[index])
            }
        }
    }
}

@Composable
private fun DateSummary(date: LocalDate, count: Int) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFFFF0DF),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.WbSunny, null, tint = StudyOrange, modifier = Modifier.size(28.dp))
                }
            }
            Column {
                Text(
                    date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (count == 0) "今天暂时没有已启用的提醒" else "今天有 $count 项安排",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PermissionBanner(
    text: String,
    actionLabel: String,
    onClick: () -> Unit,
) {
    Surface(color = Color(0xFFFFF3E5), shape = RoundedCornerShape(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.NotificationsOff, null, tint = StudyOrange)
            Text(text, modifier = Modifier.padding(start = 10.dp).weight(1f), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onClick) { Text(actionLabel) }
        }
    }
}

@Composable
private fun EmptyToday() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Alarm, null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.outline)
        Text("今天没有已启用的安排", modifier = Modifier.padding(top = 18.dp), fontWeight = FontWeight.Bold)
        Text(
            "从计划页启用计划，提醒会按时间显示在这里。",
            modifier = Modifier.padding(top = 6.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TimelineRow(planName: String, reminder: StudyReminder, status: TimelineStatus) {
    val state = when (status) {
        TimelineStatus.UPCOMING -> "未开始" to MaterialTheme.colorScheme.onSurfaceVariant
        TimelineStatus.ACTIVE -> "进行中" to StudyOrange
        TimelineStatus.REMINDED -> "已提醒" to StudyGreen
    }
    Row(verticalAlignment = Alignment.Top) {
        Text(
            reminder.timeText,
            modifier = Modifier.width(58.dp).padding(top = 18.dp),
            fontWeight = FontWeight.Bold,
        )
        Box(
            modifier = Modifier.padding(top = 22.dp).size(9.dp).background(state.second, CircleShape),
        )
        Card(
            modifier = Modifier.padding(start = 12.dp).fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = CardDefaults.outlinedCardBorder(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(reminderIcon(reminder.name), null, tint = StudyGreen)
                Column(Modifier.weight(1f)) {
                    Text(reminder.name, fontWeight = FontWeight.Bold)
                    Text(planName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(color = state.second.copy(alpha = 0.10f), shape = CircleShape) {
                    Text(state.first, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = state.second, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private fun reminderIcon(name: String): ImageVector = when {
    "餐" in name -> Icons.Default.Restaurant
    "篮球" in name || "户外" in name -> Icons.Default.SportsBasketball
    "检查" in name || "整理" in name -> Icons.Default.Checklist
    else -> Icons.Default.Book
}

@Composable
private fun PlansScreen(
    modifier: Modifier,
    viewModel: PlanViewModel,
    records: List<PlanRecord>,
    isBusy: Boolean,
    onEnablePlan: (String) -> Unit,
    onEdit: (StudyPlan) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("我的计划", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${records.size} 组", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(records, key = { it.plan.id }) { record ->
            PlanCard(viewModel, record, isBusy, onEnablePlan, onEdit)
        }
        item {
            Text(
                "启用后由 Android 系统管理闹钟，无需保持 App 打开。",
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlanCard(
    viewModel: PlanViewModel,
    record: PlanRecord,
    isBusy: Boolean,
    onEnablePlan: (String) -> Unit,
    onEdit: (StudyPlan) -> Unit,
) {
    var confirmDelete by remember(record.plan.id) { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(record.plan.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${record.plan.repeatText} · ${record.plan.reminders.size} 项",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (viewModel.isEnabled(record)) Icons.Default.Alarm else Icons.Outlined.Alarm,
                    null,
                    tint = if (viewModel.isEnabled(record)) StudyGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    viewModel.statusText(record),
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                    color = if (viewModel.isEnabled(record)) StudyGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Switch(
                    checked = viewModel.isEnabled(record),
                    onCheckedChange = { enabled ->
                        if (enabled) onEnablePlan(record.plan.id) else viewModel.setEnabled(record.plan.id, false)
                    },
                    enabled = !isBusy && !record.pendingDeletion,
                )
            }
            record.issue?.let {
                Text(it, modifier = Modifier.padding(top = 8.dp), color = StudyOrange, style = MaterialTheme.typography.bodySmall)
            }
            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onEdit(record.plan) }, enabled = !isBusy) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                    Text("编辑", modifier = Modifier.padding(start = 7.dp))
                }
                TextButton(
                    onClick = {
                        onEdit(record.plan.editableCopy(record.plan.name.take(37) + " 副本"))
                    },
                    enabled = !isBusy,
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Text("复制", modifier = Modifier.padding(start = 7.dp))
                }
                TextButton(
                    onClick = { confirmDelete = true },
                    enabled = !isBusy && !record.pendingDeletion,
                ) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                    Text("删除", modifier = Modifier.padding(start = 7.dp))
                }
                if (record.phase == com.xzygis.studybuddy.data.SyncPhase.ATTENTION) {
                    TextButton(onClick = { viewModel.retry(record.plan.id) }, enabled = !isBusy) {
                        Text("重试")
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除“${record.plan.name}”？") },
            text = { Text("删除前会取消整组系统闹钟。此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete(record.plan.id)
                    },
                    enabled = !isBusy,
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun TabletLayout(
    viewModel: PlanViewModel,
    records: List<PlanRecord>,
    isBusy: Boolean,
    hasNotificationPermission: Boolean,
    canScheduleExactAlarms: Boolean,
    canUseFullScreenIntent: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    needsAutostartSetup: Boolean,
    onEnablePlan: (String) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAutostartSettings: () -> Unit,
    onEdit: (StudyPlan) -> Unit,
    snackbar: SnackbarHostState,
) {
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
            NavigationRail(
                header = {
                    FloatingActionButton(
                        onClick = { onEdit(StudyPlan.draft()) },
                        modifier = Modifier.padding(vertical = 12.dp),
                        containerColor = StudyGreen,
                        contentColor = Color.White,
                    ) { Icon(Icons.Default.Add, "新建计划") }
                },
            ) {
                NavigationRailItem(selected = true, onClick = {}, icon = { Icon(Icons.Default.CalendarMonth, null) }, label = { Text("今天") })
                NavigationRailItem(selected = false, onClick = {}, icon = { Icon(Icons.Default.GridView, null) }, label = { Text("计划") })
            }
            Surface(modifier = Modifier.width(320.dp).fillMaxHeight(), color = Color(0xFFF0F3F2)) {
                PlansScreen(
                    modifier = Modifier,
                    viewModel = viewModel,
                    records = records,
                    isBusy = isBusy,
                    onEnablePlan = onEnablePlan,
                    onEdit = onEdit,
                )
            }
            TodayScreen(
                modifier = Modifier.weight(1f),
                viewModel = viewModel,
                records = records,
                hasNotificationPermission = hasNotificationPermission,
                canScheduleExactAlarms = canScheduleExactAlarms,
                canUseFullScreenIntent = canUseFullScreenIntent,
                isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                needsAutostartSetup = needsAutostartSetup,
                onOpenNotificationSettings = onOpenNotificationSettings,
                onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                onOpenFullScreenSettings = onOpenFullScreenSettings,
                onOpenBatterySettings = onOpenBatterySettings,
                onOpenAutostartSettings = onOpenAutostartSettings,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PlanEditorScreen(
    plan: StudyPlan,
    isExisting: Boolean,
    isBusy: Boolean,
    onBack: () -> Unit,
    onSave: (StudyPlan) -> Unit,
    onDelete: () -> Unit,
) {
    var draft by remember(plan.id) { mutableStateOf(plan) }
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = AppBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (isExisting) "编辑计划" else "新建计划", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = {
                    TextButton(
                        onClick = { onSave(draft) },
                        enabled = !isBusy && draft.validationMessage() == null,
                    ) { Text("保存", fontWeight = FontWeight.Bold) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                Column {
                    Text("计划名称", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { draft = draft.copy(name = it) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true,
                        placeholder = { Text("例如：上学日、周末") },
                    )
                }
            }
            item {
                Column {
                    Text("整组重复", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = { draft = draft.copy(weekdays = Weekday.entries.toSet()) }) { Text("每天") }
                        OutlinedButton(onClick = { draft = draft.copy(weekdays = Weekday.schoolDays) }) { Text("周一至五") }
                        OutlinedButton(onClick = { draft = draft.copy(weekdays = setOf(Weekday.SATURDAY, Weekday.SUNDAY)) }) { Text("周末") }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Weekday.entries.forEach { day ->
                            val selected = day in draft.weekdays
                            Surface(
                                modifier = Modifier.weight(1f).height(44.dp).clickable {
                                    draft = draft.copy(
                                        weekdays = if (selected) draft.weekdays - day else draft.weekdays + day,
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) StudyGreen else Color.White,
                                border = if (selected) null else CardDefaults.outlinedCardBorder(),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(day.shortName, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Text(
                        if (draft.weekdays.isEmpty()) "请至少选择一天。" else "${draft.repeatText}重复，应用于下面所有提醒。",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                RingtoneSelector(
                    ringtoneUri = draft.ringtoneUri,
                    onSelected = { draft = draft.copy(ringtoneUri = it) },
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("提醒事项", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.weight(1f))
                    Text("${draft.reminders.size} 项", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(draft.reminders, key = { it.id }) { reminder ->
                ReminderEditor(
                    reminder = reminder,
                    onChange = { changed ->
                        draft = draft.copy(reminders = draft.reminders.map { if (it.id == changed.id) changed else it })
                    },
                    onDelete = {
                        draft = draft.copy(reminders = draft.reminders.filterNot { it.id == reminder.id })
                    },
                )
            }
            item {
                OutlinedButton(
                    onClick = {
                        draft = draft.copy(
                            reminders = draft.reminders + StudyReminder(name = "", hour = 9, minute = 0),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Icon(Icons.Default.Add, null)
                    Text("添加提醒", modifier = Modifier.padding(start = 8.dp))
                }
            }
            draft.validationMessage()?.let { message ->
                item {
                    Text(message, color = StudyOrange, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (isExisting) {
                item {
                    TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Delete, null)
                        Text("删除计划", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除“${draft.name}”？") },
            text = { Text("删除前会取消整组系统闹钟。此操作无法撤销。") },
            confirmButton = { TextButton(onClick = onDelete) { Text("删除") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun RingtoneSelector(
    ringtoneUri: String?,
    onSelected: (String?) -> Unit,
) {
    val context = LocalContext.current
    val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
    val ringtoneTitle = remember(ringtoneUri) {
        ringtoneUri
            ?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?.let { uri ->
                runCatching {
                    RingtoneManager.getRingtone(context, uri)?.getTitle(context)
                }.getOrNull()
            }
            ?: "系统默认闹钟铃声"
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val selected = result.data?.selectedRingtoneUri() ?: return@rememberLauncherForActivityResult
        val grantFlags = result.data?.flags?.and(Intent.FLAG_GRANT_READ_URI_PERMISSION) ?: 0
        if (grantFlags != 0) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(selected, grantFlags)
            }
        }
        onSelected(if (selected == defaultUri) null else selected.toString())
    }

    Column {
        Text("闹钟铃声", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Alarm, null, tint = StudyGreen)
            Column(Modifier.weight(1f)) {
                Text(ringtoneTitle, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "计划内所有提醒使用此铃声",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ringtoneUri != null) {
                TextButton(onClick = { onSelected(null) }) {
                    Text("默认")
                }
            }
            OutlinedButton(
                onClick = {
                    launcher.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            putExtra(
                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                ringtoneUri?.let(Uri::parse) ?: defaultUri,
                            )
                        },
                    )
                },
            ) {
                Text("选择")
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun Intent.selectedRingtoneUri(): Uri? =
    getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)

@Composable
private fun ReminderEditor(
    reminder: StudyReminder,
    onChange: (StudyReminder) -> Unit,
    onDelete: () -> Unit,
) {
    var editingTime by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = reminder.name,
                    onValueChange = { onChange(reminder.copy(name = it)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("科目或提醒名称") },
                )
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除提醒") }
            }
            ListItem(
                headlineContent = { Text("开始时间") },
                trailingContent = {
                    TextButton(
                        onClick = { editingTime = true },
                    ) {
                        Text(reminder.timeText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                },
            )
        }
    }
    if (editingTime) {
        NumericTimeDialog(
            initialHour = reminder.hour,
            initialMinute = reminder.minute,
            onDismiss = { editingTime = false },
            onConfirm = { hour, minute ->
                onChange(reminder.copy(hour = hour, minute = minute))
                editingTime = false
            },
        )
    }
}

@Composable
private fun NumericTimeDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    var hourText by remember(initialHour) { mutableStateOf(initialHour.toString().padStart(2, '0')) }
    var minuteText by remember(initialMinute) { mutableStateOf(initialMinute.toString().padStart(2, '0')) }
    val hour = hourText.toIntOrNull()
    val minute = minuteText.toIntOrNull()
    val valid = hour != null && hour in 0..23 && minute != null && minute in 0..59

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置时间") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = hourText,
                    onValueChange = { value ->
                        if (value.length <= 2 && value.all(Char::isDigit)) hourText = value
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("小时") },
                    placeholder = { Text("00") },
                    singleLine = true,
                    isError = hour == null || hour !in 0..23,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(":", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = minuteText,
                    onValueChange = { value ->
                        if (value.length <= 2 && value.all(Char::isDigit)) minuteText = value
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("分钟") },
                    placeholder = { Text("00") },
                    singleLine = true,
                    isError = minute == null || minute !in 0..59,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(requireNotNull(hour), requireNotNull(minute)) },
            ) {
                Text("确定")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
