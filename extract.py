import json

transcript_path = r"C:\Users\Lenovo\.gemini\antigravity-ide\brain\80c6ebdb-f4ab-466b-af18-f959cf25ce92\.system_generated\logs\transcript_full.jsonl"
with open(transcript_path, 'r', encoding='utf-8') as f:
    for line in f:
        data = json.loads(line)
        if data.get('type') == 'USER_INPUT':
            content = data.get('content', '')
            if 'PICCO PROMPT' in content and 'Phase 2' in content:
                with open('prompt.md', 'w', encoding='utf-8') as out:
                    out.write(content)
                print("Prompt extracted to prompt.md")
                break
