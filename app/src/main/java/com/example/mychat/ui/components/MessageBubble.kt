package com.example.mychat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.mychat.data.model.Message
import com.example.mychat.data.model.MessageStatus
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageBubble(
    message: Message,
    isFromCurrentUser: Boolean,
    modifier: Modifier = Modifier
) {
    val alignment = if (isFromCurrentUser) Alignment.End else Alignment.Start
    val backgroundColor = if (isFromCurrentUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (isFromCurrentUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier,
        horizontalAlignment = alignment
    ) {
        Surface(
            color = backgroundColor,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .widthIn(max = 280.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message.content,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = formatTimestamp(message.timestamp),
                        color = textColor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Message status indicator
                    val statusIcon = when (message.status) {
                        MessageStatus.SENT -> "✓"
                        MessageStatus.DELIVERED -> "✓✓"
                    }

                    Text(
                        text = statusIcon,
                        color = textColor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val messageTime = Date(timestamp)
    val today = Date(now)
    val yesterday = Date(now - 24 * 60 * 60 * 1000)

    val messageCalendar = java.util.Calendar.getInstance().apply { time = messageTime }
    val todayCalendar = java.util.Calendar.getInstance().apply { time = today }
    val yesterdayCalendar = java.util.Calendar.getInstance().apply { time = yesterday }

    return when {
        // Today - show time only
        messageCalendar.get(java.util.Calendar.YEAR) == todayCalendar.get(java.util.Calendar.YEAR) &&
        messageCalendar.get(java.util.Calendar.DAY_OF_YEAR) == todayCalendar.get(java.util.Calendar.DAY_OF_YEAR) -> {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(messageTime)
        }
        // Yesterday - show "Yesterday"
        messageCalendar.get(java.util.Calendar.YEAR) == yesterdayCalendar.get(java.util.Calendar.YEAR) &&
        messageCalendar.get(java.util.Calendar.DAY_OF_YEAR) == yesterdayCalendar.get(java.util.Calendar.DAY_OF_YEAR) -> {
            "Yesterday ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(messageTime)}"
        }
        // Within this week - show day name
        now - timestamp < 7 * 24 * 60 * 60 * 1000 -> {
            "${SimpleDateFormat("EEE", Locale.getDefault()).format(messageTime)} ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(messageTime)}"
        }
        // Older - show date and time
        else -> {
            SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(messageTime)
        }
    }
}
