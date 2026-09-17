import re

with open('app/src/main/java/com/example/ui/screens/MediaScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Fix MediaCard edit button
content = re.sub(
    r'(\s+)IconButton\(\s*onClick = \{ onEditClick\(\) \},\s*modifier = Modifier\.size\(32\.dp\)\s*\)\s*\{\s*Icon\(\s*imageVector = Icons\.Filled\.Edit,[\s\S]*?modifier = Modifier\.size\(16\.dp\)\s*\)\s*\}',
    r'\1if (isAdmin) {\1    IconButton(\n\1        onClick = { onEditClick() },\n\1        modifier = Modifier.size(32.dp)\n\1    ) {\n\1        Icon(\n\1            imageVector = Icons.Filled.Edit,\n\1            contentDescription = "Editar informações",\n\1            tint = NeonCyan,\n\1            modifier = Modifier.size(16.dp)\n\1        )\n\1    }\n\1}',
    content
)

# 2. Fix MovieDetailDialog edit button
content = re.sub(
    r'(\s+)IconButton\(\s*onClick = onEdit,\s*modifier = Modifier\s*\.size\(38\.dp\)[\s\S]*?Icons\.Filled\.Edit,[\s\S]*?modifier = Modifier\.size\(18\.dp\)\s*\)\s*\}',
    r'\1if (isAdmin) {\1    IconButton(\n\1        onClick = onEdit,\n\1        modifier = Modifier\n\1            .size(38.dp)\n\1            .clip(CircleShape)\n\1            .background(Color.Black.copy(alpha = 0.75f))\n\1            .border(BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)), CircleShape)\n\1    ) {\n\1        Icon(\n\1            imageVector = Icons.Filled.Edit,\n\1            contentDescription = "Editar informações",\n\1            tint = NeonCyan,\n\1            modifier = Modifier.size(18.dp)\n\1        )\n\1    }\n\1}',
    content
)

# 3. Fix SeriesDetailDialog edit button (note size 34 and NeonPurple)
# But wait, looking at the previous output, SeriesDetailDialog actually succeeded!
# Let me double check SeriesDetailDialog in the file just in case.

# 4. Remove copy link for episodes
content = re.sub(
    r'(\s+)// Copy Link Action Button[\s\S]*?Spacer\(modifier = Modifier\.width\(4\.dp\)\)\s*\}',
    '',
    content
)

with open('app/src/main/java/com/example/ui/screens/MediaScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

