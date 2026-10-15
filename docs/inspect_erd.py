import xml.etree.ElementTree as ET
import html
import re

import sys
sys.stdout.reconfigure(encoding='utf-8')

with open('docs/erd.drawio.svg', 'r', encoding='utf-8') as f:
    text = f.read()

m = re.search(r'content="([^"]+)"', text)
if m:
    xml_content = html.unescape(m.group(1))
    print('Found mxfile content, length:', len(xml_content))
    root = ET.fromstring(xml_content)
    for i, diagram in enumerate(root.findall('diagram')):
        name = diagram.get('name') or f'page_{i}'
        print('========================================')
        print(f'Diagram ID: {diagram.get("id")} Name: {name}')
        found_mermaid = False
        for elem in diagram.iter():
            if 'mermaidData' in elem.attrib:
                found_mermaid = True
                data = html.unescape(elem.attrib['mermaidData'])
                out_path = f'docs/erd_{name}.json'
                with open(out_path, 'w', encoding='utf-8') as out:
                    out.write(data)
                print(f'Saved mermaid data to {out_path}')
        if not found_mermaid:
            print('No mermaid data found directly in diagram elements. Checking text...')
            cells = []
            for elem in diagram.iter():
                val = elem.get('value')
                if val:
                    cells.append(val)
                label = elem.get('label')
                if label:
                    cells.append(label)
            print(f'Found {len(cells)} text values in {name}:')
            print('\n'.join(cells[:30]))


