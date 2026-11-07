#!/usr/bin/env python3
"""Checks that the balance table in DESIGN.md §12 matches tools/simulate.py.

Runs the simulator with its default seed and run count and compares its
Markdown table with the one in DESIGN.md. Exits with status 1 and prints a
diff if they differ, so a change to the meter maths or to the table can't be
merged without updating the other.

Usage: python3 tools/check_balance.py
"""
import difflib
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
HEADER = "| Asleep | Unrest 0 | Unrest 2 | Unrest 5 |"


def table(lines):
    lines = [line.rstrip() for line in lines]
    start = lines.index(HEADER)
    end = start
    while end < len(lines) and lines[end].startswith("|"):
        end += 1
    return lines[start:end]


def main():
    sim = subprocess.run(
        [sys.executable, str(ROOT / "tools" / "simulate.py")],
        check=True, capture_output=True, text=True,
    ).stdout.splitlines()
    doc = (ROOT / "DESIGN.md").read_text(encoding="utf-8").splitlines()
    expected, actual = table(doc), table(sim)
    if expected == actual:
        print("DESIGN.md balance table matches tools/simulate.py.")
        return 0
    print("DESIGN.md §12 is out of date with tools/simulate.py:\n")
    sys.stdout.writelines(
        line + "\n"
        for line in difflib.unified_diff(expected, actual, "DESIGN.md", "simulate.py", lineterm="")
    )
    return 1


if __name__ == "__main__":
    sys.exit(main())
