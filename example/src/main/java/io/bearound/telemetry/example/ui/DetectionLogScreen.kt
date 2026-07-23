package io.bearound.telemetry.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.bearound.telemetry.example.DetectionLogEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class LogViewMode(val label: String) {
    DETAIL("Detalhado"),
    GROUPED("Por Minuto"),
}

enum class LogModeFilter(val label: String) {
    ALL("Tudo"),
    FOREGROUND("FG"),
    BACKGROUND("BG"),
}

enum class LogTypeFilter(val label: String) {
    ALL("Tudo"),
    BEAD("BEAD"),
    IBEACON("iBeacon"),
}

private data class MinuteGroup(
    val date: Date,
    val total: Int,
    val fgCount: Int,
    val bgCount: Int,
    val uniqueBeacons: Int,
)

/**
 * Detection log — mirrors the BearoundScan log tab: per-detection entries plus a
 * per-minute view showing HOW MANY detections the device observed (total, FG/BG,
 * unique beacons). Telemetry flavor: no proximity/positioning fields.
 */
@Composable
fun DetectionLogScreen(
    foregroundLog: List<DetectionLogEntry>,
    backgroundLog: List<DetectionLogEntry>,
    onClear: () -> Unit,
    paddingValues: PaddingValues,
) {
    var viewMode by remember { mutableStateOf(LogViewMode.DETAIL) }
    var modeFilter by remember { mutableStateOf(LogModeFilter.ALL) }
    var typeFilter by remember { mutableStateOf(LogTypeFilter.ALL) }

    val sourceLog = when (modeFilter) {
        LogModeFilter.ALL -> (foregroundLog + backgroundLog).sortedByDescending { it.timestamp }
        LogModeFilter.FOREGROUND -> foregroundLog
        LogModeFilter.BACKGROUND -> backgroundLog
    }

    val filteredLog = sourceLog.filter { entry ->
        when (typeFilter) {
            LogTypeFilter.ALL -> true
            LogTypeFilter.BEAD -> entry.discoverySource == "BEAD"
            LogTypeFilter.IBEACON -> entry.discoverySource == "iBeacon"
        }
    }

    val groupedByMinute = remember(filteredLog) {
        val calendar = Calendar.getInstance()
        filteredLog.groupBy { entry ->
            calendar.time = entry.timestamp
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.timeInMillis
        }.map { (millis, entries) ->
            MinuteGroup(
                date = Date(millis),
                total = entries.size,
                fgCount = entries.count { !it.isBackground },
                bgCount = entries.count { it.isBackground },
                uniqueBeacons = entries.map { "${it.major}.${it.minor}" }.toSet().size,
            )
        }.sortedByDescending { it.date }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                LogViewMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = viewMode == mode,
                        onClick = { viewMode = mode },
                        shape = SegmentedButtonDefaults.itemShape(index, LogViewMode.entries.size),
                    ) { Text(mode.label, fontSize = 12.sp) }
                }
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                LogModeFilter.entries.forEachIndexed { index, filter ->
                    SegmentedButton(
                        selected = modeFilter == filter,
                        onClick = { modeFilter = filter },
                        shape = SegmentedButtonDefaults.itemShape(index, LogModeFilter.entries.size),
                    ) { Text(filter.label, fontSize = 12.sp) }
                }
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                LogTypeFilter.entries.forEachIndexed { index, filter ->
                    SegmentedButton(
                        selected = typeFilter == filter,
                        onClick = { typeFilter = filter },
                        shape = SegmentedButtonDefaults.itemShape(index, LogTypeFilter.entries.size),
                    ) { Text(filter.label, fontSize = 12.sp) }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "FG:${foregroundLog.size} BG:${backgroundLog.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = onClear,
                    enabled = foregroundLog.isNotEmpty() || backgroundLog.isNotEmpty(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Limpar", fontSize = 12.sp)
                }
            }
        }

        if (filteredLog.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.ListAlt,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Nenhuma detecção registrada",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else if (viewMode == LogViewMode.GROUPED) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(groupedByMinute, key = { it.date.time }) { group ->
                    MinuteGroupRow(group)
                    HorizontalDivider()
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(filteredLog, key = { it.id }) { entry ->
                    LogEntryRow(entry)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun MinuteGroupRow(group: MinuteGroup) {
    val dateFormat = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    Column(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = dateFormat.format(group.date),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${group.total} detecções",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (group.fgCount > 0) BadgeCount("FG", group.fgCount, Color(0xFF4CAF50))
                if (group.bgCount > 0) BadgeCount("BG", group.bgCount, Color(0xFFFF9800))
            }
            Text(
                text = "${group.uniqueBeacons} beacon${if (group.uniqueBeacons == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BadgeCount(label: String, count: Int, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier
                .background(color, RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LogEntryRow(entry: DetectionLogEntry) {
    val dateFormat = remember { SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault()) }
    Column(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${entry.major}.${entry.minor}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = dateFormat.format(entry.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "RSSI: ${entry.rssi}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (entry.discoverySource) {
                "BEAD" -> LogBadge("BEAD", Color(0xFF9C27B0))
                else -> LogBadge("iB", Color(0xFF3F51B5))
            }
            if (entry.isBackground) LogBadge("BG", Color(0xFFFF9800))
            else LogBadge("FG", Color(0xFF4CAF50))
        }
    }
}

@Composable
private fun LogBadge(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = Color.White,
        modifier = Modifier
            .background(color, RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}
