import re

with open('app/src/main/java/com/example/ui/screens/MediaScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Replace the clickable link block
# We want to match:
# if (episode.streamUrl.isNotBlank()) {
#     Spacer(modifier = Modifier.height(2.dp))
#     Row(
#     ...
#     }
# }

pattern = r'(\s+)if \(episode\.streamUrl\.isNotBlank\(\)\) \{\s+Spacer\(modifier = Modifier\.height\(2\.dp\)\)\s+Row\([\s\S]*?TextOverflow\.Ellipsis\s*\)\s*\}\s*\}'

content = re.sub(pattern, '', content)

with open('app/src/main/java/com/example/ui/screens/MediaScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

