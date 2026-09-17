import sys

def check_braces(filename):
    with open(filename, 'r') as f:
        lines = f.readlines()
        
    stack = []
    
    for i, line in enumerate(lines):
        for j, char in enumerate(line):
            if char == '{':
                stack.append((i+1, j))
            elif char == '}':
                if stack:
                    stack.pop()
                else:
                    print(f"Extra closing brace at line {i+1}")
                    
    print(f"Unclosed braces: {len(stack)}")
    for line, col in stack:
        print(f"  Line {line}: {lines[line-1].strip()}")

check_braces('app/src/main/java/com/example/ui/screens/MediaScreen.kt')
