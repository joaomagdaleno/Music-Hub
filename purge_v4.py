import os
import re

root_path = r'c:\Users\joaom\Documents\GitHub\Music_Hub\app\src\main'

def process_file(file_path):
    if not (file_path.endswith('.kt') or file_path.endswith('.xml')):
        return
        
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()

        # Variable renames (whole words only)
        content = re.sub(r'\bextId\b', 'origin', content)
        content = re.sub(r'\bextensionId\b', 'origin', content)
        content = re.sub(r'\bgetExtension\b', 'getSource', content)
        content = re.sub(r'\bcacheExtensionItemFlow\b', 'cacheSourceItemFlow', content)
        content = re.sub(r'\btrackCachedFlow\b', 'trackSourceCachedFlow', content)
        content = re.sub(r'\bfeedCachedFlow\b', 'feedSourceCachedFlow', content)
        content = re.sub(r'\btrackLoadedFlow\b', 'trackSourceLoadedFlow', content)
        content = re.sub(r'\bfeedLoadedFlow\b', 'feedSourceLoadedFlow', content)
        
        # Clean up specific remnants
        content = content.replace('"Native - "', '"Internal - "')
        content = content.replace("'Native - '", "'Internal - '")

        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
    except Exception as e:
        print(f"Error processing {file_path}: {e}")

for root, dirs, files in os.walk(root_path):
    for file in files:
        process_file(os.path.join(root, file))
