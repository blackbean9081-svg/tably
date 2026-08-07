DELETE FROM reservation
WHERE member_id IN (
    SELECT id FROM member WHERE email LIKE 'loadtest-%@tably.com'
);
