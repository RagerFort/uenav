# Generates sql/04_engineering_building.sql: the College of Engineering building, floors 1 to 4, from the
# emergency-exit floor plans posted on each floor (photos of EN 113, EN 217, EN 310 and EN 420, Nov 2026).
#
# Run from the project folder:   .\sql\tools\engineering-building.ps1
# Then load it:                   mysql -u root -p uenav < sql\04_engineering_building.sql
#
# How positions work
# - u = metres from the NORTH end of the building, v = metres EAST of the main hallway's centre line.
#   The floor plans are drawn sideways: their left wing is the north end (the I's top bar on the campus map) and the
#   top of the plan is east. This orientation is an assumption from the building's shape; flip $NorthIsPlanLeft if
#   it turns out wrong when checked on site.
# - Building length 104.6 m (owner's measurement on a map distance calculator). The campus map draws it 484 units
#   long (y 118 to 602), so 1 m = 4.627 map units. Rooms are placed in the order and spacing of the plans.
# - There is NO elevator (owner, 2026-11-09): floors are joined only by the four stairwells, two at each end.

$ErrorActionPreference = "Stop"
$PerMetre = 484 / 104.6          # map units per metre
$X0 = 1346                # map x of the hallway centre line
$Y0 = 118                 # map y of the north end
$FlightM = 9.0            # walking metres for one floor of stairs

# floors: id in the database, number, name
$Floors = @(
  @{ id = 1; num = 1; name = '1st floor' },
  @{ id = 3; num = 2; name = '2nd floor' },
  @{ id = 4; num = 3; name = '3rd floor' },
  @{ id = 2; num = 4; name = '4th floor' }
)

# Rooms per floor: name, type, u, then side N (east of the hallway), S (west) or an explicit v for wing rooms.
# u values were read off the plans (plan x position x plan scale).
$Rooms = @{
  1 = @(
    @('Class Room (EN 1F)', 'ROOM', 16.8, 'N'), @('Mechanical Engineering Lab', 'LAB', 22.4, 'N'),
    @('Ministeam Power Plant', 'LAB', 27.1, 'N'), @("Dean's Office (College of Engineering)", 'OFFICE', 37.0, 'N'),
    @('EN 112', 'ROOM', 51.7, 'N'), @('EN 113', 'ROOM', 57.3, 'N'), @('ME Tool Room', 'ROOM', 63.3, 'N'),
    @('Machine Fabrication Room', 'LAB', 83.9, 'N'),
    @('Accreditation Room', 'ROOM', 17.5, 'S'), @('Faculty Briefing and Training Room', 'ROOM', 24.5, 'S'),
    @('Fluid Mechanics and Hydraulics Lab', 'LAB', 34.0, 'S'), @('CE Tool Room', 'ROOM', 41.3, 'S'),
    @('Structural, Material and Testing Lab', 'LAB', 46.9, 'S'), @('Soil Mechanics Laboratory', 'LAB', 52.5, 'S'),
    @('EN 111B', 'ROOM', 59.0, 'S'), @('CE Faculty Room', 'OFFICE', 65.9, 'S'), @('CPE Faculty Room', 'OFFICE', 71.0, 'S'),
    @('ECE Faculty Room', 'OFFICE', 76.2, 'S'), @('EE Faculty Room', 'OFFICE', 81.4, 'S'),
    @('ME Faculty Room', 'OFFICE', 86.5, 'S'),
    @('ACES / PICE Office', 'OFFICE', 4.7, 10.7), @('EN 102', 'ROOM', 4.7, 5.2), @('EN 101', 'ROOM', 4.7, -2.8),
    @('EN 103', 'ROOM', 12.5, 5.5),
    @('CAS Faculty Room', 'OFFICE', 101.2, 10.7), @('EN 116', 'ROOM', 101.2, 5.2), @('Lab Room (EN 1F)', 'LAB', 101.2, -2.8)
  )
  2 = @(
    @('DCSS Faculty Room', 'OFFICE', 16.4, 'N'), @('Open Lab Room', 'LAB', 25.4, 'N'), @('ECE Lab I', 'LAB', 30.1, 'N'),
    @('ECE Lab II', 'LAB', 35.5, 'N'), @('ECE Lab III', 'LAB', 40.5, 'N'), @('ECE Lab IV', 'LAB', 46.2, 'N'),
    @('Satellite Repair Room', 'LAB', 50.9, 'N'), @('ECE / CoE Tool Room', 'ROOM', 56.2, 'N'),
    @("Electrical Engineering Shop", 'LAB', 60.0, 'N'), @('Electrical Power Engineering Lab I', 'LAB', 64.4, 'N'),
    @('Electrical Power Engineering Lab II', 'LAB', 73.2, 'N'), @('Electrical Power Engineering Lab III', 'LAB', 81.3, 'N'),
    @('Electrical Power Engineering Lab IV', 'LAB', 88.2, 'N'),
    @('Bio Laboratory Stock Room', 'ROOM', 18.5, 'S'), @('Computer Laboratory Room II', 'LAB', 27.0, 'S'),
    @('Computer Laboratory Room III', 'LAB', 34.5, 'S'), @('Computer Laboratory Room IV', 'LAB', 42.1, 'S'),
    @('Computer Laboratory Room V', 'LAB', 49.9, 'S'), @('Computer Laboratory Room VI', 'LAB', 57.5, 'S'),
    @('Computer Laboratory Room VII', 'LAB', 65.0, 'S'), @('Computer Laboratory Room VIII', 'LAB', 72.5, 'S'),
    @('EN 206', 'ROOM', 79.4, 'S'), @('EN 205', 'ROOM', 87.0, 'S'),
    @('EN 217', 'ROOM', 4.1, 8.2), @('Chemistry Laboratory Stock Room', 'ROOM', 4.1, 2.5), @('EN 215', 'ROOM', 4.1, -3.8),
    @("Department Chairmen's Offices", 'OFFICE', 12.5, 5.6), @('IFEE / UESB Office', 'OFFICE', 12.5, -5.0),
    @('Net Lab', 'LAB', 95.8, 9.4), @('EE Tool Room', 'ROOM', 95.8, 3.5), @('EN 219', 'ROOM', 103.0, 8.5),
    @('Physics Stock Room', 'ROOM', 103.0, 0.9), @('EN 201', 'ROOM', 103.0, -5.0)
  )
  3 = @(
    @('Drawing Area', 'ROOM', 16.0, 'N'), @('Engineering Library', 'LIBRARY', 38.8, 'N'),
    @('Faculty Nook', 'ROOM', 45.6, 'N'), @('Discussion Room I', 'ROOM', 58.6, 'N'), @('Discussion Room II', 'ROOM', 60.6, 'N'),
    @('Multi-Purpose Hall II', 'ROOM', 64.0, 'N'), @('Multi-Purpose Hall Office', 'OFFICE', 68.6, 'N'),
    @('Multi-Purpose Hall I', 'ROOM', 77.3, 'N'),
    @('EN 313', 'ROOM', 19.9, 'S'), @('EN 312', 'ROOM', 27.0, 'S'), @('EN 311', 'ROOM', 34.5, 'S'), @('EN 310', 'ROOM', 42.5, 'S'),
    @('EN 309', 'ROOM', 50.0, 'S'), @('EN 308', 'ROOM', 57.1, 'S'), @('EN 307', 'ROOM', 64.6, 'S'), @('EN 306', 'ROOM', 72.0, 'S'),
    @('EN 305', 'ROOM', 79.2, 'S'), @('EN 304', 'ROOM', 86.0, 'S'),
    @('EN 319', 'ROOM', 5.9, 10.4), @('EN 318', 'ROOM', 5.9, 3.1), @('EN 317', 'ROOM', 5.9, -3.7),
    @('Multimedia Section (Library)', 'ROOM', 12.7, 5.0),
    @('Office (EN 3F)', 'OFFICE', 94.0, 8.0), @('IECEP Office', 'OFFICE', 94.0, 3.0),
    @('EN 303', 'ROOM', 101.5, 10.2), @('EN 302', 'ROOM', 101.5, 2.8), @('EN 301', 'ROOM', 101.5, -4.7)
  )
  4 = @(
    @('EN 401', 'ROOM', 23.0, 'N'), @('EN 403', 'ROOM', 28.8, 'N'), @('EN 405', 'ROOM', 35.6, 'N'), @('EN 407', 'ROOM', 42.0, 'N'),
    @('EN 409', 'ROOM', 47.8, 'N'), @('EN 411', 'ROOM', 53.6, 'N'), @('EN 413', 'ROOM', 59.2, 'N'), @('EN 415', 'ROOM', 64.6, 'N'),
    @('EN 417', 'ROOM', 69.9, 'N'), @('EN 419', 'ROOM', 75.3, 'N'), @('EN 421', 'ROOM', 80.4, 'N'), @('EN 423', 'ROOM', 85.8, 'N'),
    @('EN 425', 'ROOM', 89.5, 'N'),
    @('EN 402', 'ROOM', 23.2, 'S'), @('EN 404', 'ROOM', 30.2, 'S'), @('EN 406', 'ROOM', 35.8, 'S'), @('EN 408', 'ROOM', 42.7, 'S'),
    @('EN 410', 'ROOM', 48.4, 'S'), @('EN 412', 'ROOM', 53.9, 'S'), @('EN 414', 'ROOM', 59.6, 'S'), @('EN 416', 'ROOM', 64.8, 'S'),
    @('EN 418', 'ROOM', 70.3, 'S'), @('EN 420', 'ROOM', 75.9, 'S'), @('EN 422', 'ROOM', 80.8, 'S'), @('EN 424', 'ROOM', 86.2, 'S'),
    @('EN 426', 'ROOM', 89.5, 'S'),
    @('EN D1', 'ROOM', 4.0, -10.0), @('EN D2', 'ROOM', 4.0, -2.9), @('EN D3', 'ROOM', 4.0, 5.0), @('EN D4', 'ROOM', 4.0, 13.0),
    @('EN D5', 'ROOM', 13.5, 4.0), @('EN D6', 'ROOM', 13.5, 9.0),
    @('EN 427', 'ROOM', 95.6, 12.0), @('EN 428', 'ROOM', 95.6, 4.7), @('EN 433', 'ROOM', 102.8, 17.0),
    @('EN 429', 'ROOM', 102.8, 10.9), @('EN 430', 'ROOM', 102.8, 5.4), @('EN 431', 'ROOM', 102.8, 0.0),
    @('EN 432', 'ROOM', 102.8, -5.4), @('EN 434', 'ROOM', 102.8, -10.0)
  )
}
# Nodes that already exist in the database keep their ids (the usage log may point at them).
$KeepIds = @{ 'EN 420' = 42; 'Male CR (EN 1F, north)' = 43; 'Male CR (EN 4F, north)' = 44 }

$NorthWing = @{ u = 12.0; vs = @(-14, -7, 0, 7, 14) }
$SouthWing = @{ u = 95.0; vs = @(-8, 0, 8, 16) }
$Stairs = @(
  @{ key = 'north stairs, east side'; u = 12.0; v = 13.0 },
  @{ key = 'north stairs, west side'; u = 12.0; v = -15.0 },
  @{ key = 'south stairs, east side'; u = 94.0; v = 17.0 },
  @{ key = 'south stairs, west side'; u = 96.0; v = -9.0 }
)

$nodes = New-Object System.Collections.ArrayList
$edges = New-Object System.Collections.ArrayList
$script:nextNode = 100
$script:nextEdge = 100

function Add-Node($floorId, $name, $type, $u, $v, $desc = $null, $id = $null) {
  $u = [double]"$u"; $v = [double]"$v"
  if (-not $id) {
    if ($KeepIds.ContainsKey($name)) { $id = $KeepIds[$name] } else { $id = $script:nextNode; $script:nextNode++ }
  }
  $n = [pscustomobject]@{ id = $id; floor = $floorId; name = $name; type = $type; u = $u; v = $v; desc = $desc
    x = [int][math]::Round($X0 + $v * $PerMetre); y = [int][math]::Round($Y0 + $u * $PerMetre) }
  [void]$nodes.Add($n); return $n
}
function Add-Edge($a, $b, $type = 'WALK', $m = $null) {
  if ($null -eq $m) { $m = [math]::Max(1.0, [math]::Round([math]::Sqrt(($a.u - $b.u) * ($a.u - $b.u) + ($a.v - $b.v) * ($a.v - $b.v)), 1)) }
  $acc = if ($type -eq 'STAIRS') { 'FALSE' } else { 'TRUE' }
  [void]$edges.Add([pscustomobject]@{ id = $script:nextEdge; a = $a.id; b = $b.id; m = $m; type = $type; acc = $acc })
  $script:nextEdge++
}
function Nearest($list, $u, $v) {
  $best = $null; $bd = [double]::MaxValue
  foreach ($n in $list) { $d = [math]::Abs($n.u - $u) * 2 + [math]::Abs($n.v - $v); if ($d -lt $bd) { $bd = $d; $best = $n } }
  return $best
}

$stairNodes = @{}
foreach ($f in $Floors) {
  $fl = "EN $($f.num)F"
  # wing corridors
  $nw = @(); foreach ($v in $NorthWing.vs) { $nw += Add-Node $f.id "$fl north wing hallway $v" 'JUNCTION' $NorthWing.u $v }
  $sw = @(); foreach ($v in $SouthWing.vs) { $sw += Add-Node $f.id "$fl south wing hallway $v" 'JUNCTION' $SouthWing.u $v }
  for ($i = 1; $i -lt $nw.Count; $i++) { Add-Edge $nw[$i - 1] $nw[$i] }
  for ($i = 1; $i -lt $sw.Count; $i++) { Add-Edge $sw[$i - 1] $sw[$i] }
  $nCenter = $nw | Where-Object { $_.v -eq 0 }; $sCenter = $sw | Where-Object { $_.v -eq 0 }

  # main hallway: one junction per room column, from the north wing to the south wing
  $stem = @($Rooms[$f.num] | Where-Object { $_[3] -is [string] })
  $us = @($stem | ForEach-Object { [math]::Round($_[2], 0) } | Sort-Object -Unique)
  $hall = @($nCenter)
  foreach ($u in $us) { if ($u -gt $NorthWing.u + 1 -and $u -lt $SouthWing.u - 1) { $hall += Add-Node $f.id "$fl hallway $u m" 'JUNCTION' $u 0 } }
  $hall += $sCenter
  for ($i = 1; $i -lt $hall.Count; $i++) { Add-Edge $hall[$i - 1] $hall[$i] }

  # rooms
  foreach ($r in $Rooms[$f.num]) {
    if ($r[3] -is [string]) {
      $v = if ($r[3] -eq 'N') { 5.0 } else { -5.0 }
      $room = Add-Node $f.id $r[0] $r[1] $r[2] $v
      Add-Edge $room (Nearest $hall $r[2] 0)
    } else {
      $room = Add-Node $f.id $r[0] $r[1] $r[2] ([double]$r[3])
      $wing = if ($r[2] -lt 50) { $nw } else { $sw }
      Add-Edge $room (Nearest $wing $r[2] $r[3])
    }
  }
  # comfort rooms: both ends, except 4F where the south wing has classrooms instead
  $crs = @(@('Male CR', 'north', 3.0, 13.0), @('Female CR', 'north', 3.0, -13.0))
  if ($f.num -ne 4) { $crs += @(, @('Male CR', 'south', 103.0, 17.0)); $crs += @(, @('Female CR', 'south', 103.0, -8.0)) }
  foreach ($c in $crs) {
    $cr = Add-Node $f.id "$($c[0]) (EN $($f.num)F, $($c[1]))" 'RESTROOM' $c[2] $c[3]
    $wing = if ($c[1] -eq 'north') { $nw } else { $sw }
    Add-Edge $cr (Nearest $wing $c[2] $c[3])
  }
  # stairs (same spot on every floor)
  foreach ($st in $Stairs) {
    $s = Add-Node $f.id "EN $($st.key) ($($f.num)F)" 'STAIRS' $st.u $st.v
    $wing = if ($st.u -lt 50) { $nw } else { $sw }
    Add-Edge $s (Nearest $wing $st.u $st.v)
    $stairNodes["$($st.key)|$($f.num)"] = $s
  }
  # doors on the ground floor
  if ($f.num -eq 1) {
    $script:entrance = Add-Node 1 'College of Engineering Building' 'BUILDING_ENTRANCE' 97.0 -10.5 $null 35
    Add-Edge $script:entrance (Nearest $sw 97 -10.5)
    $script:northExit = Add-Node 1 'EN north exit (1F)' 'EXIT' 8.0 -20.0
    Add-Edge $script:northExit (Nearest $nw 8 -20)
    $eastExit = Add-Node 1 'EN east exit (1F)' 'EXIT' 97.0 29.0
    Add-Edge $eastExit (Nearest $sw 97 29)
  }
}
# stairs between floors
foreach ($st in $Stairs) {
  for ($n = 1; $n -lt 4; $n++) { Add-Edge $stairNodes["$($st.key)|$n"] $stairNodes["$($st.key)|$($n + 1)"] 'STAIRS' $FlightM }
}
# outside: Field southeast corner (13) to the main door, Field northeast corner (11) to the north exit
[void]$edges.Add([pscustomobject]@{ id = $script:nextEdge; a = 13; b = 35; m = 11.5; type = 'WALK'; acc = 'TRUE' }); $script:nextEdge++
[void]$edges.Add([pscustomobject]@{ id = $script:nextEdge; a = 11; b = $script:northExit.id; m = 15.2; type = 'WALK'; acc = 'TRUE' }); $script:nextEdge++

# descriptions
foreach ($n in $nodes) {
  if ($n.name -eq 'Engineering Library') { $n.desc = 'Circulation desk, reference, reserve and multimedia sections, faculty nook and discussion rooms. (SAMPLE text)' }
  if ($n.name -like "Dean's Office*") { $n.desc = 'Office of the Dean, College of Engineering. (SAMPLE text)' }
}

function Q($s) { if ($null -eq $s) { 'NULL' } else { "'" + ($s -replace "'", "''") + "'" } }
$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine('-- College of Engineering building: floors 1 to 4, rooms, comfort rooms and the four stairwells (no elevator).')
[void]$sb.AppendLine('-- GENERATED by sql/tools/engineering-building.ps1 from the posted emergency-exit floor plans. Edit the script and')
[void]$sb.AppendLine('-- run it again rather than editing this file. Positions are estimates (building length 104.6 m, measured on a map).')
[void]$sb.AppendLine('-- Safe to run more than once. Replaces the old SAMPLE indoor rows; keeps the usage log and the ids it uses')
[void]$sb.AppendLine('-- (35 entrance, 42 EN 420, 43 and 44 comfort rooms). Run after 01, 02 (and 03 on databases made before 2026-11-09).')
[void]$sb.AppendLine('-- Then copy GET /api/snapshot into uenav-mobile/src/main/resources/campus-snapshot.json before building the APK.')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('USE uenav;')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('INSERT INTO floor (floor_id, building_id, floor_number, floor_name) VALUES')
[void]$sb.AppendLine((($Floors | Sort-Object { $_.id } | ForEach-Object { "  ($($_.id), 1, $($_.num), '$($_.name)')" }) -join ",`n") + '')
[void]$sb.AppendLine('ON DUPLICATE KEY UPDATE floor_number = VALUES(floor_number), floor_name = VALUES(floor_name);')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('-- every walkway inside the building (and its links to the outside) is rebuilt below')
[void]$sb.AppendLine('DELETE FROM edge WHERE from_node_id IN (SELECT node_id FROM (SELECT node_id FROM node WHERE building_id = 1) b)')
[void]$sb.AppendLine('                  OR to_node_id IN (SELECT node_id FROM (SELECT node_id FROM node WHERE building_id = 1) b);')
$ids = ($nodes | ForEach-Object { $_.id }) -join ', '
[void]$sb.AppendLine('-- old sample waypoints and the elevator (there is none), and rows from an older run of this script')
[void]$sb.AppendLine("DELETE FROM node WHERE building_id = 1 AND node_id NOT IN ($ids)")
[void]$sb.AppendLine('  AND node_id NOT IN (SELECT from_node_id FROM usage_log) AND node_id NOT IN (SELECT to_node_id FROM usage_log);')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('INSERT INTO node (node_id, building_id, floor_id, name, node_type, x, y, description) VALUES')
[void]$sb.AppendLine((($nodes | ForEach-Object { "  ($($_.id), 1, $($_.floor), $(Q $_.name), '$($_.type)', $($_.x), $($_.y), $(Q $_.desc))" }) -join ",`n"))
[void]$sb.AppendLine('ON DUPLICATE KEY UPDATE building_id = VALUES(building_id), floor_id = VALUES(floor_id), name = VALUES(name),')
[void]$sb.AppendLine('  node_type = VALUES(node_type), x = VALUES(x), y = VALUES(y),')
[void]$sb.AppendLine('  description = IFNULL(node.description, VALUES(description));')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('INSERT INTO edge (edge_id, from_node_id, to_node_id, distance_m, edge_type, is_accessible) VALUES')
[void]$sb.AppendLine((($edges | ForEach-Object { "  ($($_.id), $($_.a), $($_.b), $($_.m.ToString([Globalization.CultureInfo]::InvariantCulture)), '$($_.type)', $($_.acc))" }) -join ",`n"))
[void]$sb.AppendLine('ON DUPLICATE KEY UPDATE from_node_id = VALUES(from_node_id), to_node_id = VALUES(to_node_id),')
[void]$sb.AppendLine('  distance_m = VALUES(distance_m), edge_type = VALUES(edge_type), is_accessible = VALUES(is_accessible);')

$out = Join-Path $PSScriptRoot '..\04_engineering_building.sql'
[IO.File]::WriteAllText([IO.Path]::GetFullPath($out), $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
Write-Host ("Wrote {0}: {1} places, {2} walkways" -f (Resolve-Path $out), $nodes.Count, $edges.Count)
