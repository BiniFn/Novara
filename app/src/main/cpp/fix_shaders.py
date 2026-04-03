import os
import glob

for comp in glob.glob("*.comp"):
    with open(comp, "r") as f:
        text = f.read()

    # Clean up completely
    lines = text.split('\n')
    # remove leading blank lines
    while lines and not lines[0].strip():
        lines.pop(0)
    
    # ensure first line is exactly #version 450
    if not lines[0].startswith('#version'):
        lines.insert(0, '#version 450')
    else:
        lines[0] = '#version 450'

    text = '\n'.join(lines)
    # Fix the constant type
    text = text.replace('1 / 255.f', '1.f / 255.f')
    
    with open(comp, "w") as f:
        f.write(text)
        
    data = text.encode('utf-8') + b'\x00'
    name = comp.replace('.', '_') + '_data'
    
    with open(comp + ".hex.h", "w") as f:
        f.write(f"static const char {name}[] = {{\n")
        
        # Format cleanly to avoid huge lines
        hex_array = [hex(b) for b in data]
        chunks = [hex_array[i:i+16] for i in range(0, len(hex_array), 16)]
        for chunk in chunks:
            f.write("  " + ", ".join(chunk) + ",\n")
            
        f.write("};\n")

print("Done fixing and dumping!")
