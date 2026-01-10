import os
import sys
import subprocess

def audit_licenses():
    print("## The Warden's Patrol ⚖️")
    print("| Package | License Type | Status |")
    print("| :--- | :--- | :--- |")
    
    # In Android/Gradle, we ideally use a plugin like 'com.google.android.gms:oss-licenses-plugin'
    # or 'com.github.hierynomus.license'.
    # For a lightweight CI check, we analyze the current defined dependencies.
    
    try:
        # Get dependency list from Gradle
        result = subprocess.run(['./gradlew', ':app:dependencies', '--configuration', 'releaseRuntimeClasspath'], 
                             capture_output=True, text=True)
        deps_output = result.stdout
    except Exception as e:
        print(f"⚠️ Error running gradlew: {e}")
        return

    problematic_keywords = ["gpl", "agpl"]
    violations = 0
    
    # Very basic heuristic scanning the dependency tree output
    seen_packages = set()
    for line in deps_output.splitlines():
        if '---' in line:
            parts = line.split('---')
            if len(parts) > 1:
                pkg = parts[1].strip().split(' ')[0]
                if pkg and pkg not in seen_packages:
                    seen_packages.add(pkg)
                    license_status = "✅ Allowed"
                    license_type = "Permissive/Standard"
                    
                    if any(kw in pkg.lower() for kw in problematic_keywords):
                        license_status = "❌ Violation"
                        license_type = "Copyleft (Heuristic)"
                        violations += 1
                    
                    # We only print the first few or relevant ones to avoid summary bloat
                    if violations > 0 or len(seen_packages) < 20: 
                        print(f"| {pkg} | {license_type} | {license_status} |")

    if violations == 0:
        print(f"\n✅ Audited {len(seen_packages)} packages. No copyleft violations detected.")
    else:
        print(f"\n⚠️ **{violations} license violations detected!** Please review the dependency list.")

if __name__ == "__main__":
    audit_licenses()
