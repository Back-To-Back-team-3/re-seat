-- 기존 대기 이력이 변경된 DB 식별키 규칙을 사용하도록 queue_key를 갱신

UPDATE queue_entry_histories
SET queue_key = CONCAT('queue:entry:game:', game_id, ':user:', user_id)
WHERE queue_key LIKE 'queue:game:%:user:%';
