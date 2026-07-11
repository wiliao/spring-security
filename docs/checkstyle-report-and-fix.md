<!--
  checkstyle-report-and-fix.md
  Purpose: Guide for generating the Checkstyle (nohttp) report and how to inspect & fix violations
  Location: docs/
-->

# Checkstyle (nohttp) — generate report and fix violations

This document describes how to run the project's Checkstyle tasks (specifically the `nohttp` check), how to inspect the generated reports, and recommended steps to safely fix any `http://` occurrences flagged by the check.

Checklist

- Run the check: `./gradlew.bat check` (Windows PowerShell) or `./gradlew check` (Unix)
- Open the `nohttp` report: `build/reports/checkstyleNohttp/nohttp.html`
- If violations exist: search the repository for `http://` occurrences (exclude build, .git, node_modules)
- Evaluate each occurrence and either convert to `https://`, add an allowlist entry, or leave as-is with justification
- Re-run `./gradlew.bat check` and iterate until clean

1) Generate the Checkstyle (nohttp) report

Run the Gradle `check` task from the repository root. On Windows PowerShell:

```powershell
.\gradlew.bat check --no-daemon
```

This runs the full `check` lifecycle, including the `nohttp` Checkstyle plugin used by this project. The `nohttp` report is generated under:

```
build/reports/checkstyleNohttp/nohttp.html
```

Open that file in a browser to see the summary and detailed violations.

2) Understand the allowlist

The project maintains an allowlist of permitted `http://` patterns in:

```
etc/nohttp/allowlist.lines
```

Before editing files that contain `http://`, check whether the occurrence is covered by an allowlist entry. If it is, you can either:

- Leave the occurrence as-is (if intentionally allowed), or
- Update `etc/nohttp/allowlist.lines` to remove or refine the pattern if the URL should no longer be allowed.

3) Find `http://` occurrences in the repository

Use PowerShell to find occurrences while skipping generated directories. From the project root (PowerShell):

```powershell
# Recursively search files, excluding build, node_modules and .git directories
Get-ChildItem -Recurse -File |
  Where-Object { $_.FullName -notmatch '\\(build|node_modules|\.git)\\' } |
  Select-String -Pattern 'http://' |
  Sort-Object Path, LineNumber |
  Format-Table Path, LineNumber, Line -AutoSize
```

Notes:

- The search above tries to avoid generated files. Adjust exclusions if you have other generated directories.
- For large repos you can limit the search to specific file types, e.g. `Get-ChildItem -Include *.md,*.adoc,*.xml -Recurse`.

4) Decide how to fix each occurrence

For every matching occurrence consider:

- Can the URL use `https://` safely? If yes, prefer switching to `https://`.
- Is the URL purely an example (e.g. `http://example.com`) used in documentation? Consider using `https://example.com` or an RFC example domain (`https://example.org`) if appropriate.
- Is the URL an internal/local endpoint (e.g. `http://host.docker.internal:8090/...` or `http://169.254.169.254/...`)? If so, consider adding a specific allowlist entry in `etc/nohttp/allowlist.lines` rather than changing the URL.

Automated replacement is risky — prefer manual review. If you choose to do a bulk replace, make a branch and commit so you can review changes.

Example quick replacement (manual review recommended):

```powershell
# Make a backup branch
git checkout -b fix/nohttp-replace

# Replace http:// with https:// in a single file (example); inspect before committing
(Get-Content path\\to\\file.md -Raw) -replace 'http://', 'https://' | Set-Content path\\to\\file.md

# Run the Check again
.\gradlew.bat check --no-daemon
```

5) Adding or editing allowlist entries

If a given `http://` must remain (e.g. known internal metadata endpoints), add or refine an entry in `etc/nohttp/allowlist.lines`. These are regex patterns — add the minimal pattern that permits the URL, with a comment in the file explaining why it is allowed.

Example (append to `etc/nohttp/allowlist.lines`):

```
# Allow the local metadata endpoint used in CI (justification: required for test infra)
^http://169.254.169.254/keys
```

6) Validate changes and iterate

- Re-run `.\gradlew.bat check` until `build/reports/checkstyleNohttp/nohttp.html` reports zero violations.
- Use `git diff` and code review to ensure no unintended changes were made by bulk-replace.

7) Helpful tips

- Use the report `build/reports/checkstyleNohttp/nohttp.html` as the authoritative source of Checkstyle `nohttp` results.
- If you update `etc/nohttp/allowlist.lines`, be careful: the file contains regex patterns. A too-broad pattern may accidentally permit problematic URLs.
- If you prefer to programmatically find replacements in a safe way, write a short script that shows candidate changes and requires confirmation for each replacement.

8) Example PR checklist

- [ ] Branch created: `git checkout -b fix/nohttp-YYYYMMDD`
- [ ] All `http://` occurrences either converted to `https://` or documented in `etc/nohttp/allowlist.lines`
- [ ] `./gradlew.bat check` passes locally
- [ ] Changes are pushed and PR opened for review

If you want, I can:

- Run a repo-wide search for `http://` and prepare a draft branch with safe replacements (I will show each candidate before making changes), or
- Prepare a small PowerShell script that lists candidate `http://` occurrences grouped by file and suggests replacements, or
- Create this file in the `docs/` folder (done) and open it for review.

---

Document created: `docs/checkstyle-report-and-fix.md`

