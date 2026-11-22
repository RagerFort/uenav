-- Office hours (owner, 2026-11-22, from UE's announcement): "Regular office hours at both UE Manila and UE Caloocan
-- are from 8:00 a.m. to 12:00 noon and 1:00 p.m. to 5:00 p.m., Mondays to Fridays. The Admissions Offices of both
-- campuses will continue to be open on Saturdays during the ongoing enrollment period."
-- Every office gets the regular hours; the Admissions Office also opens on Saturdays during enrollment. Hours an
-- admin already typed are kept (only empty or sample hours are filled). The Security Office is not an office with
-- office hours and keeps its own. Run after 04, 06 and 07 (new offices come from them), and again after they run.

USE uenav;

UPDATE node SET open_hours = 'Mon-Fri 8:00 AM-12:00 noon and 1:00-5:00 PM; also Saturdays during enrollment'
WHERE node_id = 19 AND (open_hours IS NULL OR open_hours LIKE '%sample%');

UPDATE node SET open_hours = 'Mon-Fri 8:00 AM-12:00 noon and 1:00-5:00 PM'
WHERE node_type = 'OFFICE' AND node_id NOT IN (19, 24) AND (open_hours IS NULL OR open_hours LIKE '%sample%');
