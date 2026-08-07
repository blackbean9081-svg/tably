SELECT r.slot_id, COUNT(*) AS active_count
FROM reservation r
WHERE r.status IN ('PENDING_PAYMENT', 'CONFIRMED')
GROUP BY r.slot_id
HAVING COUNT(*) > 1;

SELECT COUNT(*) AS loadtest_active_reservations
FROM reservation r
JOIN member m ON m.id = r.member_id
WHERE m.email LIKE 'loadtest-%@tably.com'
  AND r.status IN ('PENDING_PAYMENT', 'CONFIRMED');

SELECT r.slot_id, r.status, COUNT(*) AS cnt
FROM reservation r
JOIN member m ON m.id = r.member_id
WHERE m.email LIKE 'loadtest-%@tably.com'
GROUP BY r.slot_id, r.status
ORDER BY r.slot_id;
