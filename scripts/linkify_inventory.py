import re
import os

inventory_path = r"C:\Users\tom7s\Desktopp\Claude_Folder_2\Date_Program\WORKSPACE_INVENTORY.md"
root_dir = r"C:\Users\tom7s\Desktopp\Claude_Folder_2\Date_Program"

with open(inventory_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
# Stack to keep track of current directory path based on indentation
# Each element is (indent_level, directory_name)
dir_stack = []

for line in lines:
    original_line = line
    # Calculate indentation level (space count)
    indent = len(line) - len(line.lstrip())
    stripped = line.strip()

    # Check if this is a directory line (ends with /, typically bolded like **core/**)
    # Regex to capture directory name from lines like: "- **core/** — ..." or "    - **java/**"
    dir_match = re.match(r'^-\s+\*\*([^\*]+)/?\*\*', stripped)

    if dir_match:
        dirname = dir_match.group(1).rstrip('/') # Remove trailing slash if caught

        # Pop from stack if current indent is <= items in stack
        # We need to find the parent.
        # Logic: If current indent is X, parent must have indent < X.
        while dir_stack and dir_stack[-1][0] >= indent:
            dir_stack.pop()

        dir_stack.append((indent, dirname))
        new_lines.append(original_line)
        continue

    # Check if this is a file line
    # Regex to capture filename from lines like: "- User.java — ..." or "    - User.java"
    # It usually starts with "- " and ends with " — " or just the filename
    file_match = re.match(r'^-\s+([^\s]+)\s+—', stripped)
    if not file_match:
        # Try matching lines without description separator " — " if any
        # But our format strictly uses " — " based on previous edits, except maybe simple lists
        # Let's interact with lines that look like files.
        pass

    if file_match:
        filename = file_match.group(1)

        # Construct path from stack
        # Filter stack to getting only the names
        # Note: The root of the inventory seems to start implied at root or under src/ etc.
        # We need to be careful. The inventory structure mimics the file system.

        # Logic: Reconstruct relative path
        # But wait, the indentation implies hierarchy.

        # We need to adjust stack before processing file if indent changed
        while dir_stack and dir_stack[-1][0] >= indent:
            dir_stack.pop()

        current_path_parts = [item[1] for item in dir_stack]
        relative_path = os.path.join(*current_path_parts, filename)

        # Verify file exists
        full_path = os.path.join(root_dir, relative_path)

        if os.path.exists(full_path):
            # Create link: [filename](relative_path)
            # Replace: "- filename —" with "- [filename](relative_path) —"
            # Note: relative_path needs forward slashes for markdown links usually, though windows handles both.
            # Best practice: forward slashes.
            rel_path_fwd = relative_path.replace('\\', '/')

            # Reconstruction
            # We want to preserve exact whitespace and description
            prefix = line[:line.find(filename)]
            suffix = line[line.find(filename) + len(filename):]

            new_line = f"{prefix}[{filename}]({rel_path_fwd}){suffix}"
            new_lines.append(new_line)
        else:
            # File not found at calculated path - append as is but maybe warn?
            # For now, just keep as is to be safe
            # Debug print
            print(f"File not found: {full_path}")
            new_lines.append(original_line)
    else:
        new_lines.append(original_line)

# Write back
with open(inventory_path, 'w', encoding='utf-8') as f:
    f.writelines(new_lines)

print("Inventory processing complete.")
