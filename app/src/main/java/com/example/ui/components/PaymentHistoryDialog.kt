package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.PaymentRecord
import com.example.ui.theme.StadiumAccentYellow
import com.example.ui.theme.StadiumGreenPrimary

enum class PaymentFilter(val label: String) {
    ALL("TODOS"),
    PAID("PAGOS"),
    PENDING("PENDENTES"),
    CANCELLED("CANCELADOS"),
    EXPIRED("EXPIRADOS")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentHistoryDialog(
    payments: List<PaymentRecord>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf(PaymentFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredPayments = remember(payments, selectedFilter, searchQuery) {
        payments.filter { payment ->
            val matchesFilter = when (selectedFilter) {
                PaymentFilter.ALL -> true
                PaymentFilter.PAID -> payment.status.equals("PAID", ignoreCase = true)
                PaymentFilter.PENDING -> payment.status.equals("PENDING", ignoreCase = true)
                PaymentFilter.CANCELLED -> payment.status.equals("CANCELLED", ignoreCase = true)
                PaymentFilter.EXPIRED -> payment.status.equals("EXPIRED", ignoreCase = true)
            }
            val matchesSearch = searchQuery.isBlank() ||
                    payment.userName.contains(searchQuery, ignoreCase = true) ||
                    payment.orderNsu.contains(searchQuery, ignoreCase = true) ||
                    payment.transactionNsu.contains(searchQuery, ignoreCase = true) ||
                    payment.uid.contains(searchQuery, ignoreCase = true)

            matchesFilter && matchesSearch
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = StadiumGreenPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Histórico de Pagamentos",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Campo de Busca
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar por usuário, NSU ou transação...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpar")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Filtros (TODOS, PAGOS, PENDENTES, CANCELADOS, EXPIRADOS)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(PaymentFilter.values()) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StadiumGreenPrimary,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Lista de Pagamentos
                if (filteredPayments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Payments,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Nenhum pagamento encontrado",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredPayments, key = { it.transactionNsu.ifBlank { it.orderNsu } }) { payment ->
                            PaymentCard(payment = payment) { receiptUrl ->
                                if (receiptUrl.isNotBlank()) {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(receiptUrl))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentCard(
    payment: PaymentRecord,
    onOpenReceipt: (String) -> Unit
) {
    val isPaid = payment.status.equals("PAID", ignoreCase = true)
    val statusColor = when (payment.status.uppercase()) {
        "PAID", "APPROVED" -> StadiumGreenPrimary
        "PENDING" -> StadiumAccentYellow
        else -> MaterialTheme.colorScheme.error
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = payment.userName.ifBlank { "Usuário: ${payment.uid.take(8)}" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Data: ${payment.getFormattedPaidAt()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = payment.status.uppercase(),
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Valor: ${payment.getFormattedAmount()}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = StadiumGreenPrimary
                    )
                    Text(
                        text = "Forma: ${payment.captureMethod.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "NSU: ${payment.orderNsu.takeLast(12)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (payment.transactionNsu.isNotBlank()) {
                        Text(
                            text = "Tx: ${payment.transactionNsu.takeLast(10)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (payment.receiptUrl.isNotBlank()) {
                TextButton(
                    onClick = { onOpenReceipt(payment.receiptUrl) },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ver Comprovante", fontSize = 12.sp, color = StadiumGreenPrimary)
                }
            }
        }
    }
}
