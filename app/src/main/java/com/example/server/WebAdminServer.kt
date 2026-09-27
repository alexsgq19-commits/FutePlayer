package com.example.server

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.FutemaisRepository
import com.example.data.UserRepository
import com.example.data.models.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.*

/**
 * Servidor HTTP Embutido no FutePlayer para Gerenciamento Web no Navegador.
 * Permite autenticar administradores, adicionar, editar e excluir canais, filmes e séries em tempo real,
 * refletindo imediatamente no aplicativo Android e sincronizando com o Firebase Firestore.
 */
class WebAdminServer private constructor(
    private val context: Context,
    private val repository: FutemaisRepository
) {
    private val userRepository by lazy { UserRepository(context) }
    companion object {
        private const val TAG = "WebAdminServer"
        const val DEFAULT_PORT = 8765

        @Volatile
        private var instance: WebAdminServer? = null

        fun getInstance(context: Context, repository: FutemaisRepository): WebAdminServer {
            return instance ?: synchronized(this) {
                instance ?: WebAdminServer(context.applicationContext, repository).also {
                    instance = it
                }
            }
        }
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var serverJob: Job? = null
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var activePort = DEFAULT_PORT

    var onDataUpdatedListener: (() -> Unit)? = null

    fun isServerRunning(): Boolean = isRunning

    fun getPort(): Int = activePort

    /**
     * Obtém o endereço IP local na rede Wi-Fi para acesso pelo computador/outro dispositivo.
     */
    fun getLocalIpAddress(): String? {
        try {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiManager?.connectionInfo?.ipAddress?.let { ipInt ->
                if (ipInt != 0) {
                    val ip = String.format(
                        java.util.Locale.US,
                        "%d.%d.%d.%d",
                        ipInt and 0xff,
                        ipInt shr 8 and 0xff,
                        ipInt shr 16 and 0xff,
                        ipInt shr 24 and 0xff
                    )
                    if (ip != "0.0.0.0") return ip
                }
            }

            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                if (intf.isLoopback || !intf.isUp) continue
                val addresses = intf.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (!host.isNullOrBlank() && !host.startsWith("127.")) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível obter IP local: ${e.message}")
        }
        return null
    }

    /**
     * Retorna a URL para abrir no navegador do próprio aparelho (127.0.0.1)
     * ou no navegador do PC (via IP local da rede Wi-Fi).
     */
    fun getWebUrl(forExternalDevice: Boolean = false): String {
        val ip = if (forExternalDevice) getLocalIpAddress() ?: "127.0.0.1" else "127.0.0.1"
        return "http://$ip:$activePort"
    }

    /**
     * Inicia o servidor HTTP em segundo plano.
     */
    @Synchronized
    fun start(onStarted: (port: Int, url: String) -> Unit = { _, _ -> }) {
        if (isRunning && serverSocket != null && !serverSocket!!.isClosed) {
            val url = getWebUrl()
            onStarted(activePort, url)
            return
        }

        serverJob?.cancel()
        serverJob = serverScope.launch {
            var port = DEFAULT_PORT
            var socket: ServerSocket? = null

            for (offset in 0..5) {
                try {
                    socket = ServerSocket(port + offset, 50, InetAddress.getByName("0.0.0.0"))
                    port += offset
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Porta ${port + offset} ocupada, tentando próxima...")
                }
            }

            if (socket == null) {
                Log.e(TAG, "Falha ao vincular porta para o WebAdminServer")
                return@launch
            }

            serverSocket = socket
            activePort = port
            isRunning = true

            val localUrl = getWebUrl()
            Log.d(TAG, "WebAdminServer iniciado com sucesso em $localUrl (IP local: ${getLocalIpAddress()})")

            withContext(Dispatchers.Main) {
                onStarted(activePort, localUrl)
            }

            while (isActive && isRunning) {
                try {
                    val clientSocket = socket.accept()
                    launch(Dispatchers.IO) {
                        handleClient(clientSocket)
                    }
                } catch (e: Exception) {
                    if (!isRunning) break
                    Log.w(TAG, "Aviso no accept do socket: ${e.message}")
                }
            }
        }
    }

    /**
     * Para o servidor HTTP.
     */
    @Synchronized
    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        Log.d(TAG, "WebAdminServer parado.")
    }

    /**
     * Exporta o arquivo HTML autônomo do painel e compartilha via Intent.ACTION_SEND
     */
    fun shareAdminHtml(ctx: Context) {
        try {
            val htmlContent = loadAdminHtml()
            val cacheFile = File(ctx.cacheDir, "painel_admin_futeplayer.html")
            cacheFile.writeText(htmlContent, Charsets.UTF_8)

            val contentUri: Uri = FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.provider",
                cacheFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/html"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "Painel Web Administrador - FutePlayer")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Abra este arquivo HTML no navegador do seu computador para adicionar, editar e excluir canais, filmes e séries diretamente no FutePlayer!"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Compartilhar Painel Web FutePlayer").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao compartilhar arquivo HTML do painel: ${e.message}", e)
        }
    }

    /**
     * Carrega o HTML do painel dos assets ou fallback inline
     */
    private fun loadAdminHtml(): String {
        return try {
            context.assets.open("admin/index.html").use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Assets admin/index.html não encontrado, usando fallback embutido.")
            getAdminPanelHtml()
        }
    }

    /**
     * Processa a requisição HTTP do navegador.
     */
    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 15000
            val input = BufferedReader(InputStreamReader(socket.getInputStream(), "UTF-8"))
            val output = BufferedOutputStream(socket.getOutputStream())

            val requestLine = input.readLine() ?: run {
                socket.close()
                return
            }

            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                socket.close()
                return
            }

            val method = parts[0].uppercase()
            val fullPath = parts[1]
            val path = fullPath.substringBefore("?")

            var contentLength = 0
            var line: String?
            while (input.readLine().also { line = it } != null) {
                if (line.isNullOrBlank()) break
                val headerParts = line!!.split(":", limit = 2)
                if (headerParts.size == 2) {
                    val name = headerParts[0].trim().lowercase()
                    val value = headerParts[1].trim()
                    if (name == "content-length") {
                        contentLength = value.toIntOrNull() ?: 0
                    }
                }
            }

            val body = if (contentLength > 0) {
                val charArray = CharArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val read = input.read(charArray, readTotal, contentLength - readTotal)
                    if (read == -1) break
                    readTotal += read
                }
                String(charArray, 0, readTotal)
            } else ""

            if (method == "OPTIONS") {
                sendResponse(output, 204, "No Content", "text/plain", ByteArray(0), addCors = true)
                socket.close()
                return
            }

            when {
                // Página principal do Painel Web (serve o painel completo com abas de edição)
                method == "GET" && (path == "/" || path == "/index.html" || path == "/admin") -> {
                    val html = loadAdminHtml()
                    val bytes = html.toByteArray(Charsets.UTF_8)
                    sendResponse(output, 200, "OK", "text/html; charset=UTF-8", bytes, addCors = true)
                }

                // API: Status do Servidor
                method == "GET" && path == "/api/status" -> {
                    val json = JSONObject().apply {
                        put("status", "online")
                        put("port", activePort)
                        put("localIp", getLocalIpAddress() ?: "127.0.0.1")
                        put("appVersion", "1.3.0")
                        put("timestamp", System.currentTimeMillis())
                    }
                    val bytes = json.toString().toByteArray(Charsets.UTF_8)
                    sendResponse(output, 200, "OK", "application/json; charset=UTF-8", bytes, addCors = true)
                }

                // API: Lista completa de canais e catálogo para visualização e edição no navegador
                method == "GET" && path == "/api/data" -> {
                    val channels = repository.getQuickChannels()
                    val media = repository.getMediaCatalog()
                    val categories = repository.getCustomCategories()
                    val genres = repository.getCustomMediaGenres()

                    val channelsJson = JSONArray()
                    channels.forEach { ch ->
                        channelsJson.put(JSONObject().apply {
                            put("id", ch.id)
                            put("title", ch.title)
                            put("subtitle", ch.subtitle)
                            put("streamUrl", ch.streamUrl)
                            put("category", ch.category ?: "Esportes")
                            put("isWorking", ch.isWorking)
                            put("posterUrl", ch.posterUrl ?: "")
                            put("isWebPlayer", ch.forceWebPlayer)
                            put("isIframe", ch.isIframe)
                        })
                    }

                    val mediaJson = JSONArray()
                    media.forEach { m ->
                        val seasonsArr = JSONArray()
                        m.seasons.forEach { s ->
                            val epsArr = JSONArray()
                            s.episodes.forEach { ep ->
                                epsArr.put(JSONObject().apply {
                                    put("id", ep.id)
                                    put("episodeNumber", ep.episodeNumber)
                                    put("title", ep.title)
                                    put("streamUrl", ep.streamUrl)
                                    put("isWebPlayer", ep.isWebPlayer)
                                    put("duration", ep.duration ?: "45 min")
                                    put("synopsis", ep.synopsis ?: "")
                                })
                            }
                            seasonsArr.put(JSONObject().apply {
                                put("seasonNumber", s.seasonNumber)
                                put("title", s.title)
                                put("episodes", epsArr)
                            })
                        }

                        mediaJson.put(JSONObject().apply {
                            put("id", m.id)
                            put("title", m.title)
                            put("type", m.type.name)
                            put("category", m.category)
                            put("year", m.year)
                            put("rating", m.rating)
                            put("coverUrl", m.coverUrl)
                            put("backdropUrl", m.backdropUrl ?: "")
                            put("synopsis", m.synopsis ?: "")
                            put("movieStreamUrl", m.movieStreamUrl ?: "")
                            put("isWebPlayer", m.isWebPlayer)
                            put("isWorking", m.isWorking)
                            put("totalEpisodes", m.totalEpisodes)
                            put("seasons", seasonsArr)
                        })
                    }

                    val root = JSONObject().apply {
                        put("channels", channelsJson)
                        put("media", mediaJson)
                        put("categories", JSONArray(categories))
                        put("genres", JSONArray(genres))
                        put("channelCount", channels.size)
                        put("mediaCount", media.size)
                    }
                    val bytes = root.toString().toByteArray(Charsets.UTF_8)
                    sendResponse(output, 200, "OK", "application/json; charset=UTF-8", bytes, addCors = true)
                }

                // API: Adicionar ou Editar Canal
                method == "POST" && (path == "/api/channels" || path == "/api/channels/save" || path == "/api/channels/edit") -> {
                    val resp = handleSaveChannel(body)
                    val bytes = resp.toString().toByteArray(Charsets.UTF_8)
                    sendResponse(output, 200, "OK", "application/json; charset=UTF-8", bytes, addCors = true)
                    notifyDataChanged()
                }

                // API: Excluir Canal
                (method == "POST" || method == "DELETE") && (path == "/api/channels/delete" || path == "/api/channels/remove") -> {
                    val resp = handleDeleteChannel(body)
                    val bytes = resp.toString().toByteArray(Charsets.UTF_8)
                    sendResponse(output, 200, "OK", "application/json; charset=UTF-8", bytes, addCors = true)
                    notifyDataChanged()
                }

                // API: Adicionar ou Editar Filme
                method == "POST" && (path == "/api/movies" || path == "/api/movies/save" || path == "/api/movies/edit") -> {
                    val resp = handleSaveMovie(body)
                    val bytes = resp.toString().toByteArray(Charsets.UTF_8)
                    sendResponse(output, 200, "OK", "application/json; charset=UTF-8", bytes, addCors = true)
                    notifyDataChanged()
                }

                // API: Excluir Filme ou Item de Mídia
                (method == "POST" || method == "DELETE") && (path == "/api/movies/delete" || path == "/api/media/delete" || path == "/api/media/remove") -> {
                    val resp = handleDeleteMedia(body)
                    val bytes = resp.toString().toByteArray(Charsets.UTF_8)
                    sendResponse(output, 200, "OK", "application/json; charset=UTF-8", bytes, addCors = true)
                    notifyDataChanged()
                }

                // API: Adicionar ou Editar Série
                method == "POST" && (path == "/api/series" || path == "/api/series/save" || path == "/api/series/edit") -> {
                    val resp = handleSaveSeries(body)
                    val bytes = resp.toString().toByteArray(Charsets.UTF_8)
                    sendResponse(output, 200, "OK", "application/json; charset=UTF-8", bytes, addCors = true)
                    notifyDataChanged()
                }

                // API: Excluir Série
                (method == "POST" || method == "DELETE") && (path == "/api/series/delete" || path == "/api/series/remove") -> {
                    val resp = handleDeleteMedia(body)
                    val bytes = resp.toString().toByteArray(Charsets.UTF_8)
                    sendResponse(output, 200, "OK", "application/json; charset=UTF-8", bytes, addCors = true)
                    notifyDataChanged()
                }

                // API: Sincronização em Lote de Canais
                method == "POST" && path == "/api/sync/channels" -> {
                    val resp = handleBulkSyncChannels(body)
                    val bytes = resp.toString().toByteArray(Charsets.UTF_8)
                    sendResponse(output, 200, "OK", "application/json; charset=UTF-8", bytes, addCors = true)
                    notifyDataChanged()
                }

                // API: Sincronização em Lote de Catálogo de Mídia
                method == "POST" && path == "/api/sync/media" -> {
                    val resp = handleBulkSyncMedia(body)
                    val bytes = resp.toString().toByteArray(Charsets.UTF_8)
                    sendResponse(output, 200, "OK", "application/json; charset=UTF-8", bytes, addCors = true)
                    notifyDataChanged()
                }

                // API: Autenticação de Administrador no Painel Web (Exige Login e Senha de ADMIN)
                method == "POST" && (path == "/api/login" || path == "/api/auth/login") -> {
                    val resp = handleLogin(body)
                    val bytes = resp.toString().toByteArray(Charsets.UTF_8)
                    val status = if (resp.optBoolean("success", false)) 200 else 401
                    sendResponse(output, status, if (status == 200) "OK" else "Unauthorized", "application/json; charset=UTF-8", bytes, addCors = true)
                }

                // API: Salvar Categorias Personalizadas
                method == "POST" && path == "/api/categories/save" -> {
                    val resp = handleSaveCategories(body)
                    val bytes = resp.toString().toByteArray(Charsets.UTF_8)
                    sendResponse(output, 200, "OK", "application/json; charset=UTF-8", bytes, addCors = true)
                    notifyDataChanged()
                }

                // API: Salvar Gêneros Personalizados
                method == "POST" && path == "/api/genres/save" -> {
                    val resp = handleSaveGenres(body)
                    val bytes = resp.toString().toByteArray(Charsets.UTF_8)
                    sendResponse(output, 200, "OK", "application/json; charset=UTF-8", bytes, addCors = true)
                    notifyDataChanged()
                }

                else -> {
                    val msg = "{\"error\": \"Rota não encontrada: $path\"}"
                    val bytes = msg.toByteArray(Charsets.UTF_8)
                    sendResponse(output, 404, "Not Found", "application/json; charset=UTF-8", bytes, addCors = true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao atender requisição: ${e.message}")
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    private fun handleLogin(jsonBody: String): JSONObject {
        val root = try { JSONObject(jsonBody) } catch (_: Exception) { JSONObject() }
        val identifier = root.optString("identifier").trim().ifBlank { root.optString("cpf").trim() }.ifBlank { root.optString("username").trim() }
        val password = root.optString("password").trim()

        if (identifier.isBlank() || password.isBlank()) {
            return JSONObject().apply {
                put("success", false)
                put("error", "Informe o CPF/Usuário e a senha para acessar o painel!")
            }
        }

        val cleanDigits = identifier.filter { it.isDigit() }
        // Hardcoded Master Admin fallback match
        if ((identifier == "06462555505" || cleanDigits == "06462555505") && password == "123456") {
            return JSONObject().apply {
                put("success", true)
                put("token", "admin_session_${System.currentTimeMillis()}")
                put("user", JSONObject().apply {
                    put("name", "Administrador Master")
                    put("cpf", "06462555505")
                    put("role", "ADMIN")
                })
                put("message", "Login de Administrador realizado com sucesso!")
            }
        }

        // Authenticate using UserRepository
        var user: User? = null
        try {
            kotlinx.coroutines.runBlocking {
                val result = userRepository.login(identifier, password)
                user = result.getOrNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao autenticar usuário no painel: ${e.message}")
        }

        if (user == null) {
            return JSONObject().apply {
                put("success", false)
                put("error", "Credenciais incorretas! Verifique o CPF/usuário e senha.")
            }
        }

        val isAdmin = user?.role == "ADMIN" || user?.cpf == "06462555505" || user?.uid?.startsWith("admin_") == true
        if (!isAdmin) {
            return JSONObject().apply {
                put("success", false)
                put("error", "Acesso Negado: Apenas contas com privilégios de Administrador podem acessar o Painel Web!")
            }
        }

        return JSONObject().apply {
            put("success", true)
            put("token", "admin_session_${System.currentTimeMillis()}")
            put("user", JSONObject().apply {
                put("name", user?.name ?: "Administrador")
                put("cpf", user?.cpf ?: "")
                put("role", user?.role ?: "ADMIN")
                put("phone", user?.phone ?: "")
                put("uid", user?.uid ?: "")
            })
            put("message", "Bem-vindo(a), ${user?.name ?: "Administrador"}!")
        }
    }

    private fun handleSaveCategories(jsonBody: String): JSONObject {
        val root = try { JSONObject(jsonBody) } catch (_: Exception) { JSONObject() }
        val arr = root.optJSONArray("categories")
        val list = mutableListOf<String>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val cat = arr.getString(i).trim()
                if (cat.isNotBlank() && !list.contains(cat)) list.add(cat)
            }
        }
        if (list.isNotEmpty()) {
            repository.saveCustomCategories(list)
        }
        return JSONObject().apply {
            put("success", true)
            put("message", "Categorias salvas com sucesso!")
        }
    }

    private fun handleSaveGenres(jsonBody: String): JSONObject {
        val root = try { JSONObject(jsonBody) } catch (_: Exception) { JSONObject() }
        val arr = root.optJSONArray("genres")
        val list = mutableListOf<String>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val g = arr.getString(i).trim()
                if (g.isNotBlank() && !list.contains(g)) list.add(g)
            }
        }
        if (list.isNotEmpty()) {
            repository.saveCustomMediaGenres(list)
        }
        return JSONObject().apply {
            put("success", true)
            put("message", "Gêneros salvos com sucesso!")
        }
    }

    private fun handleSaveChannel(jsonBody: String): JSONObject {
        val root = JSONObject(jsonBody)
        val id = root.optString("id").trim().ifBlank { "custom_${System.currentTimeMillis()}" }
        val title = root.optString("title").trim()
        val url = root.optString("streamUrl").ifBlank { root.optString("url") }.trim()
        val subtitle = root.optString("subtitle").ifBlank { "Canal Gerenciado via Painel Web" }.trim()
        val category = root.optString("category").ifBlank { "Canais TV" }.trim()
        val logoUrl = root.optString("logoUrl").ifBlank { root.optString("posterUrl") }.trim().takeIf { it.isNotBlank() }
        val isWebPlayer = root.optBoolean("isWebPlayer", false)
        val isIframe = root.optBoolean("isIframe", false) || url.contains("<iframe", ignoreCase = true)
        val isWorking = root.optBoolean("isWorking", true)

        if (title.isBlank() || url.isBlank()) {
            return JSONObject().apply {
                put("success", false)
                put("error", "Título e URL do canal são obrigatórios!")
            }
        }

        val channel = PlayableVideo(
            id = id,
            title = title,
            subtitle = subtitle,
            streamUrl = url,
            posterUrl = logoUrl,
            embedUrl = url,
            forceWebPlayer = isWebPlayer || isIframe,
            isLive = true,
            category = category,
            isWorking = isWorking,
            isIframe = isIframe
        )

        repository.addCustomChannel(channel)

        return JSONObject().apply {
            put("success", true)
            put("message", "Canal '$title' salvo com sucesso no aplicativo!")
            put("id", id)
        }
    }

    private fun handleDeleteChannel(jsonBody: String): JSONObject {
        val root = JSONObject(jsonBody)
        val id = root.optString("id").trim()
        if (id.isBlank()) {
            return JSONObject().apply {
                put("success", false)
                put("error", "ID do canal é obrigatório para exclusão!")
            }
        }
        repository.deleteCustomChannel(id)
        return JSONObject().apply {
            put("success", true)
            put("message", "Canal excluído com sucesso do aplicativo!")
        }
    }

    private fun handleSaveMovie(jsonBody: String): JSONObject {
        val root = JSONObject(jsonBody)
        val id = root.optString("id").trim().ifBlank { "movie_${System.currentTimeMillis()}" }
        val title = root.optString("title").trim()
        val streamUrl = root.optString("movieStreamUrl").ifBlank { root.optString("streamUrl") }.trim()
        val category = root.optString("category").ifBlank { "Geral" }.trim()
        val coverUrl = root.optString("coverUrl").ifBlank {
            "https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg"
        }.trim()
        val backdropUrl = root.optString("backdropUrl").takeIf { it.isNotBlank() }
        val synopsis = root.optString("synopsis").ifBlank { "Filme gerenciado pelo Painel Web." }.trim()
        val year = root.optString("year").ifBlank { "2024" }.trim()
        val rating = root.optString("rating").ifBlank { "8.5" }.trim()
        val isWebPlayer = root.optBoolean("isWebPlayer", false)

        if (title.isBlank() || streamUrl.isBlank()) {
            return JSONObject().apply {
                put("success", false)
                put("error", "Título e Link do Filme são obrigatórios!")
            }
        }

        val movieItem = MediaItem(
            id = id,
            title = title,
            type = MediaContentType.MOVIE,
            coverUrl = coverUrl,
            backdropUrl = backdropUrl,
            synopsis = synopsis,
            category = category,
            year = year,
            rating = rating,
            movieStreamUrl = streamUrl,
            isWebPlayer = isWebPlayer,
            isWorking = true
        )

        repository.addOrUpdateMediaItem(movieItem)

        return JSONObject().apply {
            put("success", true)
            put("message", "Filme '$title' salvo com sucesso no aplicativo!")
            put("id", id)
        }
    }

    private fun handleDeleteMedia(jsonBody: String): JSONObject {
        val root = JSONObject(jsonBody)
        val id = root.optString("id").trim()
        if (id.isBlank()) {
            return JSONObject().apply {
                put("success", false)
                put("error", "ID do item de mídia é obrigatório para exclusão!")
            }
        }
        repository.deleteMediaItem(id)
        return JSONObject().apply {
            put("success", true)
            put("message", "Item excluído com sucesso do catálogo do aplicativo!")
        }
    }

    private fun handleSaveSeries(jsonBody: String): JSONObject {
        val root = JSONObject(jsonBody)
        val id = root.optString("id").trim().ifBlank { "series_${System.currentTimeMillis()}" }
        val title = root.optString("title").trim()
        val category = root.optString("category").ifBlank { "Séries" }.trim()
        val coverUrl = root.optString("coverUrl").ifBlank {
            "https://image.tmdb.org/t/p/w500/u3bZgnGQ9T01sWNhyveQz0wH0Hl.jpg"
        }.trim()
        val backdropUrl = root.optString("backdropUrl").takeIf { it.isNotBlank() }
        val synopsis = root.optString("synopsis").ifBlank { "Série gerenciada pelo Painel Web." }.trim()
        val year = root.optString("year").ifBlank { "2024" }.trim()
        val rating = root.optString("rating").ifBlank { "9.0" }.trim()

        if (title.isBlank()) {
            return JSONObject().apply {
                put("success", false)
                put("error", "Título da série é obrigatório!")
            }
        }

        val seasonsList = mutableListOf<SeasonItem>()
        val seasonsArr = root.optJSONArray("seasons")

        if (seasonsArr != null && seasonsArr.length() > 0) {
            for (sIdx in 0 until seasonsArr.length()) {
                val sObj = seasonsArr.getJSONObject(sIdx)
                val seasonNum = sObj.optInt("seasonNumber", sIdx + 1)
                val seasonTitle = sObj.optString("title").ifBlank { "Temporada $seasonNum" }
                val epList = mutableListOf<EpisodeItem>()
                val epArr = sObj.optJSONArray("episodes")

                if (epArr != null) {
                    for (eIdx in 0 until epArr.length()) {
                        val eObj = epArr.getJSONObject(eIdx)
                        val epNum = eObj.optInt("episodeNumber", eIdx + 1)
                        val epTitle = eObj.optString("title").ifBlank { "Episódio $epNum" }
                        val epUrl = eObj.optString("streamUrl").trim()
                        val isWeb = eObj.optBoolean("isWebPlayer", false)
                        val duration = eObj.optString("duration").ifBlank { "45 min" }
                        val epSyn = eObj.optString("synopsis")

                        if (epUrl.isNotBlank()) {
                            epList.add(
                                EpisodeItem(
                                    id = eObj.optString("id").ifBlank { "ep_${id}_${seasonNum}_$epNum" },
                                    episodeNumber = epNum,
                                    title = epTitle,
                                    streamUrl = epUrl,
                                    duration = duration,
                                    synopsis = epSyn,
                                    isWebPlayer = isWeb
                                )
                            )
                        }
                    }
                }

                if (epList.isNotEmpty()) {
                    seasonsList.add(
                        SeasonItem(
                            seasonNumber = seasonNum,
                            title = seasonTitle,
                            episodes = epList
                        )
                    )
                }
            }
        }

        if (seasonsList.isEmpty()) {
            val simpleUrl = root.optString("streamUrl").ifBlank { root.optString("movieStreamUrl") }.trim()
            if (simpleUrl.isNotBlank()) {
                seasonsList.add(
                    SeasonItem(
                        seasonNumber = 1,
                        title = "Temporada 1",
                        episodes = listOf(
                            EpisodeItem(
                                id = "ep_${id}_1_1",
                                episodeNumber = 1,
                                title = "Episódio 1",
                                streamUrl = simpleUrl
                            )
                        )
                    )
                )
            }
        }

        val seriesItem = MediaItem(
            id = id,
            title = title,
            type = MediaContentType.SERIES,
            coverUrl = coverUrl,
            backdropUrl = backdropUrl,
            synopsis = synopsis,
            category = category,
            year = year,
            rating = rating,
            seasons = seasonsList,
            isWorking = true
        )

        repository.addOrUpdateMediaItem(seriesItem)

        return JSONObject().apply {
            put("success", true)
            put("message", "Série '$title' com ${seriesItem.totalEpisodes} episódio(s) salva com sucesso!")
            put("id", id)
        }
    }

    private fun handleBulkSyncChannels(jsonBody: String): JSONObject {
        val root = JSONObject(jsonBody)
        val channelsArr = root.optJSONArray("channels")
        if (channelsArr != null) {
            for (i in 0 until channelsArr.length()) {
                val obj = channelsArr.getJSONObject(i)
                val item = PlayableVideo(
                    id = obj.optString("id"),
                    title = obj.optString("title"),
                    subtitle = obj.optString("subtitle"),
                    streamUrl = obj.optString("streamUrl"),
                    posterUrl = obj.optString("posterUrl").takeIf { it.isNotBlank() },
                    category = obj.optString("category"),
                    isWorking = obj.optBoolean("isWorking", true),
                    forceWebPlayer = obj.optBoolean("isWebPlayer", false),
                    isIframe = obj.optBoolean("isIframe", false)
                )
                repository.addCustomChannel(item)
            }
        }
        return JSONObject().apply {
            put("success", true)
            put("message", "Canais sincronizados no aplicativo!")
        }
    }

    private fun handleBulkSyncMedia(jsonBody: String): JSONObject {
        val root = JSONObject(jsonBody)
        val mediaArr = root.optJSONArray("media")
        if (mediaArr != null) {
            for (i in 0 until mediaArr.length()) {
                val obj = mediaArr.getJSONObject(i)
                val typeStr = obj.optString("type", "MOVIE")
                val type = if (typeStr.equals("SERIES", ignoreCase = true)) MediaContentType.SERIES else MediaContentType.MOVIE

                val seasonsList = mutableListOf<SeasonItem>()
                val sArr = obj.optJSONArray("seasons")
                if (sArr != null) {
                    for (sIdx in 0 until sArr.length()) {
                        val sObj = sArr.getJSONObject(sIdx)
                        val epList = mutableListOf<EpisodeItem>()
                        val epArr = sObj.optJSONArray("episodes")
                        if (epArr != null) {
                            for (eIdx in 0 until epArr.length()) {
                                val epObj = epArr.getJSONObject(eIdx)
                                epList.add(
                                    EpisodeItem(
                                        id = epObj.optString("id"),
                                        episodeNumber = epObj.optInt("episodeNumber", eIdx + 1),
                                        title = epObj.optString("title"),
                                        streamUrl = epObj.optString("streamUrl"),
                                        duration = epObj.optString("duration", "45 min"),
                                        synopsis = epObj.optString("synopsis"),
                                        isWebPlayer = epObj.optBoolean("isWebPlayer", false)
                                    )
                                )
                            }
                        }
                        seasonsList.add(
                            SeasonItem(
                                seasonNumber = sObj.optInt("seasonNumber", sIdx + 1),
                                title = sObj.optString("title"),
                                episodes = epList
                            )
                        )
                    }
                }

                val mediaItem = MediaItem(
                    id = obj.optString("id"),
                    title = obj.optString("title"),
                    type = type,
                    category = obj.optString("category"),
                    year = obj.optString("year"),
                    rating = obj.optString("rating"),
                    coverUrl = obj.optString("coverUrl"),
                    backdropUrl = obj.optString("backdropUrl").takeIf { it.isNotBlank() },
                    synopsis = obj.optString("synopsis"),
                    movieStreamUrl = obj.optString("movieStreamUrl"),
                    isWebPlayer = obj.optBoolean("isWebPlayer", false),
                    isWorking = obj.optBoolean("isWorking", true),
                    seasons = seasonsList
                )
                repository.addOrUpdateMediaItem(mediaItem)
            }
        }
        return JSONObject().apply {
            put("success", true)
            put("message", "Catálogo sincronizado no aplicativo!")
        }
    }

    private fun notifyDataChanged() {
        Handler(Looper.getMainLooper()).post {
            onDataUpdatedListener?.invoke()
        }
    }

    private fun sendResponse(
        output: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: ByteArray,
        addCors: Boolean = true
    ) {
        val writer = PrintWriter(OutputStreamWriter(output, Charsets.UTF_8))
        writer.print("HTTP/1.1 $statusCode $statusText\r\n")
        writer.print("Content-Type: $contentType\r\n")
        writer.print("Content-Length: ${body.size}\r\n")
        writer.print("Connection: close\r\n")
        if (addCors) {
            writer.print("Access-Control-Allow-Origin: *\r\n")
            writer.print("Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n")
            writer.print("Access-Control-Allow-Headers: Content-Type, Authorization\r\n")
        }
        writer.print("\r\n")
        writer.flush()
        if (body.isNotEmpty()) {
            output.write(body)
            output.flush()
        }
    }

    /**
     * Retorna a página HTML do Painel Web Admin Completo com suporte a Adicionar, Editar e Excluir.
     */
    private fun getAdminPanelHtml(): String {
        return """
<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>FutePlayer • Painel Web de Conteúdo</title>
  <style>
    :root {
      --primary: #00E676;
      --primary-hover: #00C853;
      --secondary: #00E5FF;
      --bg-dark: #0B132B;
      --surface: #161E2E;
      --surface-light: #1E293B;
      --border: rgba(0, 230, 118, 0.25);
      --text: #F8FAFC;
      --text-muted: #94A3B8;
      --danger: #FF1744;
      --warning: #FFD600;
    }
    * {
      box-sizing: border-box;
      margin: 0;
      padding: 0;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    }
    body {
      background-color: var(--bg-dark);
      color: var(--text);
      min-height: 100vh;
      display: flex;
      flex-direction: column;
    }
    header {
      background: linear-gradient(135deg, #0B132B 0%, #162447 100%);
      border-bottom: 1px solid var(--border);
      padding: 16px 24px;
      display: flex;
      align-items: center;
      justify-content: space-between;
      position: sticky;
      top: 0;
      z-index: 100;
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .logo-badge {
      width: 40px;
      height: 40px;
      border-radius: 12px;
      background: linear-gradient(135deg, var(--primary) 0%, #00B0FF 100%);
      display: flex;
      align-items: center;
      justify-content: center;
      font-weight: 900;
      font-size: 20px;
      color: #000;
    }
    .brand h1 {
      font-size: 20px;
      font-weight: 800;
      letter-spacing: -0.5px;
    }
    .brand span {
      color: var(--primary);
    }
    .status-badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      background: rgba(0, 230, 118, 0.15);
      border: 1px solid rgba(0, 230, 118, 0.4);
      padding: 6px 12px;
      border-radius: 20px;
      font-size: 13px;
      color: var(--primary);
      font-weight: 600;
    }
    .status-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--primary);
      box-shadow: 0 0 8px var(--primary);
    }
    main {
      max-width: 960px;
      width: 100%;
      margin: 0 auto;
      padding: 24px 16px;
      flex: 1;
    }
    .tabs {
      display: flex;
      gap: 8px;
      background: var(--surface);
      padding: 6px;
      border-radius: 14px;
      margin-bottom: 24px;
      border: 1px solid rgba(255, 255, 255, 0.05);
      overflow-x: auto;
    }
    .tab-btn {
      flex: 1;
      min-width: 130px;
      padding: 12px 16px;
      background: transparent;
      border: none;
      color: var(--text-muted);
      font-size: 14px;
      font-weight: 600;
      border-radius: 10px;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      transition: all 0.2s ease;
    }
    .tab-btn.active {
      background: var(--primary);
      color: #000;
      box-shadow: 0 4px 12px rgba(0, 230, 118, 0.3);
    }
    .card {
      background: var(--surface);
      border-radius: 18px;
      border: 1px solid var(--border);
      padding: 24px;
      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
      display: none;
      animation: fadeIn 0.3s ease;
    }
    .card.active {
      display: block;
    }
    @keyframes fadeIn {
      from { opacity: 0; transform: translateY(6px); }
      to { opacity: 1; transform: translateY(0); }
    }
    .card-title {
      font-size: 18px;
      font-weight: 700;
      margin-bottom: 8px;
      display: flex;
      align-items: center;
      gap: 10px;
      color: var(--primary);
    }
    .card-desc {
      font-size: 13px;
      color: var(--text-muted);
      margin-bottom: 20px;
      line-height: 1.5;
    }
    .form-group {
      margin-bottom: 16px;
    }
    .form-row {
      display: flex;
      gap: 16px;
      flex-wrap: wrap;
    }
    .form-row .form-group {
      flex: 1;
      min-width: 220px;
    }
    label {
      display: block;
      font-size: 13px;
      font-weight: 600;
      color: var(--text);
      margin-bottom: 6px;
    }
    label span.req {
      color: var(--primary);
    }
    input[type="text"], input[type="url"], input[type="number"], select, textarea {
      width: 100%;
      background: var(--surface-light);
      border: 1px solid rgba(255, 255, 255, 0.12);
      border-radius: 10px;
      padding: 12px 14px;
      color: #fff;
      font-size: 14px;
      transition: border-color 0.2s, box-shadow 0.2s;
    }
    input:focus, select:focus, textarea:focus {
      outline: none;
      border-color: var(--primary);
      box-shadow: 0 0 0 2px rgba(0, 230, 118, 0.2);
    }
    textarea {
      min-height: 80px;
      resize: vertical;
    }
    .checkbox-group {
      display: flex;
      align-items: center;
      gap: 10px;
      margin-top: 8px;
      background: var(--surface-light);
      padding: 10px 14px;
      border-radius: 10px;
      cursor: pointer;
    }
    .checkbox-group input {
      width: 18px;
      height: 18px;
      accent-color: var(--primary);
      cursor: pointer;
    }
    .btn-actions-row {
      display: flex;
      gap: 10px;
      margin-top: 14px;
    }
    .btn-submit {
      flex: 1;
      background: var(--primary);
      color: #000;
      border: none;
      padding: 14px 20px;
      border-radius: 12px;
      font-size: 15px;
      font-weight: 700;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      transition: background 0.2s, transform 0.1s;
    }
    .btn-submit:hover {
      background: var(--primary-hover);
    }
    .btn-cancel {
      background: rgba(255, 255, 255, 0.1);
      color: #fff;
      border: 1px solid rgba(255, 255, 255, 0.2);
      padding: 14px 20px;
      border-radius: 12px;
      font-size: 14px;
      font-weight: 600;
      cursor: pointer;
      display: none;
    }
    .notification {
      padding: 14px 16px;
      border-radius: 12px;
      margin-bottom: 20px;
      font-size: 14px;
      display: none;
      align-items: center;
      gap: 10px;
    }
    .notification.success {
      display: flex;
      background: rgba(0, 230, 118, 0.15);
      border: 1px solid var(--primary);
      color: #A7F3D0;
    }
    .notification.error {
      display: flex;
      background: rgba(255, 23, 68, 0.15);
      border: 1px solid var(--danger);
      color: #FECDD3;
    }
    .episodes-box {
      background: var(--surface-light);
      border: 1px dashed rgba(255, 255, 255, 0.15);
      padding: 16px;
      border-radius: 12px;
      margin-bottom: 16px;
    }
    .ep-row {
      display: flex;
      gap: 10px;
      margin-bottom: 10px;
      align-items: center;
    }
    .ep-row input {
      flex: 1;
    }
    .ep-row input.ep-num {
      width: 70px;
      flex: none;
    }
    .btn-remove-ep {
      background: rgba(255, 23, 68, 0.2);
      color: #FF5252;
      border: 1px solid rgba(255, 23, 68, 0.4);
      padding: 10px 14px;
      border-radius: 8px;
      cursor: pointer;
      font-weight: bold;
    }
    .btn-add-ep {
      background: rgba(0, 229, 255, 0.15);
      color: var(--secondary);
      border: 1px solid var(--secondary);
      padding: 10px 14px;
      border-radius: 8px;
      cursor: pointer;
      font-weight: 600;
      font-size: 13px;
    }
    .item-list {
      display: flex;
      flex-direction: column;
      gap: 12px;
      max-height: 520px;
      overflow-y: auto;
      padding-right: 4px;
    }
    .list-item {
      background: var(--surface-light);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 12px;
      padding: 14px 16px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
    }
    .list-item-title {
      font-weight: 700;
      font-size: 15px;
      color: #fff;
    }
    .list-item-sub {
      font-size: 12px;
      color: var(--text-muted);
      margin-top: 4px;
    }
    .item-actions {
      display: flex;
      gap: 8px;
      align-items: center;
    }
    .btn-sm-edit {
      background: rgba(0, 229, 255, 0.15);
      color: var(--secondary);
      border: 1px solid var(--secondary);
      padding: 6px 12px;
      border-radius: 8px;
      cursor: pointer;
      font-weight: 600;
      font-size: 12px;
    }
    .btn-sm-delete {
      background: rgba(255, 23, 68, 0.15);
      color: #FF5252;
      border: 1px solid rgba(255, 23, 68, 0.4);
      padding: 6px 10px;
      border-radius: 8px;
      cursor: pointer;
      font-size: 12px;
    }
    .editing-banner {
      background: rgba(0, 229, 255, 0.15);
      border: 1px solid var(--secondary);
      color: #BAE6FD;
      padding: 10px 14px;
      border-radius: 10px;
      font-size: 13px;
      margin-bottom: 16px;
      display: none;
      align-items: center;
      justify-content: space-between;
    }
  </style>
</head>
<body>

  <header>
    <div class="brand">
      <div class="logo-badge">FP</div>
      <div>
        <h1>Fute<span>Player</span> Web Admin</h1>
        <small style="color: var(--text-muted); font-size: 11px;">Gerenciamento de Conteúdo em Tempo Real</small>
      </div>
    </div>
    <div class="status-badge">
      <div class="status-dot"></div>
      <span>Conectado ao App</span>
    </div>
  </header>

  <main>
    <div id="notify" class="notification"></div>

    <div class="tabs">
      <button class="tab-btn active" onclick="switchTab('channels', this)">
        📺 Canais
      </button>
      <button class="tab-btn" onclick="switchTab('movies', this)">
        🎬 Filmes
      </button>
      <button class="tab-btn" onclick="switchTab('series', this)">
        🍿 Séries
      </button>
      <button class="tab-btn" onclick="switchTab('view', this)">
        📋 Catálogo no App
      </button>
    </div>

    <!-- ABA 1: CANAIS RÁPIDOS -->
    <div id="tab-channels" class="card active">
      <div id="ch-editing-banner" class="editing-banner">
        <span>✏️ Modo de Edição Ativo: você está alterando um canal existente.</span>
        <button type="button" class="btn-sm-edit" onclick="cancelChannelEdit()" style="background:#fff;color:#000;">Cancelar</button>
      </div>
      <div id="ch-card-title" class="card-title">📺 Adicionar / Editar Canal de TV ou Esporte</div>
      <div class="card-desc">
        Preencha os dados do canal. Ao salvar, as alterações aparecerão instantaneamente no aplicativo.
      </div>
      <form id="form-channel" onsubmit="submitChannel(event)">
        <input type="hidden" id="ch-id" value="">
        <div class="form-row">
          <div class="form-group" style="flex: 2;">
            <label>Nome / Título do Canal <span class="req">*</span></label>
            <input type="text" id="ch-title" placeholder="Ex: ESPN Brasil HD, Premiere 1, SportTV" required>
          </div>
          <div class="form-group" style="flex: 1;">
            <label>Categoria <span class="req">*</span></label>
            <input type="text" id="ch-category" placeholder="Ex: Esportes, Canais TV, Abertos" value="Esportes">
          </div>
        </div>

        <div class="form-group">
          <label>Link / URL do Stream (.m3u8, .mp4 ou Embed) <span class="req">*</span></label>
          <input type="url" id="ch-url" placeholder="https://exemplo.com/stream.m3u8 ou https://player.site.com/embed/123" required>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label>Subtítulo / Descrição</label>
            <input type="text" id="ch-subtitle" placeholder="Ex: Transmissão 1080p • 60fps">
          </div>
          <div class="form-group">
            <label>Link da Logo / Capa (Opcional)</label>
            <input type="url" id="ch-logo" placeholder="https://exemplo.com/logo.png">
          </div>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label class="checkbox-group">
              <input type="checkbox" id="ch-web">
              <span>Reproduzir via WebPlayer / WebView</span>
            </label>
          </div>
          <div class="form-group">
            <label class="checkbox-group">
              <input type="checkbox" id="ch-iframe">
              <span>Link é um código/página de Iframe</span>
            </label>
          </div>
        </div>

        <div class="btn-actions-row">
          <button type="button" id="ch-btn-cancel" class="btn-cancel" onclick="cancelChannelEdit()">Cancelar Edição</button>
          <button type="submit" id="ch-btn-submit" class="btn-submit">
            <span>+ Salvar Canal no Aplicativo</span>
          </button>
        </div>
      </form>
    </div>

    <!-- ABA 2: FILMES -->
    <div id="tab-movies" class="card">
      <div id="mv-editing-banner" class="editing-banner">
        <span>✏️ Modo de Edição Ativo: você está alterando um filme existente.</span>
        <button type="button" class="btn-sm-edit" onclick="cancelMovieEdit()" style="background:#fff;color:#000;">Cancelar</button>
      </div>
      <div id="mv-card-title" class="card-title">🎬 Adicionar / Editar Filme</div>
      <div class="card-desc">
        Cadastre ou edite filmes no catálogo do aplicativo com capa, sinopse e link de reprodução.
      </div>
      <form id="form-movie" onsubmit="submitMovie(event)">
        <input type="hidden" id="mv-id" value="">
        <div class="form-row">
          <div class="form-group" style="flex: 2;">
            <label>Título do Filme <span class="req">*</span></label>
            <input type="text" id="mv-title" placeholder="Ex: Velozes e Furiosos 10" required>
          </div>
          <div class="form-group" style="flex: 1;">
            <label>Gênero / Categoria <span class="req">*</span></label>
            <input type="text" id="mv-category" placeholder="Ex: Ação / Aventura" value="Ação">
          </div>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label>Ano de Lançamento</label>
            <input type="text" id="mv-year" placeholder="Ex: 2024" value="2024">
          </div>
          <div class="form-group">
            <label>Avaliação / Nota</label>
            <input type="text" id="mv-rating" placeholder="Ex: 8.5" value="8.5">
          </div>
        </div>

        <div class="form-group">
          <label>Link / URL do Filme (MP4, M3U8 ou Embed) <span class="req">*</span></label>
          <input type="url" id="mv-url" placeholder="https://servidor.com/filmes/filme.mp4" required>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label>URL da Capa / Pôster</label>
            <input type="url" id="mv-cover" placeholder="https://image.tmdb.org/t/p/w500/..." value="https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg">
          </div>
          <div class="form-group">
            <label>URL do Banner / Backdrop (Opcional)</label>
            <input type="url" id="mv-backdrop" placeholder="https://image.tmdb.org/t/p/w780/...">
          </div>
        </div>

        <div class="form-group">
          <label>Sinopse do Filme</label>
          <textarea id="mv-synopsis" placeholder="Breve resumo da história do filme..."></textarea>
        </div>

        <div class="form-group">
          <label class="checkbox-group">
            <input type="checkbox" id="mv-web">
            <span>Usar WebPlayer para este filme</span>
          </label>
        </div>

        <div class="btn-actions-row">
          <button type="button" id="mv-btn-cancel" class="btn-cancel" onclick="cancelMovieEdit()">Cancelar Edição</button>
          <button type="submit" id="mv-btn-submit" class="btn-submit">
            <span>+ Salvar Filme no Catálogo</span>
          </button>
        </div>
      </form>
    </div>

    <!-- ABA 3: SÉRIES -->
    <div id="tab-series" class="card">
      <div id="sr-editing-banner" class="editing-banner">
        <span>✏️ Modo de Edição Ativo: você está alterando uma série existente.</span>
        <button type="button" class="btn-sm-edit" onclick="cancelSeriesEdit()" style="background:#fff;color:#000;">Cancelar</button>
      </div>
      <div id="sr-card-title" class="card-title">🍿 Adicionar / Editar Série com Episódios</div>
      <div class="card-desc">
        Cadastre ou edite séries com temporadas e episódios para navegação e reprodução direta no app.
      </div>
      <form id="form-series" onsubmit="submitSeries(event)">
        <input type="hidden" id="sr-id" value="">
        <div class="form-row">
          <div class="form-group" style="flex: 2;">
            <label>Título da Série <span class="req">*</span></label>
            <input type="text" id="sr-title" placeholder="Ex: Stranger Things, Breaking Bad" required>
          </div>
          <div class="form-group" style="flex: 1;">
            <label>Gênero</label>
            <input type="text" id="sr-category" placeholder="Ex: Suspense / Ficção" value="Ficção">
          </div>
        </div>

        <div class="form-row">
          <div class="form-group">
            <label>Ano</label>
            <input type="text" id="sr-year" placeholder="Ex: 2024" value="2024">
          </div>
          <div class="form-group">
            <label>Nota</label>
            <input type="text" id="sr-rating" placeholder="Ex: 9.0" value="9.0">
          </div>
        </div>

        <div class="form-group">
          <label>URL da Capa da Série</label>
          <input type="url" id="sr-cover" placeholder="https://image.tmdb.org/t/p/w500/..." value="https://image.tmdb.org/t/p/w500/u3bZgnGQ9T01sWNhyveQz0wH0Hl.jpg">
        </div>

        <div class="form-group">
          <label>Sinopse da Série</label>
          <textarea id="sr-synopsis" placeholder="História da série..."></textarea>
        </div>

        <div class="episodes-box">
          <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom: 12px;">
            <label style="margin-bottom:0;">Temporada 1 • Episódios</label>
            <button type="button" class="btn-add-ep" onclick="addEpisodeRow()">+ Adicionar Episódio</button>
          </div>
          <div id="episodes-container">
            <div class="ep-row">
              <input type="number" class="ep-num" value="1" placeholder="Nº" title="Número do Episódio">
              <input type="text" class="ep-title" value="Episódio 1" placeholder="Título do Episódio">
              <input type="url" class="ep-url" placeholder="URL do Stream (.mp4/.m3u8)" required>
              <button type="button" class="btn-remove-ep" onclick="removeEpRow(this)">✕</button>
            </div>
          </div>
        </div>

        <div class="btn-actions-row">
          <button type="button" id="sr-btn-cancel" class="btn-cancel" onclick="cancelSeriesEdit()">Cancelar Edição</button>
          <button type="submit" id="sr-btn-submit" class="btn-submit">
            <span>+ Salvar Série com Episódios</span>
          </button>
        </div>
      </form>
    </div>

    <!-- ABA 4: ITENS NO APP (COM BOTÕES EDITAR E EXCLUIR) -->
    <div id="tab-view" class="card">
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom: 16px;">
        <div class="card-title" style="margin-bottom:0;">📋 Conteúdos Cadastrados</div>
        <button type="button" class="btn-add-ep" onclick="loadExistingData()">🔄 Atualizar Lista</button>
      </div>
      <div class="card-desc">Lista em tempo real dos canais, filmes e séries. Clique em ✏️ para editar ou 🗑️ para excluir.</div>
      <div id="items-container" class="item-list">
        <div style="text-align: center; color: var(--text-muted); padding: 30px;">Carregando itens...</div>
      </div>
    </div>
  </main>

  <script>
    let globalChannels = [];
    let globalMedia = [];

    function switchTab(tab, btnElement) {
      document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
      document.querySelectorAll('.card').forEach(card => card.classList.remove('active'));
      
      if (btnElement) {
        btnElement.classList.add('active');
      } else {
        const matching = Array.from(document.querySelectorAll('.tab-btn')).find(b => b.getAttribute('onclick')?.includes(tab));
        if (matching) matching.classList.add('active');
      }
      
      document.getElementById('tab-' + tab).classList.add('active');

      if (tab === 'view') {
        loadExistingData();
      }
    }

    function showNotify(msg, isSuccess = true) {
      const el = document.getElementById('notify');
      el.className = 'notification ' + (isSuccess ? 'success' : 'error');
      el.innerHTML = (isSuccess ? '✅ ' : '⚠️ ') + msg;
      el.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      setTimeout(() => {
        el.style.display = 'none';
      }, 6000);
    }

    function addEpisodeRow(num = null, title = null, url = '') {
      const container = document.getElementById('episodes-container');
      const count = num || (container.querySelectorAll('.ep-row').length + 1);
      const row = document.createElement('div');
      row.className = 'ep-row';
      row.innerHTML = `
        <input type="number" class="ep-num" value="${'$'}{count}" placeholder="Nº" title="Número do Episódio">
        <input type="text" class="ep-title" value="${'$'}{title || ('Episódio ' + count)}" placeholder="Título do Episódio">
        <input type="url" class="ep-url" value="${'$'}{url}" placeholder="URL do Stream (.mp4/.m3u8)" required>
        <button type="button" class="btn-remove-ep" onclick="removeEpRow(this)">✕</button>
      `;
      container.appendChild(row);
    }

    function removeEpRow(btn) {
      const container = document.getElementById('episodes-container');
      if (container.querySelectorAll('.ep-row').length > 1) {
        btn.parentElement.remove();
      } else {
        alert('A série precisa ter pelo menos 1 episódio!');
      }
    }

    // ==========================================
    // CANAIS: ADICIONAR, EDITAR E EXCLUIR
    // ==========================================
    function editChannel(id) {
      const ch = globalChannels.find(c => c.id === id);
      if (!ch) return;

      document.getElementById('ch-id').value = ch.id;
      document.getElementById('ch-title').value = ch.title || '';
      document.getElementById('ch-url').value = ch.streamUrl || '';
      document.getElementById('ch-category').value = ch.category || 'Esportes';
      document.getElementById('ch-subtitle').value = ch.subtitle || '';
      document.getElementById('ch-logo').value = ch.posterUrl || '';
      document.getElementById('ch-web').checked = !!ch.isWebPlayer;
      document.getElementById('ch-iframe').checked = !!ch.isIframe;

      document.getElementById('ch-editing-banner').style.display = 'flex';
      document.getElementById('ch-card-title').textContent = '✏️ Editando Canal: ' + ch.title;
      document.getElementById('ch-btn-submit').innerHTML = '<span>💾 Salvar Alterações no Canal</span>';
      document.getElementById('ch-btn-cancel').style.display = 'block';

      switchTab('channels');
    }

    function cancelChannelEdit() {
      document.getElementById('ch-id').value = '';
      document.getElementById('ch-title').value = '';
      document.getElementById('ch-url').value = '';
      document.getElementById('ch-subtitle').value = '';
      document.getElementById('ch-logo').value = '';
      document.getElementById('ch-web').checked = false;
      document.getElementById('ch-iframe').checked = false;

      document.getElementById('ch-editing-banner').style.display = 'none';
      document.getElementById('ch-card-title').textContent = '📺 Adicionar / Editar Canal de TV ou Esporte';
      document.getElementById('ch-btn-submit').innerHTML = '<span>+ Salvar Canal no Aplicativo</span>';
      document.getElementById('ch-btn-cancel').style.display = 'none';
    }

    async function deleteChannel(id, title) {
      if (!confirm('Deseja realmente excluir o canal "' + title + '"?')) return;
      try {
        const resp = await fetch('/api/channels/delete', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ id: id })
        });
        const data = await resp.json();
        if (data.success) {
          showNotify('Canal "' + title + '" excluído com sucesso!');
          loadExistingData();
        } else {
          showNotify(data.error || 'Erro ao excluir canal', false);
        }
      } catch (err) {
        showNotify('Erro ao excluir: ' + err.message, false);
      }
    }

    async function submitChannel(e) {
      e.preventDefault();
      const id = document.getElementById('ch-id').value.trim();
      const payload = {
        id: id || undefined,
        title: document.getElementById('ch-title').value.trim(),
        streamUrl: document.getElementById('ch-url').value.trim(),
        category: document.getElementById('ch-category').value.trim() || 'Esportes',
        subtitle: document.getElementById('ch-subtitle').value.trim() || 'Canal Adicionado via Web',
        posterUrl: document.getElementById('ch-logo').value.trim() || null,
        isWebPlayer: document.getElementById('ch-web').checked,
        isIframe: document.getElementById('ch-iframe').checked,
        isWorking: true
      };

      try {
        const resp = await fetch('/api/channels', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });
        const data = await resp.json();
        if (data.success) {
          showNotify(data.message || 'Canal salvo com sucesso!');
          cancelChannelEdit();
        } else {
          showNotify(data.error || 'Erro ao salvar canal', false);
        }
      } catch (err) {
        showNotify('Erro de conexão ao enviar canal: ' + err.message, false);
      }
    }

    // ==========================================
    // FILMES: ADICIONAR, EDITAR E EXCLUIR
    // ==========================================
    function editMovie(id) {
      const mv = globalMedia.find(m => m.id === id);
      if (!mv) return;

      document.getElementById('mv-id').value = mv.id;
      document.getElementById('mv-title').value = mv.title || '';
      document.getElementById('mv-url').value = mv.movieStreamUrl || '';
      document.getElementById('mv-category').value = mv.category || 'Ação';
      document.getElementById('mv-year').value = mv.year || '2024';
      document.getElementById('mv-rating').value = mv.rating || '8.5';
      document.getElementById('mv-cover').value = mv.coverUrl || '';
      document.getElementById('mv-backdrop').value = mv.backdropUrl || '';
      document.getElementById('mv-synopsis').value = mv.synopsis || '';
      document.getElementById('mv-web').checked = !!mv.isWebPlayer;

      document.getElementById('mv-editing-banner').style.display = 'flex';
      document.getElementById('mv-card-title').textContent = '✏️ Editando Filme: ' + mv.title;
      document.getElementById('mv-btn-submit').innerHTML = '<span>💾 Salvar Alterações no Filme</span>';
      document.getElementById('mv-btn-cancel').style.display = 'block';

      switchTab('movies');
    }

    function cancelMovieEdit() {
      document.getElementById('mv-id').value = '';
      document.getElementById('mv-title').value = '';
      document.getElementById('mv-url').value = '';
      document.getElementById('mv-synopsis').value = '';
      document.getElementById('mv-backdrop').value = '';
      document.getElementById('mv-web').checked = false;

      document.getElementById('mv-editing-banner').style.display = 'none';
      document.getElementById('mv-card-title').textContent = '🎬 Adicionar / Editar Filme';
      document.getElementById('mv-btn-submit').innerHTML = '<span>+ Salvar Filme no Catálogo</span>';
      document.getElementById('mv-btn-cancel').style.display = 'none';
    }

    async function deleteMediaItem(id, title, typeLabel) {
      if (!confirm('Deseja realmente excluir ' + typeLabel + ' "' + title + '"?')) return;
      try {
        const resp = await fetch('/api/media/delete', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ id: id })
        });
        const data = await resp.json();
        if (data.success) {
          showNotify(typeLabel + ' "' + title + '" excluído com sucesso!');
          loadExistingData();
        } else {
          showNotify(data.error || 'Erro ao excluir', false);
        }
      } catch (err) {
        showNotify('Erro ao excluir: ' + err.message, false);
      }
    }

    async function submitMovie(e) {
      e.preventDefault();
      const id = document.getElementById('mv-id').value.trim();
      const payload = {
        id: id || undefined,
        title: document.getElementById('mv-title').value.trim(),
        movieStreamUrl: document.getElementById('mv-url').value.trim(),
        category: document.getElementById('mv-category').value.trim() || 'Ação',
        year: document.getElementById('mv-year').value.trim() || '2024',
        rating: document.getElementById('mv-rating').value.trim() || '8.5',
        coverUrl: document.getElementById('mv-cover').value.trim(),
        backdropUrl: document.getElementById('mv-backdrop').value.trim() || null,
        synopsis: document.getElementById('mv-synopsis').value.trim(),
        isWebPlayer: document.getElementById('mv-web').checked
      };

      try {
        const resp = await fetch('/api/movies', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });
        const data = await resp.json();
        if (data.success) {
          showNotify(data.message || 'Filme salvo com sucesso!');
          cancelMovieEdit();
        } else {
          showNotify(data.error || 'Erro ao salvar filme', false);
        }
      } catch (err) {
        showNotify('Erro de conexão ao enviar filme: ' + err.message, false);
      }
    }

    // ==========================================
    // SÉRIES: ADICIONAR, EDITAR E EXCLUIR
    // ==========================================
    function editSeries(id) {
      const sr = globalMedia.find(m => m.id === id);
      if (!sr) return;

      document.getElementById('sr-id').value = sr.id;
      document.getElementById('sr-title').value = sr.title || '';
      document.getElementById('sr-category').value = sr.category || 'Ficção';
      document.getElementById('sr-year').value = sr.year || '2024';
      document.getElementById('sr-rating').value = sr.rating || '9.0';
      document.getElementById('sr-cover').value = sr.coverUrl || '';
      document.getElementById('sr-synopsis').value = sr.synopsis || '';

      const container = document.getElementById('episodes-container');
      container.innerHTML = '';

      const firstSeason = (sr.seasons && sr.seasons.length) ? sr.seasons[0] : null;
      if (firstSeason && firstSeason.episodes && firstSeason.episodes.length) {
        firstSeason.episodes.forEach(ep => {
          addEpisodeRow(ep.episodeNumber, ep.title, ep.streamUrl);
        });
      } else {
        addEpisodeRow(1, 'Episódio 1', '');
      }

      document.getElementById('sr-editing-banner').style.display = 'flex';
      document.getElementById('sr-card-title').textContent = '✏️ Editando Série: ' + sr.title;
      document.getElementById('sr-btn-submit').innerHTML = '<span>💾 Salvar Alterações na Série</span>';
      document.getElementById('sr-btn-cancel').style.display = 'block';

      switchTab('series');
    }

    function cancelSeriesEdit() {
      document.getElementById('sr-id').value = '';
      document.getElementById('sr-title').value = '';
      document.getElementById('sr-synopsis').value = '';

      const container = document.getElementById('episodes-container');
      container.innerHTML = '';
      addEpisodeRow(1, 'Episódio 1', '');

      document.getElementById('sr-editing-banner').style.display = 'none';
      document.getElementById('sr-card-title').textContent = '🍿 Adicionar / Editar Série com Episódios';
      document.getElementById('sr-btn-submit').innerHTML = '<span>+ Salvar Série com Episódios</span>';
      document.getElementById('sr-btn-cancel').style.display = 'none';
    }

    async function submitSeries(e) {
      e.preventDefault();
      const id = document.getElementById('sr-id').value.trim();
      const rows = document.querySelectorAll('#episodes-container .ep-row');
      const episodes = [];
      rows.forEach(r => {
        const num = parseInt(r.querySelector('.ep-num').value) || 1;
        const title = r.querySelector('.ep-title').value.trim() || ('Episódio ' + num);
        const url = r.querySelector('.ep-url').value.trim();
        if (url) {
          episodes.push({ episodeNumber: num, title: title, streamUrl: url });
        }
      });

      if (episodes.length === 0) {
        showNotify('Adicione ao menos um episódio com link de vídeo!', false);
        return;
      }

      const payload = {
        id: id || undefined,
        title: document.getElementById('sr-title').value.trim(),
        category: document.getElementById('sr-category').value.trim() || 'Séries',
        year: document.getElementById('sr-year').value.trim() || '2024',
        rating: document.getElementById('sr-rating').value.trim() || '9.0',
        coverUrl: document.getElementById('sr-cover').value.trim(),
        synopsis: document.getElementById('sr-synopsis').value.trim(),
        seasons: [
          {
            seasonNumber: 1,
            title: 'Temporada 1',
            episodes: episodes
          }
        ]
      };

      try {
        const resp = await fetch('/api/series', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload)
        });
        const data = await resp.json();
        if (data.success) {
          showNotify(data.message || 'Série salva com sucesso!');
          cancelSeriesEdit();
        } else {
          showNotify(data.error || 'Erro ao salvar série', false);
        }
      } catch (err) {
        showNotify('Erro de conexão ao enviar série: ' + err.message, false);
      }
    }

    // ==========================================
    // CARREGAR DADOS DO APLICATIVO
    // ==========================================
    async function loadExistingData() {
      const container = document.getElementById('items-container');
      container.innerHTML = '<div style="text-align: center; color: var(--text-muted); padding: 30px;">Carregando itens...</div>';
      try {
        const resp = await fetch('/api/data');
        const data = await resp.json();
        globalChannels = data.channels || [];
        globalMedia = data.media || [];
        let html = '';

        if (globalChannels.length === 0 && globalMedia.length === 0) {
          container.innerHTML = '<div style="text-align: center; color: var(--text-muted); padding: 30px;">Nenhum item cadastrado ainda. Adicione nas abas acima!</div>';
          return;
        }

        if (globalChannels.length > 0) {
          html += '<div style="font-weight: bold; color: var(--primary); margin: 8px 0 6px 0;">📺 Canais Cadastrados (' + globalChannels.length + ')</div>';
          globalChannels.forEach(ch => {
            html += `
              <div class="list-item">
                <div style="flex: 1;">
                  <div class="list-item-title">${'$'}{ch.title}</div>
                  <div class="list-item-sub">${'$'}{ch.category} • ${'$'}{ch.subtitle || ''}</div>
                </div>
                <div class="item-actions">
                  <button type="button" class="btn-sm-edit" onclick="editChannel('${'$'}{ch.id}')">✏️ Editar</button>
                  <button type="button" class="btn-sm-delete" onclick="deleteChannel('${'$'}{ch.id}', '${'$'}{ch.title}')" title="Excluir Canal">🗑️</button>
                </div>
              </div>
            `;
          });
        }

        if (globalMedia.length > 0) {
          const movies = globalMedia.filter(m => m.type === 'MOVIE');
          const series = globalMedia.filter(m => m.type === 'SERIES');

          if (movies.length > 0) {
            html += '<div style="font-weight: bold; color: var(--secondary); margin: 20px 0 6px 0;">🎬 Filmes (' + movies.length + ')</div>';
            movies.forEach(m => {
              html += `
                <div class="list-item">
                  <div style="flex: 1;">
                    <div class="list-item-title">${'$'}{m.title}</div>
                    <div class="list-item-sub">${'$'}{m.category} • ${'$'}{m.year} • ★ ${'$'}{m.rating || '8.5'}</div>
                  </div>
                  <div class="item-actions">
                    <button type="button" class="btn-sm-edit" onclick="editMovie('${'$'}{m.id}')">✏️ Editar</button>
                    <button type="button" class="btn-sm-delete" onclick="deleteMediaItem('${'$'}{m.id}', '${'$'}{m.title}', 'o filme')" title="Excluir Filme">🗑️</button>
                  </div>
                </div>
              `;
            });
          }

          if (series.length > 0) {
            html += '<div style="font-weight: bold; color: var(--warning); margin: 20px 0 6px 0;">🍿 Séries (' + series.length + ')</div>';
            series.forEach(s => {
              html += `
                <div class="list-item">
                  <div style="flex: 1;">
                    <div class="list-item-title">${'$'}{s.title}</div>
                    <div class="list-item-sub">${'$'}{s.category} • ${'$'}{s.year} • ${'$'}{s.totalEpisodes || 1} episódio(s)</div>
                  </div>
                  <div class="item-actions">
                    <button type="button" class="btn-sm-edit" onclick="editSeries('${'$'}{s.id}')">✏️ Editar</button>
                    <button type="button" class="btn-sm-delete" onclick="deleteMediaItem('${'$'}{s.id}', '${'$'}{s.title}', 'a série')" title="Excluir Série">🗑️</button>
                  </div>
                </div>
              `;
            });
          }
        }

        container.innerHTML = html;
      } catch (err) {
        container.innerHTML = '<div style="text-align: center; color: var(--danger); padding: 20px;">Não foi possível carregar dados: ' + err.message + '</div>';
      }
    }
  </script>
</body>
</html>
        """.trimIndent()
    }
}
