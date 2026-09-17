with open('app/src/main/java/com/example/data/AbysResolver.kt', 'r', encoding='utf-8') as f:
    content = f.read()

new_content = content.replace(
    """val videoUrl = Regex("\"\"\"\"video_url\"\":\"([^\"]+)\"\"\"\").find(text4)?.groupValues?.get(1)?.replace("\\/", "/")
            return@withContext videoUrl""",
    """val videoUrl = Regex("\"\"\"\"video_url\"\":\"([^\"]+)\"\"\"\").find(text4)?.groupValues?.get(1)?.replace("\\/", "/")
            if (videoUrl != null && videoUrl.contains("embedplayabyss.top/player.html?v=")) {
                val slug = videoUrl.substringAfter("?v=").substringBefore("&")
                return@withContext "https://abysscdn.com/?v=$slug"
            }
            return@withContext videoUrl"""
)

# Actually the regex in kotlin is Regex(""""video_url":"([^"]+)"""")
