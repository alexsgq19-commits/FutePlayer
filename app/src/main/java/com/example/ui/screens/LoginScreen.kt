package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.StadiumGreenPrimary

@Composable
fun LoginScreen(
    isRegistrationEnabled: Boolean = true,
    noticeMessage: String? = null,
    onClearNotice: () -> Unit = {},
    onLogin: (String, String, Boolean, (Boolean, String?) -> Unit) -> Unit,
    onRegister: (String, String, String, String, (Boolean, String?) -> Unit) -> Unit = { _, _, _, _, _ -> }
) {
    val context = LocalContext.current

    // Tab state: 0 = Entrar (Login), 1 = Criar Conta (Register)
    var selectedTab by remember { mutableIntStateOf(0) }

    // Login Form State
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(false) }
    var isLoadingLogin by remember { mutableStateOf(false) }
    var loginErrorMessage by remember { mutableStateOf<String?>(null) }

    // Register Form State
    var regName by remember { mutableStateOf("") }
    var regPhone by remember { mutableStateOf("") }
    var regCpf by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regConfirmPassword by remember { mutableStateOf("") }
    var isLoadingRegister by remember { mutableStateOf(false) }
    var registerErrorMessage by remember { mutableStateOf<String?>(null) }
    var showRegistrationSuccessDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Stadium background image
        Image(
            painter = painterResource(id = R.drawable.bg_login),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Scrim overlay for contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .imePadding(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.futeplayer_app_icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )

                // Tabs: Entrar / Criar Conta
                if (isRegistrationEnabled) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = StadiumGreenPrimary,
                        divider = {},
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = {
                                selectedTab = 0
                                loginErrorMessage = null
                            },
                            text = {
                                Text(
                                    "Entrar",
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 0) StadiumGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = {
                                selectedTab = 1
                                registerErrorMessage = null
                            },
                            text = {
                                Text(
                                    "Criar Conta",
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == 1) StadiumGreenPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }

                if (!noticeMessage.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = noticeMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // TAB 0: LOGIN
                if (selectedTab == 0) {
                    Text(
                        text = "Faça login com seu CPF ou Celular e senha para continuar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    OutlinedTextField(
                        value = identifier,
                        onValueChange = {
                            identifier = it
                            if (!noticeMessage.isNullOrBlank()) onClearNotice()
                            loginErrorMessage = null
                        },
                        label = { Text("CPF ou Celular") },
                        placeholder = { Text("Ex: CPF ou (00) 00000-0000") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            if (!noticeMessage.isNullOrBlank()) onClearNotice()
                            loginErrorMessage = null
                        },
                        label = { Text("Senha") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = { rememberMe = it },
                            colors = CheckboxDefaults.colors(checkedColor = StadiumGreenPrimary)
                        )
                        Text(
                            text = "Salvar login e senha",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (loginErrorMessage != null) {
                        Text(
                            text = loginErrorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.Start)
                        )
                    }

                    Button(
                        onClick = {
                            if (identifier.isBlank() || password.isBlank()) {
                                loginErrorMessage = "Preencha o CPF/Celular e a senha."
                                return@Button
                            }
                            isLoadingLogin = true
                            loginErrorMessage = null
                            onLogin(identifier.trim(), password, rememberMe) { success, error ->
                                isLoadingLogin = false
                                if (!success) {
                                    loginErrorMessage = error ?: "CPF, celular ou senha inválidos."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StadiumGreenPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoadingLogin
                    ) {
                        if (isLoadingLogin) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Entrar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    if (isRegistrationEnabled) {
                        TextButton(
                            onClick = {
                                selectedTab = 1
                                registerErrorMessage = null
                            }
                        ) {
                            Text(
                                "Não tem uma conta? Cadastre-se aqui",
                                color = StadiumGreenPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                } else {
                    // TAB 1: CRIAR CONTA (REGISTER)
                    Text(
                        text = "Preencha seus dados para solicitar o cadastro.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = StadiumGreenPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "A conta será criada com status inativo até a aprovação de um administrador.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    OutlinedTextField(
                        value = regName,
                        onValueChange = {
                            regName = it
                            registerErrorMessage = null
                        },
                        label = { Text("Nome Completo *") },
                        placeholder = { Text("Seu nome completo") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = regPhone,
                        onValueChange = {
                            regPhone = it
                            registerErrorMessage = null
                        },
                        label = { Text("Número de Celular / WhatsApp *") },
                        placeholder = { Text("Ex: (75) 99249-0975") },
                        leadingIcon = {
                            Icon(Icons.Default.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = regCpf,
                        onValueChange = {
                            regCpf = it
                            registerErrorMessage = null
                        },
                        label = { Text("CPF (Opcional)") },
                        placeholder = { Text("000.000.000-00") },
                        leadingIcon = {
                            Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = regPassword,
                        onValueChange = {
                            regPassword = it
                            registerErrorMessage = null
                        },
                        label = { Text("Senha *") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = regConfirmPassword,
                        onValueChange = {
                            regConfirmPassword = it
                            registerErrorMessage = null
                        },
                        label = { Text("Confirmar Senha *") },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (registerErrorMessage != null) {
                        Text(
                            text = registerErrorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.Start)
                        )
                    }

                    Button(
                        onClick = {
                            if (regName.isBlank()) {
                                registerErrorMessage = "Por favor, informe seu nome completo."
                                return@Button
                            }
                            if (regPhone.isBlank()) {
                                registerErrorMessage = "Por favor, informe o número de celular."
                                return@Button
                            }
                            if (regPassword.isBlank()) {
                                registerErrorMessage = "Por favor, crie uma senha."
                                return@Button
                            }
                            if (regPassword.length < 4) {
                                registerErrorMessage = "A senha deve ter pelo menos 4 caracteres."
                                return@Button
                            }
                            if (regPassword != regConfirmPassword) {
                                registerErrorMessage = "As senhas não coincidem. Digite novamente."
                                return@Button
                            }

                            isLoadingRegister = true
                            registerErrorMessage = null
                            onRegister(regName.trim(), regPhone.trim(), regCpf.trim(), regPassword.trim()) { success, err ->
                                isLoadingRegister = false
                                if (success) {
                                    showRegistrationSuccessDialog = true
                                    // prefill identifier for login
                                    identifier = if (regCpf.isNotBlank()) regCpf.trim() else regPhone.trim()
                                    password = regPassword.trim()
                                } else {
                                    registerErrorMessage = err ?: "Erro ao criar conta."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StadiumGreenPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isLoadingRegister
                    ) {
                        if (isLoadingRegister) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Criar Conta", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }

                    TextButton(
                        onClick = {
                            selectedTab = 0
                            loginErrorMessage = null
                        }
                    ) {
                        Text(
                            "Já possui uma conta? Faça login",
                            color = StadiumGreenPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        val prefs = context.getSharedPreferences("futemais_prefs", android.content.Context.MODE_PRIVATE)
                        val supportNumber = prefs.getString("support_whatsapp_number", "(75) 9 9249-0975") ?: "(75) 9 9249-0975"
                        val digitsOnly = supportNumber.filter { it.isDigit() }
                        val cleanNumber = if (digitsOnly.startsWith("55")) digitsOnly else "55$digitsOnly"
                        try {
                            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=Ol%C3%A1,%20preciso%20de%20suporte%20no%20aplicativo.")
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "WhatsApp suporte: $supportNumber", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StadiumGreenPrimary)
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Suporte / WhatsApp", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Modal de Sucesso após criação de conta
    if (showRegistrationSuccessDialog) {
        AlertDialog(
            onDismissRequest = {
                showRegistrationSuccessDialog = false
                selectedTab = 0
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = StadiumGreenPrimary,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = "Conta Criada com Sucesso!",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Sua solicitação de cadastro foi registrada no sistema.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Por razões de segurança, a sua conta foi criada com status INATIVO. Um administrador irá analisar e ativar sua conta em breve.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Surface(
                        color = StadiumGreenPrimary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = null,
                                tint = StadiumGreenPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Entre em contato com o suporte para agilizar a liberação da sua conta!",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = StadiumGreenPrimary
                            )
                        }
                    }

                    Text(
                        text = "Assim que a conta for aprovada, você poderá entrar utilizando seu celular ou CPF e a senha cadastrada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val prefs = context.getSharedPreferences("futemais_prefs", android.content.Context.MODE_PRIVATE)
                        val supportNumber = prefs.getString("support_whatsapp_number", "(75) 9 9249-0975") ?: "(75) 9 9249-0975"
                        val digitsOnly = supportNumber.filter { it.isDigit() }
                        val cleanNumber = if (digitsOnly.startsWith("55")) digitsOnly else "55$digitsOnly"
                        val candidateName = regName.trim()
                        val msg = if (candidateName.isNotBlank()) {
                            "Ol%C3%A1,%20acabei%20de%20criar%20minha%20conta%20($candidateName)%20no%20app%20e%20gostaria%20de%20solicitar%20a%20ativa%C3%A7%C3%A3o."
                        } else {
                            "Ol%C3%A1,%20acabei%20de%20criar%20minha%20conta%20no%20app%20e%20gostaria%20de%20solicitar%20a%20ativa%C3%A7%C3%A3o."
                        }
                        try {
                            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanNumber&text=$msg")
                            val intent = Intent(Intent.ACTION_VIEW, uri)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "WhatsApp suporte: $supportNumber", Toast.LENGTH_LONG).show()
                        }
                        showRegistrationSuccessDialog = false
                        selectedTab = 0
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StadiumGreenPrimary)
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Falar com Suporte")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showRegistrationSuccessDialog = false
                        selectedTab = 0
                    }
                ) {
                    Text("OK, Entendi")
                }
            }
        )
    }
}

