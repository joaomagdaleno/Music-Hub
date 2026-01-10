import os
import re

root_path = r'c:\Users\joaom\Documents\GitHub\Music_Hub\app\src\main'

def process_file(file_path):
    if not (file_path.endswith('.kt') or file_path.endswith('.xml')):
        return
        
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()

        # Rename string IDs
        content = content.replace('name="extensions"', 'name="data_sources"')
        content = content.replace('@string/extensions', '@string/data_sources')
        content = content.replace('name="x_sources"', 'name="x_providers"')
        content = content.replace('name="x_source"', 'name="x_provider"')
        content = content.replace('@string/x_sources', '@string/x_providers')
        content = content.replace('@string/x_source', '@string/x_provider')
        content = content.replace('com.joaomagdaleno.music_hub.data.sources', 'com.joaomagdaleno.music_hub.data.providers')
        
        # Specific terminology replacements
        if file_path.endswith('.xml'):
            content = content.replace('extension', 'source')
            content = content.replace('Extension', 'Source')
            content = content.replace('unified', 'internal')
            content = content.replace('Unified', 'Internal')
        else:
            # Kotlin - be a bit more careful but thorough
            content = content.replace('extensionLoader', 'sourceLoader')
            content = content.replace('extensionId', 'origin')
            content = content.replace('UnifiedDatabase', 'MusicDatabase')
            # Replace common occurrences in strings/comments
            content = re.sub(r'\bextension\b', 'source', content)
            content = re.sub(r'\bExtension\b', 'Source', content)
            content = re.sub(r'\bunified\b', 'internal', content)
            content = re.sub(r'\bUnified\b', 'Internal', content)

        with open(file_path, 'w', encoding='utf-8') as f:
            f.write(content)
    except Exception as e:
        print(f"Error processing {file_path}: {e}")

for root, dirs, files in os.walk(root_path):
    for file in files:
        process_file(os.path.join(root, file))
