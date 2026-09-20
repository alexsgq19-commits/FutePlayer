package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.PaymentRecord
import com.example.ui.theme.StadiumAccentYellow
import com.example.ui.theme.StadiumCyanSecondary
import com.example.ui.theme.StadiumGreenPrimary
import java.util.Locale

enum class PaymentFilter(val label: String) {
    ALL("TODOS"),
    PAID("PAGOS"),
    PENDING("PENDENTES"),
    CANCELLED("CANCELADOS"),
    EXPIRED("EXPIRADOS")
}

val PAYMENT_STATUS_OPTIONS = listOf(
    "PAID" to "Pago / Aprovado",
    "PENDING" to "Pendente",
    "CANCELLED" to "Cancelado",
    "EXPIRED" to "Expirado",
    "REFUNDED" to "Reembolsado"
)

val PAYMENT_METHOD_OPTIONS = listOf(
    "PIX",
    "CARTÃO",
    "BOLETO",
    "DINHEIRO",
    "TRANSFERÊNCIA",
    "OUTRO"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentHistoryDialog(
    payments: List<PaymentRecord>,
    isAdmin: Boolean = false,
    onUpdatePayment: ((paymentId: String, userName: String, amountCents: Long, captureMethod: String, status: String, receiptUrl: String, onComplete: (Boolean, String) -> Unit) -> Unit)? = null,
    onChangePaymentStatus: ((paymentId: String, newStatus: String, onComplete: (Boolean, String) -> Unit) -> Unit)? = null,
    onDeletePayment: ((paymentId: String, onComplete: (Boolean, String) -> Unit) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf(PaymentFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    var paymentToEdit by remember { mutableStateOf<PaymentRecord?>(null) }
    var paymentToDelete by remember { mutableStateOf<PaymentRecord?>(null) }
    var paymentToChangeStatus by remember { mutableStateOf<PaymentRecord?>(null) }

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
                    payment.uid.contains(searchQuery, ignoreCase = true) ||
                    payment.captureMethod.contains(searchQuery, ignoreCase = true)

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
                .fillMaxHeight(0.90f),
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
                        Column {
                            Text(
                                text = "Histórico de Pagamentos",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (isAdmin) {
                                Text(
                                    text = "Painel Admin • Editar, alterar status e excluir registros",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StadiumCyanSecondary
                                )
                            }
                        }
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
                    placeholder = { Text("Buscar por usuário, NSU, método...", fontSize = 13.sp) },
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

                Spacer(modifier = Modifier.height(10.dp))

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
                        items(filteredPayments, key = { it.getDocId() }) { payment ->
                            PaymentCard(
                                payment = payment,
                                isAdmin = isAdmin,
                                onOpenReceipt = { receiptUrl ->
                                    if (receiptUrl.isNotBlank()) {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(receiptUrl))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            Toast.makeText(context, "Não foi possível abrir o link do comprovante", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onEdit = { paymentToEdit = payment },
                                onChangeStatus = { paymentToChangeStatus = payment },
                                onDelete = { paymentToDelete = payment }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal: Editar Pagamento
    paymentToEdit?.let { payment ->
        EditPaymentDialog(
            payment = payment,
            onDismiss = { paymentToEdit = null },
            onSave = { updatedName, updatedAmountCents, updatedMethod, updatedStatus, updatedReceipt ->
                val targetId = payment.getDocId()
                if (onUpdatePayment != null) {
                    onUpdatePayment(targetId, updatedName, updatedAmountCents, updatedMethod, updatedStatus, updatedReceipt) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (success) paymentToEdit = null
                    }
                } else {
                    Toast.makeText(context, "Ação não suportada", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Modal: Alterar Status Rápido
    paymentToChangeStatus?.let { payment ->
        ChangePaymentStatusDialog(
            currentStatus = payment.status,
            payment = payment,
            onDismiss = { paymentToChangeStatus = null },
            onSelectStatus = { newStatus ->
                val targetId = payment.getDocId()
                if (onChangePaymentStatus != null) {
                    onChangePaymentStatus(targetId, newStatus) { success, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        if (success) paymentToChangeStatus = null
                    }
                }
            }
        )
    }

    // Modal: Confirmar Exclusão de Pagamento
    paymentToDelete?.let { payment ->
        AlertDialog(
            onDismissRequest = { paymentToDelete = null },
            icon = {
                Icon(
                    Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Excluir Pagamento?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Tem certeza de que deseja excluir permanentemente este registro de pagamento do Firestore?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Usuário: ${payment.userName.ifBlank { payment.uid }}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Valor: ${payment.getFormattedAmount()}", color = StadiumGreenPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Status: ${payment.status.uppercase()}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("ID: ${payment.getDocId()}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        text = "Esta ação é irreversível.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetId = payment.getDocId()
                        if (onDeletePayment != null) {
                            onDeletePayment(targetId) { success, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (success) paymentToDelete = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Excluir", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { paymentToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun PaymentCard(
    payment: PaymentRecord,
    isAdmin: Boolean,
    onOpenReceipt: (String) -> Unit,
    onEdit: () -> Unit = {},
    onChangeStatus: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val statusColor = when (payment.status.uppercase()) {
        "PAID", "APPROVED" -> StadiumGreenPrimary
        "PENDING" -> StadiumAccentYellow
        "CANCELLED", "REFUNDED" -> Color(0xFFFF9800)
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
                Column(modifier = Modifier.weight(1f)) {
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
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable(enabled = isAdmin) {
                        if (isAdmin) onChangeStatus()
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = payment.status.uppercase(),
                            color = statusColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (isAdmin) {
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Alterar Status",
                                tint = statusColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
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
                        text = "Forma: ${payment.getFormattedCaptureMethod()}",
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

            // Ações: Comprovante e/ou Ações Administrativas (Editar, Alterar Status, Excluir)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (payment.receiptUrl.isNotBlank()) {
                    TextButton(
                        onClick = { onOpenReceipt(payment.receiptUrl) },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ver Comprovante", fontSize = 12.sp, color = StadiumGreenPrimary)
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (isAdmin) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Botão Alterar Status
                        IconButton(
                            onClick = onChangeStatus,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.PublishedWithChanges,
                                contentDescription = "Alterar Status",
                                tint = StadiumCyanSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Botão Editar
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Editar Pagamento",
                                tint = StadiumGreenPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Botão Excluir
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Excluir Pagamento",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditPaymentDialog(
    payment: PaymentRecord,
    onDismiss: () -> Unit,
    onSave: (userName: String, amountCents: Long, method: String, status: String, receiptUrl: String) -> Unit
) {
    var userNameInput by remember { mutableStateOf(payment.userName) }
    var amountReaisInput by remember {
        val initialReais = (if (payment.paidAmount > 0L) payment.paidAmount else payment.amount) / 100.0
        mutableStateOf(String.format(Locale.US, "%.2f", initialReais))
    }
    var methodInput by remember { mutableStateOf(payment.getFormattedCaptureMethod()) }
    var statusInput by remember { mutableStateOf(payment.status.uppercase()) }
    var receiptUrlInput by remember { mutableStateOf(payment.receiptUrl) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = StadiumGreenPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Editar Pagamento", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = userNameInput,
                    onValueChange = { userNameInput = it },
                    label = { Text("Nome do Usuário / Identificação") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountReaisInput,
                    onValueChange = { amountReaisInput = it },
                    label = { Text("Valor Pago (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Seleção de Forma de Pagamento
                Text("Forma de Pagamento:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(PAYMENT_METHOD_OPTIONS) { method ->
                        FilterChip(
                            selected = methodInput.equals(method, ignoreCase = true),
                            onClick = { methodInput = method },
                            label = { Text(method, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StadiumGreenPrimary,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }

                // Seleção de Status
                Text("Status do Pagamento:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(PAYMENT_STATUS_OPTIONS) { (key, label) ->
                        FilterChip(
                            selected = statusInput.equals(key, ignoreCase = true),
                            onClick = { statusInput = key },
                            label = { Text(key, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = StadiumCyanSecondary,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = receiptUrlInput,
                    onValueChange = { receiptUrlInput = it },
                    label = { Text("URL do Comprovante (Opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val reaisClean = amountReaisInput.replace(",", ".").trim()
                    val parsedReais = reaisClean.toDoubleOrNull()
                    if (parsedReais == null || parsedReais < 0.0) {
                        errorMessage = "Informe um valor monetário válido."
                        return@Button
                    }
                    val amountCents = (parsedReais * 100).toLong()
                    onSave(userNameInput, amountCents, methodInput, statusInput, receiptUrlInput)
                },
                colors = ButtonDefaults.buttonColors(containerColor = StadiumGreenPrimary, contentColor = Color.Black)
            ) {
                Text("Salvar Alterações", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun ChangePaymentStatusDialog(
    currentStatus: String,
    payment: PaymentRecord,
    onDismiss: () -> Unit,
    onSelectStatus: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PublishedWithChanges, contentDescription = null, tint = StadiumCyanSecondary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Alterar Status", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Selecione o novo status para o pagamento do usuário ${payment.userName.ifBlank { payment.uid }}:",
                    style = MaterialTheme.typography.bodySmall
                )
                PAYMENT_STATUS_OPTIONS.forEach { (key, desc) ->
                    val isSelected = currentStatus.equals(key, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) StadiumCyanSecondary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) StadiumCyanSecondary else Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectStatus(key) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(key, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = StadiumCyanSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}

