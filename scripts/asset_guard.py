import os
import sys

def get_unused_assets():
    # Adjusted for Android project structure
    project_root = "app"
    res_dir = os.path.join(project_root, "src", "main", "res")
    java_dir = os.path.join(project_root, "src", "main", "java")
    xml_dir = os.path.join(project_root, "src", "main", "res", "layout")
    
    if not os.path.exists(res_dir):
        print("ℹ️ No resources directory found.")
        return []

    # Get all drawable/mipmap/layout files
    asset_files = []
    for root, dirs, files in os.walk(res_dir):
        if "layout" in root or "drawable" in root or "mipmap" in root:
            for file in files:
                # Store the base name without extension for matching in code/XML
                name = os.path.splitext(file)[0]
                asset_files.append(name)

    if not asset_files:
        print("✅ No assets found to audit.")
        return []

    unused_assets = set(asset_files)
    
    # Scan Java/Kotlin and XML files for references
    search_dirs = [java_dir, xml_dir]
    for search_dir in search_dirs:
        if not os.path.exists(search_dir):
            continue
            
        for root, _, files in os.walk(search_dir):
            for file in files:
                if file.endswith((".java", ".kt", ".xml")):
                    with open(os.path.join(root, file), 'r', encoding='utf-8', errors='ignore') as f:
                        content = f.read()
                        for asset in list(unused_assets):
                            # Search for R.drawable.name, R.layout.name, @drawable/name, etc.
                            if asset in content:
                                unused_assets.discard(asset)

    return sorted(list(unused_assets))

if __name__ == "__main__":
    print("🧪 Hermes: The Alchemist's Brew - Android Asset Audit")
    unused = get_unused_assets()
    
    if unused:
        # We report many but some might be false positives due to string concatenation
        # so we just print them as warnings
        print(f"⚠️ Found {len(unused)} potentially unused assets:")
        for a in unused:
            print(f"  - {a}")
        sys.exit(0) 
    else:
        print("✅ All assets appear to be in use.")
        sys.exit(0)
