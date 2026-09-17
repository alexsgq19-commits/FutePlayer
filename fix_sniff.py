with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

out = []
for l in lines:
    if 'var isSniffingMedia by remember' in l:
        continue
    if 'val isMovieOrSeries' in l:
        out.append('    var isSniffingMedia by remember { mutableStateOf(false) }\n')
    out.append(l)

with open('app/src/main/java/com/example/ui/screens/PlayerScreen.kt', 'w', encoding='utf-8') as f:
    f.writelines(out)
