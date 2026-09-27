package com.example.server

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.data.FutemaisRepository
import com.example.data.models.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.*

/**
 * Servidor HTTP Embutido no FutePlayer para Gerenciamento Web no Navegador.
 * Permite ao usuário/administrador abrir uma página da web no navegador do celular ou do PC
 * para adicionar canais, filmes e séries em tempo real, refletindo imediatamente no aplicativo.
 */
class WebAdminServer private constructor(
    private val context: Context,
    private val repository: FutemaisRepository
) {
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
            // Tenta obter via WifiManager
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

            // Fallback para NetworkInterface
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
     * Retorna a URL para abrir no navegador do próprio aparelho (localhost)
     * ou no navegador do PC (via IP local da rede).
     */
    fun getWebUrl(forExternalDevice: Boolean = false): String {
        val ip = if (forExternalDevice) getLocalIpAddress() ?: "localhost" else "localhost"
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

            // Tenta vincular na porta padrão ou subsequentes
            for (offset in 0..5) {
                try {
                    socket = ServerSocket(port + offset)
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

            // Lê os headers
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

            // Lê o corpo caso seja POST
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

            // Tratamento de CORS Preflight (OPTIONS)
            if (method == "OPTIONS") {
                sendResponse(output, 204, "No Content", "text/plain", ByteArray(0), addCors = true)
                socket.close()
                return
            }

            when {
                // Página principal do Painel Web
                method == "GET" && (path == "/" || path == "/index.html" || path == "/admin") -> {
                    val html = getAdminPanelHtml()
                    val bytes = html.toByteArray(Charsets.UTF_8)
                    sendResponse(output, 200, "OK", "text/html; charset=UTF-8", bytes, addCors = true)
                }

                // API: Status do Servidor
                method == "GET" && path == "/api/status" -> {
                    val json = JSONObject().apply {
                        put("status", "online")
                        put("port", activePort)
                        put("localIp", getLocalIpAddress() ?: "localhost")
                        put("appVersion", "1.3.0")
                        put("timestamp", System.currentTimeMillis())
                    }
                    val bytes = json.toString().toByteArray(Charsets.UTF_8)
                    sendResponse(output, 200, "OK", "application/json; charset=UTF-8", bytes, addCors = true)
                }

                // API: Lista atual de canais e catálogo para visualização no navegador
                method == "GET" && path == "/api/data" -> {
                    val channels = repository.getQuickChannels()
                    val media = repository.getMediaCatalog()

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
                        })
                    }

                    val mediaJson = JSONArray()
                    media.forEach { m ->
                        mediaJson.put(JSONObject().apply {
                            put("id", m.id)
                            put("title", m.title)
                            put("type", m.type.name)
                            put("category", m.category)
                            put("year", m.year)
                            put("coverUrl", m.coverUrl)
                            put("totalEpisodes", m.totalEpisodes)
                        })
                    }

                    val root = JSONObject().apply {
                        put("channels", channelsJson)
                        put("media", mediaJson)
                        put("channelCount", channels.size)
                        put("mediaCount", media.size)
                    }
                    val bytes = root.toString().toByteArray(Charsets.UTF_8)
                    sendResponse(output, 200, "OK", "application/json; charset=UTF-8", bytes, addCors = true)
                }

                // API: Adicionar Canal Rápido
                method == "POST" && path == "/api/channels" -> {
                    val resp = handleAddChannel(body)
                    val bytes = resp.toString().toByteArray(Charsets.UTF_8)
                    sendResponse(output, 200, "OK", "application/json; charset=UTF-8", bytes, addCors = true)
                    notifyDataChanged()
                }

                // API: Adicionar Filme
                method == "POST" && path == "/api/movies" -> {
                    val resp = handleAddMovie(body)
                    val bytes = resp.toString().toByteArray(Charsets.UTF_8)
                    sendResponse(output, 200, "OK", "application/json; charset=UTF-8", bytes, addCors = true)
                    notifyDataChanged()
                }

                // API: Adicionar Série
                method == "POST" && path == "/api/series" -> {
                    val resp = handleAddSeries(body)
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

    private fun handleAddChannel(jsonBody: String): JSONObject {
        val root = JSONObject(jsonBody)
        val title = root.optString("title").trim()
        val url = root.optString("streamUrl").ifBlank { root.optString("url") }.trim()
        val subtitle = root.optString("subtitle").ifBlank { "Canal Adicionado via Painel Web" }.trim()
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

        val id = "custom_${System.currentTimeMillis()}"
        val newChannel = PlayableVideo(
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

        repository.addCustomChannel(newChannel)

        return JSONObject().apply {
            put("success", true)
            put("message", "Canal '$title' adicionado com sucesso e sincronizado no aplicativo!")
            put("id", id)
        }
    }

    private fun handleAddMovie(jsonBody: String): JSONObject {
        val root = JSONObject(jsonBody)
        val title = root.optString("title").trim()
        val streamUrl = root.optString("movieStreamUrl").ifBlank { root.optString("streamUrl") }.trim()
        val category = root.optString("category").ifBlank { "Geral" }.trim()
        val coverUrl = root.optString("coverUrl").ifBlank {
            "https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg"
        }.trim()
        val backdropUrl = root.optString("backdropUrl").takeIf { it.isNotBlank() }
        val synopsis = root.optString("synopsis").ifBlank { "Filme adicionado pelo Painel Web." }.trim()
        val year = root.optString("year").ifBlank { "2024" }.trim()
        val rating = root.optString("rating").ifBlank { "8.5" }.trim()
        val isWebPlayer = root.optBoolean("isWebPlayer", false)

        if (title.isBlank() || streamUrl.isBlank()) {
            return JSONObject().apply {
                put("success", false)
                put("error", "Título e Link do Filme são obrigatórios!")
            }
        }

        val id = "movie_${System.currentTimeMillis()}"
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
            put("message", "Filme '$title' adicionado com sucesso e já disponível no aplicativo!")
            put("id", id)
        }
    }

    private fun handleAddSeries(jsonBody: String): JSONObject {
        val root = JSONObject(jsonBody)
        val title = root.optString("title").trim()
        val category = root.optString("category").ifBlank { "Séries" }.trim()
        val coverUrl = root.optString("coverUrl").ifBlank {
            "https://image.tmdb.org/t/p/w500/u3bZgnGQ9T01sWNhyveQz0wH0Hl.jpg"
        }.trim()
        val backdropUrl = root.optString("backdropUrl").takeIf { it.isNotBlank() }
        val synopsis = root.optString("synopsis").ifBlank { "Série adicionada pelo Painel Web." }.trim()
        val year = root.optString("year").ifBlank { "2024" }.trim()
        val rating = root.optString("rating").ifBlank { "9.0" }.trim()

        if (title.isBlank()) {
            return JSONObject().apply {
                put("success", false)
                put("error", "Título da série é obrigatório!")
            }
        }

        // Lê temporadas e episódios
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

                        if (epUrl.isNotBlank()) {
                            epList.add(
                                EpisodeItem(
                                    id = "ep_${System.currentTimeMillis()}_${seasonNum}_$epNum",
                                    episodeNumber = epNum,
                                    title = epTitle,
                                    streamUrl = epUrl,
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

        // Se o usuário não enviou array estruturado, mas colocou um streamUrl simples de episódio piloto
        if (seasonsList.isEmpty()) {
            val simpleUrl = root.optString("streamUrl").ifBlank { root.optString("movieStreamUrl") }.trim()
            if (simpleUrl.isNotBlank()) {
                seasonsList.add(
                    SeasonItem(
                        seasonNumber = 1,
                        title = "Temporada 1",
                        episodes = listOf(
                            EpisodeItem(
                                id = "ep_${System.currentTimeMillis()}_1_1",
                                episodeNumber = 1,
                                title = "Episódio 1",
                                streamUrl = simpleUrl
                            )
                        )
                    )
                )
            }
        }

        val id = "series_${System.currentTimeMillis()}"
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
            put("message", "Série '$title' com ${seriesItem.totalEpisodes} episódio(s) adicionada com sucesso!")
            put("id", id)
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
            writer.print("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
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
     * Retorna a página HTML do Painel Web Admin Completo, responsivo e com suporte a dual-sync.
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
      max-width: 900px;
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
      padding: 12px 14px;
      background: var(--surface-light);
      border: 1px solid rgba(255, 255, 255, 0.1);
      border-radius: 10px;
      color: var(--text);
      font-size: 14px;
      transition: border-color 0.2s;
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
    .btn-submit {
      width: 100%;
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
      margin-top: 10px;
    }
    .btn-submit:hover {
      background: var(--primary-hover);
    }
    .btn-submit:active {
      transform: scale(0.99);
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
      max-height: 480px;
      overflow-y: auto;
      padding-right: 4px;
    }
    .list-item {
      background: var(--surface-light);
      border: 1px solid rgba(255, 255, 255, 0.08);
      border-radius: 12px;
      padding: 12px 16px;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .list-item-title {
      font-weight: 600;
      font-size: 14px;
    }
    .list-item-sub {
      font-size: 12px;
      color: var(--text-muted);
      margin-top: 2px;
    }
    .list-badge {
      font-size: 11px;
      padding: 4px 8px;
      border-radius: 6px;
      background: rgba(0, 230, 118, 0.2);
      color: var(--primary);
      font-weight: 600;
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
      <button class="tab-btn active" onclick="switchTab('channels')">
        📺 Adicionar Canal
      </button>
      <button class="tab-btn" onclick="switchTab('movies')">
        🎬 Adicionar Filme
      </button>
      <button class="tab-btn" onclick="switchTab('series')">
        🍿 Adicionar Série
      </button>
      <button class="tab-btn" onclick="switchTab('view')">
        📋 Itens no App
      </button>
    </div>

    <!-- ABA 1: CANAIS RÁPIDOS -->
    <div id="tab-channels" class="card active">
      <div class="card-title">📺 Adicionar Novo Canal de TV / Esporte</div>
      <div class="card-desc">
        Preencha os dados do canal. Ao clicar em adicionar, o canal será salvo imediatamente no aplicativo e disponibilizado na aba Canais Rápidos.
      </div>
      <form id="form-channel" onsubmit="submitChannel(event)">
        <div class="form-row">
          <div class="form-group" style="flex: 2;">
            <label>Nome / Título do Canal <span class="req">*</span></label>
            <input type="text" id="ch-title" placeholder="Ex: ESPN Brasil HD, Premiere 1, SportTV" required>
          </div>
          <div class="form-group" style="flex: 1;">
            <label>Categoria <span class="req">*</span></label>
            <select id="ch-category">
              <option value="Esportes">Esportes</option>
              <option value="Canais TV">Canais TV</option>
              <option value="Abertos">Abertos</option>
              <option value="Filmes e Séries">Filmes e Séries</option>
              <option value="Notícias">Notícias</option>
              <option value="Infantil">Infantil</option>
              <option value="Variedades">Variedades</option>
            </select>
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

        <button type="submit" class="btn-submit">
          <span>+ Adicionar Canal ao Aplicativo</span>
        </button>
      </form>
    </div>

    <!-- ABA 2: FILMES -->
    <div id="tab-movies" class="card">
      <div class="card-title">🎬 Adicionar Novo Filme</div>
      <div class="card-desc">
        Cadastre filmes no catálogo do aplicativo. O filme aparecerá imediatamente na página de Filmes & Séries.
      </div>
      <form id="form-movie" onsubmit="submitMovie(event)">
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
            <label>Avaliação / Nota (IMDb)</label>
            <input type="text" id="mv-rating" placeholder="Ex: 8.4" value="8.5">
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

        <button type="submit" class="btn-submit">
          <span>+ Adicionar Filme ao Catálogo</span>
        </button>
      </form>
    </div>

    <!-- ABA 3: SÉRIES -->
    <div id="tab-series" class="card">
      <div class="card-title">🍿 Adicionar Nova Série com Episódios</div>
      <div class="card-desc">
        Cadastre séries com múltiplas temporadas e episódios para navegação e reprodução direta no app.
      </div>
      <form id="form-series" onsubmit="submitSeries(event)">
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
            <input type="text" id="sr-rating" placeholder="Ex: 9.1" value="9.0">
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

        <button type="submit" class="btn-submit">
          <span>+ Adicionar Série com Episódios</span>
        </button>
      </form>
    </div>

    <!-- ABA 4: ITENS NO APP -->
    <div id="tab-view" class="card">
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom: 16px;">
        <div class="card-title" style="margin-bottom:0;">📋 Conteúdos Cadastrados</div>
        <button type="button" class="btn-add-ep" onclick="loadExistingData()">🔄 Atualizar Lista</button>
      </div>
      <div class="card-desc">Lista em tempo real dos canais e itens sincronizados com o aplicativo.</div>
      <div id="items-container" class="item-list">
        <div style="text-align: center; color: var(--text-muted); padding: 30px;">Carregando itens...</div>
      </div>
    </div>
  </main>

  <script>
    function switchTab(tab) {
      document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
      document.querySelectorAll('.card').forEach(card => card.classList.remove('active'));
      
      event.currentTarget.classList.add('active');
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

    function addEpisodeRow() {
      const container = document.getElementById('episodes-container');
      const count = container.querySelectorAll('.ep-row').length + 1;
      const row = document.createElement('div');
      row.className = 'ep-row';
      row.innerHTML = `
        <input type="number" class="ep-num" value="${'$'}{count}" placeholder="Nº" title="Número do Episódio">
        <input type="text" class="ep-title" value="Episódio ${'$'}{count}" placeholder="Título do Episódio">
        <input type="url" class="ep-url" placeholder="URL do Stream (.mp4/.m3u8)" required>
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

    async function submitChannel(e) {
      e.preventDefault();
      const payload = {
        title: document.getElementById('ch-title').value.trim(),
        streamUrl: document.getElementById('ch-url').value.trim(),
        category: document.getElementById('ch-category').value,
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
          showNotify(data.message || 'Canal adicionado com sucesso!');
          document.getElementById('ch-title').value = '';
          document.getElementById('ch-url').value = '';
          document.getElementById('ch-subtitle').value = '';
          document.getElementById('ch-logo').value = '';
        } else {
          showNotify(data.error || 'Erro ao adicionar canal', false);
        }
      } catch (err) {
        showNotify('Erro de conexão ao enviar canal: ' + err.message, false);
      }
    }

    async function submitMovie(e) {
      e.preventDefault();
      const payload = {
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
          showNotify(data.message || 'Filme adicionado com sucesso!');
          document.getElementById('mv-title').value = '';
          document.getElementById('mv-url').value = '';
          document.getElementById('mv-synopsis').value = '';
        } else {
          showNotify(data.error || 'Erro ao adicionar filme', false);
        }
      } catch (err) {
        showNotify('Erro de conexão ao enviar filme: ' + err.message, false);
      }
    }

    async function submitSeries(e) {
      e.preventDefault();
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
          showNotify(data.message || 'Série adicionada com sucesso!');
          document.getElementById('sr-title').value = '';
          document.getElementById('sr-synopsis').value = '';
        } else {
          showNotify(data.error || 'Erro ao adicionar série', false);
        }
      } catch (err) {
        showNotify('Erro de conexão ao enviar série: ' + err.message, false);
      }
    }

    async function loadExistingData() {
      const container = document.getElementById('items-container');
      container.innerHTML = '<div style="text-align: center; color: var(--text-muted); padding: 30px;">Carregando itens...</div>';
      try {
        const resp = await fetch('/api/data');
        const data = await resp.json();
        let html = '';

        if ((!data.channels || data.channels.length === 0) && (!data.media || data.media.length === 0)) {
          container.innerHTML = '<div style="text-align: center; color: var(--text-muted); padding: 30px;">Nenhum item cadastrado ainda.</div>';
          return;
        }

        if (data.channels && data.channels.length > 0) {
          html += '<div style="font-weight: bold; color: var(--primary); margin: 8px 0 4px 0;">📺 Canais Rápidos (' + data.channels.length + ')</div>';
          data.channels.forEach(ch => {
            html += `
              <div class="list-item">
                <div>
                  <div class="list-item-title">${'$'}{ch.title}</div>
                  <div class="list-item-sub">${'$'}{ch.category} • ${'$'}{ch.subtitle || ''}</div>
                </div>
                <div class="list-badge">No App</div>
              </div>
            `;
          });
        }

        if (data.media && data.media.length > 0) {
          html += '<div style="font-weight: bold; color: var(--secondary); margin: 16px 0 4px 0;">🎬 Filmes e Séries (' + data.media.length + ')</div>';
          data.media.forEach(m => {
            const isSeries = m.type === 'SERIES';
            html += `
              <div class="list-item">
                <div>
                  <div class="list-item-title">${'$'}{m.title}</div>
                  <div class="list-item-sub">${'$'}{m.category} • ${'$'}{m.year} ${'$'}{isSeries ? '• ' + m.totalEpisodes + ' ep(s)' : ''}</div>
                </div>
                <div class="list-badge" style="background: rgba(0, 229, 255, 0.2); color: var(--secondary);">${'$'}{isSeries ? 'Série' : 'Filme'}</div>
              </div>
            `;
          });
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
