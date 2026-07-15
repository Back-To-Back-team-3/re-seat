# Shared AI Todo Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Claude and Codex the same explicit, duplicate-aware Notion Todo workflow, and configure Claude to produce read-only daily and weekly reviews.

**Architecture:** Store one canonical personal skill under `~/.agents/skills/notion-todo-manager` so its behavior and schema remain consistent. Expose that skill to Claude from `~/.claude/skills`, use the connected Notion `Todo` data source as the only task store, and configure two Claude schedules whose prompts permit reads and reports but prohibit writes.

**Tech Stack:** Markdown agent skills, Notion connector, Claude scheduled tasks, Codex skill validator

## Global Constraints

- Notion database: `Todo`, data source `collection://39ec4b3d-7d9e-8028-9e6f-000b5493e4fd`.
- Add only one database property: `관련 링크` with URL type.
- Explicit requests execute immediately; inferred follow-up tasks require confirmation before creation.
- Search incomplete tasks before every creation and prefer updating a genuine duplicate.
- Claude scheduled reviews are strictly read-only and require no user response.
- Never delete, merge, split, archive, complete, or otherwise change Todo during a scheduled review.
- Do not invent assignees, deadlines, priorities, effort, or completion evidence.

---

### Task 1: Add and verify the Notion property

**Files:**
- Modify externally: Notion data source `collection://39ec4b3d-7d9e-8028-9e6f-000b5493e4fd`

**Interfaces:**
- Consumes: Existing `Todo` schema and the connected Notion workspace.
- Produces: A writable `관련 링크` URL property used by both assistants.

- [ ] **Step 1: Fetch the current data source schema**

Use the Notion fetch tool with:

```json
{"id":"collection://39ec4b3d-7d9e-8028-9e6f-000b5493e4fd"}
```

Expected: the schema contains the existing properties and does not yet contain `관련 링크`, or already contains `관련 링크` with type `url`.

- [ ] **Step 2: Add the property only when absent**

Use the Notion data-source update tool with the equivalent schema operation:

```sql
ADD COLUMN "관련 링크" URL
```

Do not recreate, rename, or change any existing property.

- [ ] **Step 3: Fetch the schema again**

Expected: exactly one property named `관련 링크` exists and its type is `url`.

- [ ] **Step 4: Verify existing views remain intact**

Fetch database `39ec4b3d7d9e802ba622f5b28d4091d6`.

Expected: `모든 작업`, `상태별`, and `체크리스트` still reference the same data source. No view filter, grouping, or display configuration changed.

### Task 2: Create the canonical personal skill

**Files:**
- Create: `/Users/siho/.agents/skills/notion-todo-manager/SKILL.md`
- Create: `/Users/siho/.agents/skills/notion-todo-manager/references/todo-schema.md`
- Create: `/Users/siho/.agents/skills/notion-todo-manager/references/scheduled-reviews.md`
- Create: `/Users/siho/.agents/skills/notion-todo-manager/agents/openai.yaml`

**Interfaces:**
- Consumes: Notion fetch, search/query, create-pages, and update-page capabilities.
- Produces: A `notion-todo-manager` skill with conversational and scheduled read-only workflows.

- [ ] **Step 1: Initialize the skill**

Run:

```bash
python3 /Users/siho/.codex/skills/.system/skill-creator/scripts/init_skill.py notion-todo-manager \
  --path /Users/siho/.agents/skills \
  --resources references \
  --interface 'display_name=Notion Todo Manager' \
  --interface 'short_description=Create and update the shared Notion Todo safely' \
  --interface 'default_prompt=Manage my Notion Todo using explicit requests, duplicate checks, and read-only scheduled reviews.'
```

Expected: the four listed paths exist and the generated skill has valid YAML frontmatter.

- [ ] **Step 2: Write the schema reference**

Set `references/todo-schema.md` to the exact database ID, data source ID, property names, property types, and allowed option values from the design. State that `업데이트 시간` is read-only and that `관련 링크` accepts one URL.

- [ ] **Step 3: Write the scheduled-review reference**

Define two prompts:

```markdown
## Daily review

Read the Todo data source. Report tasks due today, overdue tasks, stale in-progress tasks, missing useful metadata, and up to three priorities. Do not create or update any Notion page or data source. End after reporting.

## Weekly review

Read the Todo data source. Report likely duplicates, long-stale incomplete tasks, unclear completion criteria, oversized tasks, and possible completion or archive candidates. Do not create or update any Notion page or data source. End after reporting.
```

- [ ] **Step 4: Replace the generated SKILL.md**

Write concise imperative instructions that implement these branches:

```text
explicit create request -> fetch schema -> query incomplete tasks -> create or update genuine duplicate
inferred follow-up -> propose title -> wait for confirmation -> run explicit create branch
explicit update request -> fetch target -> apply only requested fields -> fetch to verify
scheduled review -> read/query only -> report -> stop
connector failure -> report no change -> retain proposed action for retry
uncertain write result -> fetch before retrying
```

Require the skill to read `references/todo-schema.md` for writes and `references/scheduled-reviews.md` for scheduled reviews. Explicitly prohibit writes during scheduled reviews.

- [ ] **Step 5: Validate the skill structure**

Run:

```bash
python3 /Users/siho/.codex/skills/.system/skill-creator/scripts/quick_validate.py /Users/siho/.agents/skills/notion-todo-manager
```

Expected: validation succeeds with no placeholder or frontmatter errors.

### Task 3: Forward-test the shared behavior

**Files:**
- Test: `/Users/siho/.agents/skills/notion-todo-manager/SKILL.md`
- Test: `/Users/siho/.agents/skills/notion-todo-manager/references/todo-schema.md`
- Test: `/Users/siho/.agents/skills/notion-todo-manager/references/scheduled-reviews.md`

**Interfaces:**
- Consumes: The initialized skill from Task 2 and a connected Notion workspace.
- Produces: Evidence that write confirmation, duplicate prevention, update verification, and scheduled read-only behavior work.

- [ ] **Step 1: Test inferred follow-up behavior without writing**

Prompt:

```text
배포가 끝나면 모바일 결제 화면도 확인해야겠네.
```

Expected: the assistant proposes one concise Todo title and asks for confirmation. It does not call a Notion write tool.

- [ ] **Step 2: Test explicit creation and duplicate checking**

Prompt:

```text
모바일 결제 화면 검증을 Todo에 추가해줘. 관련 링크는 https://example.com/mobile-payment 이야.
```

Expected: the assistant queries incomplete tasks before creating. If no genuine duplicate exists, it creates one task with `상태=시작 전` and the supplied URL; otherwise it identifies the existing task and updates only after the request supports that update.

- [ ] **Step 3: Test an explicit update**

Prompt:

```text
모바일 결제 화면 검증을 진행 중으로 바꿔줘.
```

Expected: the assistant resolves one target, updates only `상태`, and fetches the result to verify `진행 중`.

- [ ] **Step 4: Test scheduled read-only enforcement**

Run both prompts from `references/scheduled-reviews.md` while recording connector calls.

Expected: only fetch/query/search calls occur. No create, update, delete, schema, or view mutation call occurs.

- [ ] **Step 5: Remove test data explicitly**

If Task 3 created a test task, ask the user for an explicit cleanup instruction. Do not delete it as an implicit test teardown.

### Task 4: Expose the skill to Claude

**Files:**
- Create: `/Users/siho/.claude/skills/notion-todo-manager` as a symbolic link to `/Users/siho/.agents/skills/notion-todo-manager`

**Interfaces:**
- Consumes: The validated canonical skill from Task 3.
- Produces: The same skill contents discoverable by Claude without maintaining a second copy.

- [ ] **Step 1: Check for an existing Claude skill path**

Run:

```bash
test ! -e /Users/siho/.claude/skills/notion-todo-manager
```

Expected: exit code 0. If a path already exists, stop and compare it; do not overwrite it.

- [ ] **Step 2: Create the parent and symbolic link**

Run:

```bash
mkdir -p /Users/siho/.claude/skills
ln -s /Users/siho/.agents/skills/notion-todo-manager /Users/siho/.claude/skills/notion-todo-manager
```

- [ ] **Step 3: Verify both products read the same file**

Run:

```bash
test /Users/siho/.claude/skills/notion-todo-manager/SKILL.md -ef /Users/siho/.agents/skills/notion-todo-manager/SKILL.md
```

Expected: exit code 0.

- [ ] **Step 4: Trigger the skill in a fresh Claude conversation**

Prompt:

```text
Todo에 작업을 추가하고 업데이트할 때 어떤 규칙을 따라야 해?
```

Expected: Claude describes explicit requests, inferred-task confirmation, duplicate search, and the scheduled read-only rule.

### Task 5: Configure Claude read-only schedules

**Files:**
- Read: `/Users/siho/.agents/skills/notion-todo-manager/references/scheduled-reviews.md`
- Modify externally: Claude scheduled-task configuration

**Interfaces:**
- Consumes: The exact daily and weekly prompts from Task 2.
- Produces: Two enabled Claude schedules whose runs only inspect and report Todo.

- [ ] **Step 1: Create the daily schedule**

Create an enabled Claude scheduled task named `Todo 일일 점검` for every day at `09:00 Asia/Seoul`. Use the complete `Daily review` prompt from `scheduled-reviews.md` and explicitly invoke the `notion-todo-manager` skill.

- [ ] **Step 2: Create the weekly schedule**

Create an enabled Claude scheduled task named `Todo 주간 정리` for every Monday at `09:15 Asia/Seoul`. Use the complete `Weekly review` prompt from `scheduled-reviews.md` and explicitly invoke the `notion-todo-manager` skill.

- [ ] **Step 3: Inspect the schedule list**

Expected:

```text
Todo 일일 점검  enabled  every day 09:00  Asia/Seoul
Todo 주간 정리  enabled  Monday 09:15  Asia/Seoul
```

- [ ] **Step 4: Run each schedule once manually**

Expected: each run posts a concise point-in-time report in its Claude conversation, performs no Notion mutation, and does not ask the user to approve or acknowledge the report.

### Task 6: Final verification

**Files:**
- Verify: `/Users/siho/.agents/skills/notion-todo-manager/SKILL.md`
- Verify: `/Users/siho/.claude/skills/notion-todo-manager/SKILL.md`
- Verify externally: Notion `Todo` schema and Claude schedule list

**Interfaces:**
- Consumes: All prior tasks.
- Produces: A complete, shared, operational Todo workflow.

- [ ] **Step 1: Re-run structural validation**

Run:

```bash
python3 /Users/siho/.codex/skills/.system/skill-creator/scripts/quick_validate.py /Users/siho/.agents/skills/notion-todo-manager
```

Expected: success.

- [ ] **Step 2: Re-fetch the Notion schema**

Expected: existing schema is unchanged except for one `관련 링크` URL property.

- [ ] **Step 3: Verify schedule safety**

Inspect the latest daily and weekly executions.

Expected: both produced reports, neither mutated Notion, and neither required a response.

- [ ] **Step 4: Record the operational handoff**

Report the canonical skill path, Claude link path, database URL, schedule names and times, validation result, and any test Todo that still needs an explicit cleanup request.
