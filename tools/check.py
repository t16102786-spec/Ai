#!/usr/bin/env python3 -I
"""Проверка проекта: длина строк, отступы, скобки, ресурсы."""
import os, sys, re, xml.etree.ElementTree as ET

PROJ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
errors = []

def err(path, line, msg):
    rel = os.path.relpath(path, PROJ)
    errors.append(f"{rel}:{line}: {msg}")

def check_text(path):
    ext = os.path.splitext(path)[1]
    indent_unit = 2 if ext in ('.yml', '.yaml') else 4
    with open(path, encoding='utf-8') as f:
        lines = f.readlines()
    if len(lines) > 200:
        err(path, 200, f"файл длиннее 200 строк ({len(lines)})")
    brace_stack = []  # list of (open_indent, line_no)
    for i, raw in enumerate(lines, 1):
        line = raw.rstrip('\n')
        if '\t' in line:
            err(path, i, "табуляция")
        if line != line.rstrip():
            err(path, i, "пробел в конце строки")
        stripped = line.lstrip()
        indent = len(line) - len(stripped)
        if stripped and indent % indent_unit != 0:
            err(path, i, f"отступ {indent} не кратен {indent_unit}")
        if ext in ('.java', '.gradle'):
            check_braces(path, i, line, stripped, indent, brace_stack)
    if brace_stack:
        for open_ind, open_ln in brace_stack:
            err(path, open_ln, "незакрытая '{'")

def strip_java_line(line):
    """Убрать строковые литералы и //комментарии."""
    result = []
    in_str = False
    i = 0
    while i < len(line):
        c = line[i]
        if in_str:
            if c == '\\': i += 2; continue
            if c == '"': in_str = False
        else:
            if c == '"': in_str = True
            elif c == '/' and i+1 < len(line) and line[i+1] == '/':
                break
            else:
                result.append(c)
        i += 1
    return ''.join(result)

def check_braces(path, lineno, raw, stripped, indent, stack):
    clean = strip_java_line(raw)
    for c in clean:
        if c == '{':
            stack.append((indent, lineno))
        elif c == '}':
            if not stack:
                err(path, lineno, "лишняя '}'")
                return
            stack.pop()

def check_xml(path):
    try:
        ET.parse(path)
    except ET.ParseError as e:
        err(path, 0, f"XML ошибка: {e}")

# --- Собрать ресурсы ---
def collect_res():
    res = {k: set() for k in ('color','string','id','layout','drawable','style','mipmap')}
    res_dir = os.path.join(PROJ, 'app', 'src', 'main', 'res')
    if not os.path.isdir(res_dir):
        return res
    for root, dirs, files in os.walk(res_dir):
        for fn in files:
            fp = os.path.join(root, fn)
            name = os.path.splitext(fn)[0]
            folder = os.path.basename(root).split('-')[0]
            if folder == 'layout':
                res['layout'].add(name)
            elif folder == 'drawable':
                res['drawable'].add(name)
            elif folder.startswith('mipmap'):
                res['mipmap'].add(name)
            if fn.endswith('.xml') and folder == 'values':
                try:
                    tree = ET.parse(fp)
                    for el in tree.getroot():
                        rname = el.get('name','')
                        if el.tag == 'color': res['color'].add(rname)
                        elif el.tag == 'string': res['string'].add(rname)
                        elif el.tag == 'style': res['style'].add(rname)
                        elif el.tag in ('item','dimen','bool','integer'):
                            # check for id type
                            if el.get('type') == 'id': res['id'].add(rname)
                except ET.ParseError:
                    pass
    return res

def collect_ids_from_layouts(res):
    layout_dir = os.path.join(PROJ, 'app', 'src', 'main', 'res', 'layout')
    if not os.path.isdir(layout_dir): return
    for fn in os.listdir(layout_dir):
        if not fn.endswith('.xml'): continue
        fp = os.path.join(layout_dir, fn)
        with open(fp, encoding='utf-8') as f:
            content = f.read()
        for m in re.finditer(r'android:id="@\+id/(\w+)"', content):
            res['id'].add(m.group(1))

def check_java_refs(path, res):
    with open(path, encoding='utf-8') as f:
        content = f.read()
    for m in re.finditer(r'(?<![.\w])R\.(color|string|id|layout|drawable|style)\s*\.\s*(\w+)', content):
        rtype, rname = m.group(1), m.group(2)
        if rname not in res.get(rtype, set()):
            lineno = content[:m.start()].count('\n') + 1
            err(path, lineno, f"R.{rtype}.{rname} не найден в ресурсах")

def check_xml_refs(path, res):
    with open(path, encoding='utf-8') as f:
        content = f.read()
    for m in re.finditer(r'@(color|string|style|drawable|layout|mipmap)/(\w+)', content):
        rtype, rname = m.group(1), m.group(2)
        if rname not in res.get(rtype, set()):
            lineno = content[:m.start()].count('\n') + 1
            err(path, lineno, f"@{rtype}/{rname} не найден")
    for m in re.finditer(r'@id/(\w+)', content):
        rname = m.group(1)
        if rname not in res.get('id', set()):
            lineno = content[:m.start()].count('\n') + 1
            err(path, lineno, f"@id/{rname} не найден")

# --- Обход файлов ---
SKIP = {'.git', 'build', '.gradle', 'gradle-wrapper.jar'}

res = collect_res()
collect_ids_from_layouts(res)

for root, dirs, files in os.walk(PROJ):
    dirs[:] = [d for d in dirs if d not in SKIP]
    for fn in files:
        fp = os.path.join(root, fn)
        ext = os.path.splitext(fn)[1]
        if ext in ('.java', '.gradle', '.py', '.yml', '.yaml'):
            check_text(fp)
        if ext == '.xml':
            check_xml(fp)
        if ext == '.java':
            check_java_refs(fp, res)
        if ext == '.xml' and 'res' in fp:
            check_xml_refs(fp, res)

if errors:
    print(f"\n❌ Найдено ошибок: {len(errors)}")
    for e in errors:
        print(" ", e)
    sys.exit(1)
else:
    print("✅ Проверка прошла успешно")
