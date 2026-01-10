import os
import datetime

def consolidate_hub_processes():
    root_to_scan = '.' 
    output_dir = 'audit'
    
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)

    # Process Mappings for Music_Hub
    processes = {
        'hub_processo_core.txt': [
            'app/src/main/java/com/joaomagdaleno/music_hub/Main',
            'app/src/main/java/com/joaomagdaleno/music_hub/di',
            'app/src/main/java/com/joaomagdaleno/music_hub/utils',
            'app/src/main/java/com/joaomagdaleno/music_hub/extensions'
        ],
        'hub_processo_player.txt': [
            'app/src/main/java/com/joaomagdaleno/music_hub/playback',
            'app/src/main/java/com/joaomagdaleno/music_hub/ui/player',
            'app/src/main/java/com/joaomagdaleno/music_hub/ui/media'
        ],
        'hub_processo_download.txt': [
            'app/src/main/java/com/joaomagdaleno/music_hub/download',
            'app/src/main/java/com/joaomagdaleno/music_hub/ui/download'
        ],
        'hub_processo_biblioteca.txt': [
            'app/src/main/java/com/joaomagdaleno/music_hub/ui/feed',
            'app/src/main/java/com/joaomagdaleno/music_hub/ui/playlist'
        ],
        'hub_processo_ui_shared.txt': [
            'app/src/main/java/com/joaomagdaleno/music_hub/ui/common',
            'app/src/main/java/com/joaomagdaleno/music_hub/widget',
            'app/src/main/java/com/joaomagdaleno/music_hub/ui/settings',
            'app/src/main/res'
        ],
        'hub_processo_common.txt': [
            'common'
        ],
        'hub_processo_build.txt': [
            'build.gradle.kts',
            'settings.gradle.kts',
            'gradle'
        ],
        'hub_processo_testes.txt': [
            'app/src/test',
            'app/src/androidTest'
        ],
        'hub_processo_outros.txt': []
    }

    extensions = ('.kt', '.kts', '.java', '.xml', '.gradle', '.properties', '.yml', '.yaml', '.md', '.css')
    exclude_dirs_names = {'.git', 'build', '.gradle', '.idea', 'audit'}
    
    file_buckets = {k: [] for k in processes.keys()}

    start_time = datetime.datetime.now()
    total_files = 0
    
    print(f"Scanning files in {os.getcwd()}...")

    for root, dirs, files in os.walk(root_to_scan):
        dirs[:] = [d for d in dirs if d not in exclude_dirs_names]
        
        for file in files:
            if file.endswith(extensions):
                file_path = os.path.join(root, file)
                rel_path = os.path.relpath(file_path, root_to_scan).replace('\\', '/')
                
                if rel_path.startswith('audit/') or 'consolidate_hub' in rel_path or 'full_project_code' in rel_path:
                    continue
                
                assigned_bucket = 'hub_processo_outros.txt'
                
                for bucket, patterns in processes.items():
                    if bucket == 'hub_processo_outros.txt': continue
                    for pattern in patterns:
                        if rel_path.startswith(pattern) or pattern in rel_path:
                            assigned_bucket = bucket
                            break
                    if assigned_bucket != 'hub_processo_outros.txt':
                        break
                
                file_buckets[assigned_bucket].append(file_path)
                total_files += 1

    print(f"Assigning {total_files} files to buckets...")
    
    for bucket_name, files_list in file_buckets.items():
        output_path = os.path.join(output_dir, bucket_name)
        with open(output_path, 'w', encoding='utf-8') as outfile:
            outfile.write("="*80 + "\n")
            outfile.write(f"PROCESS AUDIT: {bucket_name}\n")
            outfile.write(f"DATE: {start_time.strftime('%Y-%m-%d %H:%M:%S')}\n")
            outfile.write(f"FILE COUNT: {len(files_list)}\n")
            outfile.write("="*80 + "\n\n")
            
            for file_path in sorted(files_list):
                 rel_path = os.path.relpath(file_path, root_to_scan)
                 outfile.write(f"{'-'*10} FILE: {rel_path} {'-'*10}\n")
                 try:
                    try:
                        with open(file_path, 'r', encoding='utf-8') as infile:
                             content = infile.read()
                    except UnicodeDecodeError:
                        with open(file_path, 'r', encoding='latin-1') as infile:
                             content = infile.read()
                    outfile.write(content)
                 except Exception as e:
                    outfile.write(f"ERROR READING FILE: {e}\n")
                 outfile.write("\n\n")

    end_time = datetime.datetime.now()
    print(f"Consolidation complete. Duration: {end_time - start_time}")

if __name__ == "__main__":
    consolidate_hub_processes()
