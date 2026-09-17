package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.models.User
import com.example.ui.theme.StadiumGreenPrimary

@Composable
fun AccountDialog(
    user: User,
    allUsers: List<User> = emptyList(),
    isLiveNotificationsEnabled: Boolean,
    onToggleLiveNotifications: () -> Unit,
    onDismiss: () -> Unit,
    onOpenUserManagement: () -> Unit,
    onChangePassword: (newPassword: String, onResult: (Boolean, String?) -> Unit) -> Unit = { _, _ -> },
    onRenewSubscription: () -> Unit = {},
    onOpenPaymentHistory: () -> Unit = {},
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val isAdmin = user.role == "ADMIN" || user.cpf == "06462555505"
    val onlineCount = allUsers.count { it.isCurrentlyOnline() }

    var showChangePasswordDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .imePadding(),
        title = { Text("Minha Conta", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = user.name.ifBlank { "Usuário" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (user.cpf.isNotBlank()) {
                    Text(
                        text = "CPF: ${user.cpf}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (user.phone.isNotBlank()) {
                    Text(
                        text = "Celular: ${user.phone}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Perfil: ${user.role}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StadiumGreenPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                // Status da Assinatura
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val isExempt = user.isBillingExempt || !user.isBillingEnabled
                        val isExpired = !user.canAccessPremiumContent()
                        val isUserAdmin = user.role == "ADMIN"

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Payments,
                                contentDescription = null,
                                tint = if (isUserAdmin || isExempt) StadiumGreenPrimary
                                else if (isExpired) Color(0xFFFF1744)
                                else StadiumGreenPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isUserAdmin) "Assinatura: Admin (Acesso Total)"
                                else if (isExempt) "Assinatura: Isento (Acesso Total)"
                                else if (isExpired) "Assinatura: VENCIDA"
                                else "Assinatura: ATIVA",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isUserAdmin || isExempt) StadiumGreenPrimary
                                else if (isExpired) Color(0xFFFF1744)
                                else StadiumGreenPrimary
                            )
                        }

                        if (!isUserAdmin && !isExempt) {
                            Text(
                                text = "Vencimento: ${user.getFormattedExpirationDate()} • R$ 10,00/30 dias",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Button(
                                onClick = {
                                    onDismiss()
                                    onRenewSubscription()
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = StadiumGreenPrimary),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Icon(Icons.Default.Payment, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("RENOVAR POR R$ 10,00", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        TextButton(
                            onClick = {
                                onDismiss()
                                onOpenPaymentHistory()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ver Histórico de Pagamentos", fontSize = 12.sp)
                        }
                    }
                }

                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // Botão para alterar a própria senha
                OutlinedButton(
                    onClick = { showChangePasswordDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StadiumGreenPrimary)
                ) {
                    Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Alterar Minha Senha", fontWeight = FontWeight.SemiBold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Notificações de Jogos ao Vivo",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = isLiveNotificationsEnabled,
                        onCheckedChange = { onToggleLiveNotifications() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = StadiumGreenPrimary,
                            checkedTrackColor = StadiumGreenPrimary.copy(alpha = 0.5f)
                        )
                    )
                }

                if (isAdmin) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = StadiumGreenPrimary.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(StadiumGreenPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$onlineCount usuário(s) online agora",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = StadiumGreenPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            onDismiss()
                            onOpenUserManagement()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = StadiumGreenPrimary)
                    ) {
                        Icon(Icons.Default.SupervisorAccount, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (onlineCount > 0) "Gestão de Usuários ($onlineCount Online)" else "Gestão de Usuários",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    onLogout()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Sair (Logout)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )

    // Modal de Alteração de Senha
    if (showChangePasswordDialog) {
        var currentPasswordInput by remember { mutableStateOf("") }
        var newPasswordInput by remember { mutableStateOf("") }
        var confirmPasswordInput by remember { mutableStateOf("") }
        var passwordError by remember { mutableStateOf<String?>(null) }
        var isSubmitting by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                if (!isSubmitting) showChangePasswordDialog = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .imePadding(),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = StadiumGreenPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Alterar Minha Senha", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Validação de senha atual se já houver senha cadastrada
                    if (user.password.isNotBlank()) {
                        OutlinedTextField(
                            value = currentPasswordInput,
                            onValueChange = {
                                currentPasswordInput = it
                                passwordError = null
                            },
                            label = { Text("Senha Atual *") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = newPasswordInput,
                        onValueChange = {
                            newPasswordInput = it
                            passwordError = null
                        },
                        label = { Text("Nova Senha *") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = confirmPasswordInput,
                        onValueChange = {
                            confirmPasswordInput = it
                            passwordError = null
                        },
                        label = { Text("Confirmar Nova Senha *") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (passwordError != null) {
                        Text(
                            text = passwordError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (user.password.isNotBlank() && currentPasswordInput.trim() != user.password.trim()) {
                            passwordError = "Senha atual incorreta."
                            return@Button
                        }
                        val cleanNew = newPasswordInput.trim()
                        if (cleanNew.isBlank()) {
                            passwordError = "Informe a nova senha."
                            return@Button
                        }
                        if (cleanNew.length < 4) {
                            passwordError = "A nova senha deve ter no mínimo 4 dígitos."
                            return@Button
                        }
                        if (cleanNew != confirmPasswordInput.trim()) {
                            passwordError = "As senhas não coincidem."
                            return@Button
                        }

                        isSubmitting = true
                        passwordError = null
                        onChangePassword(cleanNew) { success, err ->
                            isSubmitting = false
                            if (success) {
                                showChangePasswordDialog = false
                                Toast.makeText(context, "Senha alterada com sucesso!", Toast.LENGTH_SHORT).show()
                            } else {
                                passwordError = err ?: "Erro ao atualizar senha."
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StadiumGreenPrimary),
                    enabled = !isSubmitting
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Salvar Senha")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showChangePasswordDialog = false },
                    enabled = !isSubmitting
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}
