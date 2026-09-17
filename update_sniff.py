with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace("var isSniffingMedia by remember { mutableStateOf(false) }", "")
content = content.replace(
    "var isWebViewError by remember { mutableStateOf(false) }",
    """var isWebViewError by remember { mutableStateOf(false) }
    var isSniffingMedia by remember { mutableStateOf(false) }"""
)

with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)
