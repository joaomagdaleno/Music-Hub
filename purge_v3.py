import os
import re

root_path = r'c:\Users\joaom\Documents\GitHub\Music_Hub\app\src\main'

def process_file(file_path):
    if not (file_path.endswith('.kt') or file_path.endswith('.xml')):
        return
        
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()

        # Terminology standardisation
        content = content.replace('"native"', '"internal"')
        content = content.replace("'native'", "'internal'")
        content = content.replace('"extId"', '"origin"')
        content = content.replace("'extId'", "'origin'")
        
        # String IDs
        content = content.replace('name="extensions"', 'name="data_sources"')
        content = content.replace('@string/extensions', '@string/data_sources')
        
        if file_path.endswith('.xml'):
            content = content.replace('extensions', 'sources')
            content = content.replace('Extensions', 'Sources')
            content = content.replace('extension', 'source')
            content = content.replace('Extension', 'Source')
            content = content.replace('unified', 'internal')
            content = content.replace('Unified', 'Internal')
            content = content.replace('native', 'internal')
            content = content.replace('Native', 'Internal')
        else:
            content = content.replace('extensionLoader', 'sourceLoader')
            
            def comment_repl(match):
                c = match.group(0)
                c = c.replace('native', 'internal')
                c = c.replace('Native', 'Internal')
                c = c.replace('extension', 'source')
                c = c.replace('Extension', 'Source')
                c = c.replace('unified', 'internal')
                c = c.replace('Unified', 'Internal')
                return c
            
            content = re.sub(r'//.*', comment_repl, content)
            content = re.sub(r'/\*.*?\*/', comment_repl, content, flags=re.DOTALL)
            # Standalone word replacements in Kotlin (handle with care)
            # Use origin instead of extensionId (already done mostly but just in case)
            content = content.replace('extensionId', 'origin')

        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
    except Exception as e:
        print(f"Error processing {file_path}: {e}")

for root, dirs, files in os.walk(root_path):
    for file in files:
        process_file(os.path.join(root, file))
