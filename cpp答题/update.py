import re

with open("quiz.cpp", "r", encoding="utf-8") as f:
    lines = f.readlines()

result = []
for i, line in enumerate(lines):
    result.append(line)
    
    # Change 1: add forward declaration after TracksTotalTime
    if "bool TracksTotalTime();" in line and line.strip().startswith("bool"):
        result.append("void ReadEditIntoState();\n")
        continue
    
    # Change 2: Remove unconditional ReadEditIntoState at start of UpdateEditForState
    stripped = line.strip()
    if i > 600 and stripped == "ReadEditIntoState();" and i < len(lines)-1 and "shouldShow" in lines[i+1]:
        result.pop()  # remove this line (the misplaced ReadEditIntoState)
    
    # Change 3: Insert ReadEditIntoState inside shouldShow block
    if "if (shouldShow) {" in line and i > 690:
        # Already on the right line; next non-empty line is UIRect r
        continue
    
    # We'll handle insertion below by line position
    
    # Change 4: In else branch, protect userFill.clear
    if stripped == "g_state.userFill.clear();" and i > 700:
        # Check if inside else of UpdateEditForState
        # Replace with conditional
        indent = "        "
        # This line's actual text
        if line.strip() == "g_state.userFill.clear();":
            result.pop()
            result.append(indent + "// do not clear userFill while showing answer feedback\n")
            result.append(indent + "if (g_state.page != PAGE_QUIZ || !g_state.answered) {\n")
            result.append(indent + "    g_state.userFill.clear();\n")
            result.append(indent + "}\n")
            continue

with open("quiz.cpp", "w", encoding="utf-8") as f:
    f.writelines(result)
print("done")
