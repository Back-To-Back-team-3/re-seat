# Shared AI Todo Management Design

## Goal

Use the existing Notion `Todo` database as the single source of truth for follow-up work discovered or managed in Claude and Codex conversations. Both assistants can create and update tasks in response to an explicit user request. Claude additionally performs read-only scheduled daily and weekly reviews.

## Existing Todo Database

The design uses the current properties:

- `작업 이름`: action-oriented task title
- `설명`: context, rationale, and completion criteria
- `상태`: `시작 전`, `진행 중`, or `완료`
- `담당자`: person responsible for the work
- `마감일`: deadline when known
- `우선순위`: `높음`, `보통`, or `낮음`
- `작업 유형`: `🐞 버그`, `💬 기능 요청`, or `💅 다듬기`
- `노력 수준`: `작게`, `보통`, or `크게`
- `업데이트 시간`: Notion-managed last-edited timestamp

Add one property:

- `관련 링크`: URL pointing to the conversation, document, issue, or other source context

No `출처` or `마지막 점검일` property is needed. The originating assistant does not affect task execution, and `업데이트 시간` already supports stale-task detection.

## Shared Claude and Codex Behavior

### Creating tasks

1. When the user explicitly asks to add a Todo, create it immediately.
2. When an assistant infers a follow-up task, ask for confirmation with a concise proposed title before creating it.
3. Before creation, search incomplete tasks for a likely duplicate.
4. If a likely duplicate exists, propose updating the existing task instead of creating another.
5. Populate only values supported by the conversation. Do not invent deadlines, priorities, or effort estimates.
6. Assign the user when the correct Notion identity is known; otherwise leave the assignee unset rather than guessing.

### Updating tasks

Both assistants may update a task after the user explicitly requests or confirms the change:

- Change `상태` to `진행 중` when work actually begins.
- Change `상태` to `완료` only after completion is established.
- Add context, completion criteria, or a related URL.
- Apply a user-requested property change immediately.

An assistant may suggest a change, but it must not apply the change until the user explicitly requests or confirms it. This applies especially to:

- changing a deadline or priority that the user did not explicitly request;
- merging duplicate tasks;
- marking a task complete when completion is uncertain;
- splitting a large task into multiple tasks.

Never delete tasks automatically. Recommend archival or cleanup during the weekly review and act only after an explicit user request.

## Duplicate Detection

Search incomplete tasks before creating a new one. Treat a task as a duplicate candidate when its intended outcome and completion criteria substantially overlap, even if its title differs. A shared keyword alone is not enough.

When a candidate is found, show the existing task and offer the smallest appropriate action:

- keep the existing task unchanged;
- append new context or a related link;
- revise properties after an explicit user request; or
- create a separate task when the outcomes are genuinely distinct.

## Claude Scheduled Reviews

Claude has the same conversational Todo capabilities as Codex plus two scheduled reviews. Scheduled reviews are strictly read-only: they inspect and report but never modify Todo. Review output remains in the scheduled Claude conversation and is not copied into Notion.

### Daily morning review

Inspect incomplete tasks and report:

- tasks due today;
- overdue tasks;
- `진행 중` tasks with no recent update;
- tasks missing useful assignee, priority, or deadline information;
- up to three recommended priorities for the day.

Claude does not make corrections during the scheduled run, including seemingly low-risk edits. It may recommend changes in the report. The user can later request any desired change explicitly in a Claude or Codex conversation.

Suggested report format:

> 오늘 점검: 마감 2개, 기한 초과 1개, 정보 누락 2개
>
> 우선 처리 추천: A → B → C
>
> 변경 제안: B의 마감일 조정, D에 우선순위 지정

### Weekly review

Inspect the full Todo database and report:

- likely duplicates;
- long-stale incomplete tasks;
- tasks with unclear completion criteria;
- oversized tasks that may need decomposition;
- tasks that may be complete or suitable for archival.

Claude does not merge, split, complete, archive, delete, or otherwise update tasks during the scheduled review. Any later change requires an explicit user request.

## Failure and Safety Behavior

- If the Notion connector is unavailable, report that no database change was made and retain the proposed change in the conversation for retry.
- If task search fails, do not create blindly; explain that duplicate checking could not be completed and ask whether to retry.
- If creation or update returns an uncertain result, fetch the task before retrying to avoid duplicate writes.
- Treat Notion as canonical when conversation memory conflicts with current database state.
- Keep scheduled reports concise because Todo data changes frequently and the report is only a point-in-time summary.

## Acceptance Criteria

- Claude and Codex follow the same confirmation, creation, duplicate-checking, and update rules.
- Explicit Todo requests execute without an extra confirmation.
- Inferred follow-up tasks require confirmation before creation.
- Both assistants search incomplete tasks before creating a task.
- The Notion schema gains only `관련 링크`.
- Claude produces a daily review and a weekly review in its scheduled conversation without changing Todo.
- Scheduled reviews are read-only and require no user response.
- Todo changes occur only after an explicit user request or confirmation in conversation.
- No assistant automatically deletes tasks or performs consequential cleanup.
