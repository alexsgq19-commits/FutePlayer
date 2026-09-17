package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.PaymentOrder
import com.example.data.models.User
import com.example.ui.theme.StadiumAccentYellow
import com.example.ui.theme.StadiumGreenPrimary

sealed interface SubscriptionFlowState {
    data object Idle : SubscriptionFlowState
    data object CreatingCheckout : SubscriptionFlowState
    data class AwaitingPayment(val order: PaymentOrder) : SubscriptionFlowState
    data class Success(val message: String) : SubscriptionFlowState
    data class Error(val message: String) : SubscriptionFlowState
}

@Composable
fun SubscriptionDialog(
    user: User?,
    flowState: SubscriptionFlowState,
    onStartPayment: () -> Unit,
    onDismiss: () -> Unit,
    onCheckStatus: () -> Unit,
    onSimulateAdminApproval: ((String) -> Unit)? = null // Apenas se usuário for ADMIN testando
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = {
            if (flowState !is SubscriptionFlowState.CreatingCheckout) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = flowState !is SubscriptionFlowState.CreatingCheckout,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, StadiumGreenPrimary.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (flowState) {
                    is SubscriptionFlowState.Idle -> {
                        // Ícone de cabeçalho
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            StadiumGreenPrimary.copy(alpha = 0.25f),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockClock,
                                contentDescription = null,
                                tint = StadiumAccentYellow,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        Text(
                            text = "ASSINATURA VENCIDA",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "Seu período de acesso terminou. Para continuar assistindo aos canais, jogos e filmes no FutePlayer, renove sua assinatura mensal.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Card de Valor e Benefícios
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, StadiumGreenPrimary.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Valor da Renovação:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = "R$ 10,00",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = StadiumGreenPrimary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "/ 30 dias",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = StadiumGreenPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "PIX ou Cartão via InfinitePay",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = StadiumGreenPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Sem cobrança recorrente no cartão",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = StadiumGreenPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Liberação automática após confirmação",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        // Botão Principal
                        Button(
                            onClick = onStartPayment,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StadiumGreenPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Icon(Icons.Default.Payment, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "RENOVAR POR R$ 10,00",
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                fontSize = 15.sp
                            )
                        }

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Fechar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    is SubscriptionFlowState.CreatingCheckout -> {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(StadiumGreenPrimary.copy(alpha = 0.15f), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = StadiumGreenPrimary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Text(
                            text = "Gerando pagamento...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Estamos preparando seu link seguro na InfinitePay. Aguarde alguns instantes...",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is SubscriptionFlowState.AwaitingPayment -> {
                        val order = flowState.order

                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(StadiumAccentYellow.copy(alpha = 0.2f), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = StadiumAccentYellow,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        Text(
                            text = "Aguardando confirmação do Webhook...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "O checkout da InfinitePay foi aberto. Conclua o pagamento via PIX ou Cartão.\n\nO acesso é liberado exclusivamente após o Webhook oficial da InfinitePay validar a transação e creditar +30 dias no Firebase.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Código do Pedido:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(order.orderNsu, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text("Valor: R$ 10,00", style = MaterialTheme.typography.labelSmall, color = StadiumGreenPrimary, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Botão para reabrir link se fechou
                        if (order.checkoutUrl.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(order.checkoutUrl))
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reabrir Link de Pagamento")
                            }
                        }

                        // Botão para verificar status manualmente
                        Button(
                            onClick = onCheckStatus,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StadiumGreenPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Verificar Status", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        // Se usuário for Admin, opção para simular o recebimento do Webhook para homologação
                        if (user?.role == "ADMIN" && onSimulateAdminApproval != null) {
                            TextButton(
                                onClick = { onSimulateAdminApproval(order.orderNsu) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Simular Webhook InfinitePay (Admin Test)", fontSize = 12.sp)
                            }
                        }

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Fechar e aguardar em segundo plano")
                        }
                    }

                    is SubscriptionFlowState.Success -> {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(StadiumGreenPrimary.copy(alpha = 0.2f), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = StadiumGreenPrimary,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Text(
                            text = "PAGAMENTO APROVADO!",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = StadiumGreenPrimary
                        )

                        Text(
                            text = flowState.message.ifBlank { "Sua assinatura foi renovada com sucesso! Seu acesso foi estendido por 30 dias." },
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StadiumGreenPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Text("Acessar FutePlayer", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }

                    is SubscriptionFlowState.Error -> {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f), shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        Text(
                            text = "Falha no Pagamento",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )

                        Text(
                            text = flowState.message,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Button(
                            onClick = onStartPayment,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = StadiumGreenPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Tentar Novamente", color = Color.Black, fontWeight = FontWeight.Bold)
                        }

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Fechar")
                        }
                    }
                }
            }
        }
    }
}
