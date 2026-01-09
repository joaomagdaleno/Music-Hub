import os
import json
import glob
from datetime import datetime

def generate_dashboard():
    metrics_dir = "metrics_history"
    if not os.path.exists(metrics_dir):
        return "ℹ️ No metrics history found yet."

    files = sorted(glob.glob(os.path.join(metrics_dir, "android_*.json")), key=os.path.getmtime, reverse=True)[:10]
    files.reverse() # Oldest to newest for the chart
    
    if not files:
        return "ℹ️ No telemetry data points found."

    dates = []
    sizes = []
    
    for f in files:
        with open(f, 'r') as j:
            data = json.load(j)
            timestamp = datetime.fromisoformat(data['timestamp'].replace('Z', '+00:00'))
            dates.append(timestamp.strftime('%m-%d'))
            sizes.append(round(data['apk_size_bytes'] / (1024 * 1024), 2))

    mermaid = "### 📊 Binary Size Evolution (MB)\n"
    mermaid += "```mermaid\n"
    mermaid += "xychart-beta\n"
    mermaid += f'    title "APK Size Strategy (Recent {len(sizes)} builds)"\n'
    mermaid += f'    x-axis [{", ".join([f"\\"{d}\\"" for d in dates])}]\n'
    mermaid += f'    y-axis "Size (MB)" 0 -> {max(sizes) + 5 if sizes else 100}\n'
    mermaid += f'    line [{", ".join([str(s) for s in sizes])}]\n'
    mermaid += "```\n"
    
    return mermaid

if __name__ == "__main__":
    report = generate_dashboard()
    print(report)
    
    # Also update a dedicated CI status file
    with open("CI_DASHBOARD.md", "w", encoding="utf-8") as f:
        f.write("# 🏛️ Hermes: Music Hub Chronology\n\n")
        f.write(report)
        f.write(f"\n\nLast updated: {datetime.utcnow().strftime('%Y-%m-%d %H:%M:%S')} UTC")
