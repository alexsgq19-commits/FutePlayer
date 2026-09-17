import java.net.HttpURLConnection
import java.net.URL
import java.io.InputStreamReader
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.URLEncoder

fun main() {
    val embedplayUrl = "https://embedplayapi.top/embed/1100782"
    var result: String? = null
    try {
        println("Step 1")
        val conn1 = URL(embedplayUrl).openConnection() as HttpURLConnection
        conn1.setRequestProperty("User-Agent", "Mozilla/5.0")
        val text1 = conn1.inputStream.bufferedReader().readText()
        
        val serverId = """class="server dropdown-item"\s+data-id="([^"]+)"""".toRegex().find(text1)?.groupValues?.get(1)
        val movieId = """data-movie-id="([^"]+)"""".toRegex().find(text1)?.groupValues?.get(1)
        
        println("ServerId: $serverId, MovieId: $movieId")
        if (serverId != null && movieId != null) {
            println("Step 2")
            val url2 = "https://embedplayapi.top/ajax/get_stream_link?id=$serverId&movie=$movieId&is_init=false"
            val conn2 = URL(url2).openConnection() as HttpURLConnection
            conn2.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn2.setRequestProperty("X-Requested-With", "XMLHttpRequest")
            val text2 = conn2.inputStream.bufferedReader().readText()
            println("Text2: $text2")
            val link = """"link":"([^"]+)"""".toRegex().find(text2)?.groupValues?.get(1)
            
            if (link != null) {
                println("Step 3")
                val conn3 = URL(link).openConnection() as HttpURLConnection
                conn3.setRequestProperty("User-Agent", "Mozilla/5.0")
                val text3 = conn3.inputStream.bufferedReader().readText()
                
                var playerId = """data-id="([^"]+)">(?:.(?!data-id))*?ABYS""".toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).find(text3)?.groupValues?.get(1)
                if (playerId == null) {
                    playerId = """class="player_select_item"\s+data-id="([^"]+)"""".toRegex().find(text3)?.groupValues?.get(1)
                }
                println("PlayerId: $playerId")
                if (playerId != null) {
                    println("Step 4")
                    val conn4 = URL("https://www.embedplay.one/api").openConnection() as HttpURLConnection
                    conn4.requestMethod = "POST"
                    conn4.setRequestProperty("User-Agent", "Mozilla/5.0")
                    conn4.doOutput = true
                    val writer = OutputStreamWriter(conn4.outputStream)
                    writer.write("action=getPlayer&video_id=$playerId")
                    writer.flush()
                    
                    val text4 = conn4.inputStream.bufferedReader().readText()
                    println("Text4: $text4")
                    result = """"video_url":"([^"]+)"""".toRegex().find(text4)?.groupValues?.get(1)?.replace("\\/", "/")
                }
            }
        }
    } catch(e: Exception) {
        e.printStackTrace()
    }
    println("Result: $result")
}
