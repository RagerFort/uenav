# Generates sql/07_lucio_tan.sql: the Dr. Lucio C. Tan Building (LCT), ground floor to the 5th floor mezzanine, from
# the architectural floor plans (owner's photos, Nov 2026: Ground, Second, Third, Fourth Floor Plan and Fifth Floor
# Mezzanine Plan, scale 1:200, grid lines 1 to 12).
#
# Run from the project folder:   .\sql\tools\lucio-tan.ps1
# Then load it after 06_tan_yan_kee.sql (UENav SQL:  source C:/path/to/uenav/sql/07_lucio_tan.sql).
#
# What the owner confirmed (2026-11-22)
# - The plans with grid lines 1-12 are the Dr. Lucio C. Tan Building (the long building beside the Old Academic
#   Building on the UE site development plan).
# - The 5th floor holds the library (west) and a hall with a stage (east); the mezzanine above it has the high school
#   library mezzanine and the light and sound control room.
#
# How positions work
# - The building is 90 m long and 16 m deep. u = metres from grid line 1, v = metres from the main hallway's centre
#   line: negative on the 9350 side (the elevators, the two fire-exit stairs, rooms 4.7 m deep), positive on the
#   6650 side (the main stair, the canopy and the door to the campus).
# - ASSUMPTION: grid line 1 is the west end and the 6650 side faces the campus (south). The campus door (node 29)
#   is moved to the lobby, which is between grid lines 5 and 6. Flip $WestIsGrid1 if it turns out wrong on site.
# - The campus map draws the building from x 880 to 1198, so the 90 m are squeezed a little along it; walking
#   distances use the real metres. One floor of stairs counts 15 m, one floor by elevator 6 m (as in Tan Yan Kee).
# - Room u values are read off the plans between the grid lines; names are the plans' labels. Generic rooms get
#   the floor in their name so they can be told apart.

$ErrorActionPreference = "Stop"
$WestIsGrid1 = $true
$X1 = 880.0; $X12 = 1198.0            # map x of grid lines 1 and 12 (the campus map's Lucio Tan outline)
$YMid = 198.0                         # map y of the main hallway at x 1040
$Slope = 0.008                        # the outline rises slightly to the west
$UnitsPerMetreV = 5.0                 # across the building (drawing only)
$FlightM = 15.0
$LiftM = 6.0
$Length = 90.0

# grid lines (u in metres)
$G = @{ '1' = 0.0; '2' = 4.5; '3' = 13.5; '4' = 22.5; '5' = 31.5; "5'" = 36.0; '6' = 40.5; '7' = 49.5; '8' = 58.5
        '9' = 67.5; '10' = 76.5; '11' = 85.5; '12' = 90.0 }
$LobbyU = 36.0                        # between grid lines 5 and 6
$MainStairU = 33.75                   # grid 5 to 5', 6650 side
$ElevatorU = 36.0                     # three cars, grid 5 to 6, 9350 side
$WestStairU = 2.25; $EastStairU = 87.75   # fire exits, grid 1-2 and 11-12, 9350 side

# floors: database id, number, name, tag used in place names
$Floors = @(
  @{ id = 15; num = 1; name = 'Ground floor';        tag = 'LCT GF' },
  @{ id = 16; num = 2; name = '2nd floor';           tag = 'LCT 2F' },
  @{ id = 17; num = 3; name = '3rd floor';           tag = 'LCT 3F' },
  @{ id = 18; num = 4; name = '4th floor';           tag = 'LCT 4F' },
  @{ id = 19; num = 5; name = '5th floor';           tag = 'LCT 5F' },
  @{ id = 20; num = 6; name = '5th floor mezzanine'; tag = 'LCT 5M' }
)

# Rooms per floor: name, type, u (metres from grid 1), side N (9350 side), S (6650 side) or C (in the hallway).
$Rooms = @{}
function Room([int]$floorNum, [string]$name, [string]$type, [double]$u, [string]$side) {
  if (-not $Rooms.ContainsKey($floorNum)) { $Rooms[$floorNum] = New-Object System.Collections.ArrayList }
  [void]$Rooms[$floorNum].Add([pscustomobject]@{ name = $name; type = $type; u = $u; side = $side })
}

# Ground floor (offices of the basic education department, the canteen, kindergarten and shop rooms)
Room 1 'Male CR (LCT GF)' 'RESTROOM' 5.0 'S'; Room 1 'Female CR (LCT GF)' 'RESTROOM' 6.8 'S'
Room 1 "Principal's Office (LCT)" 'OFFICE' 9.4 'S'; Room 1 'Conference Room (LCT GF)' 'ROOM' 16.7 'S'
Room 1 'Staff Room (LCT GF)' 'OFFICE' 19.8 'S'; Room 1 'Pantry (LCT GF)' 'ROOM' 24.8 'S'
Room 1 'Records Office (LCT)' 'OFFICE' 28.9 'S'
Room 1 'Supply and Maintenance Room (LCT)' 'ROOM' 5.5 'N'; Room 1 'Kitchen (LCT GF)' 'ROOM' 10.3 'N'
Room 1 'Canteen (LCT GF)' 'CANTEEN' 22.5 'N'
Room 1 'Kindergarten Faculty Room' 'OFFICE' 45.4 'N'
Room 1 'GF Classroom A (LCT)' 'ROOM' 45.0 'S'; Room 1 'GF Classroom B (LCT)' 'ROOM' 54.0 'S'
Room 1 'GF Classroom C (LCT)' 'ROOM' 54.0 'N'
Room 1 'Technology and Home Economics Room' 'ROOM' 63.0 'S'; Room 1 'Home Economics Practice House' 'ROOM' 63.0 'N'
Room 1 'Drafting and Trades Room' 'ROOM' 72.0 'S'
Room 1 'Physical Arts Room' 'ROOM' 81.0 'S'; Room 1 'Machine Area (Physical Arts)' 'ROOM' 81.0 'N'

# 2F
Room 2 'Male CR (LCT 2F)' 'RESTROOM' 5.0 'N'; Room 2 'Multimedia Room (LCT 2F)' 'ROOM' 16.6 'N'
Room 2 'High School Faculty Room' 'OFFICE' 26.8 'N'
Room 2 'Guidance Office (LCT)' 'OFFICE' 45.3 'N'; Room 2 'Grade School Faculty Room' 'OFFICE' 54.6 'N'
Room 2 'LCT Classroom 1' 'ROOM' 63.4 'N'; Room 2 'LCT Classroom 2' 'ROOM' 73.0 'N'
Room 2 'Female CR (LCT 2F)' 'RESTROOM' 83.0 'N'
Room 2 'Computer Laboratory 1 (LCT 2F)' 'LAB' 9.6 'S'; Room 2 'Computer Laboratory 2 (LCT 2F)' 'LAB' 26.0 'S'
Room 2 'Speech Laboratory (LCT 2F)' 'LAB' 44.7 'S'
Room 2 'LCT Classroom 6' 'ROOM' 54.6 'S'; Room 2 'LCT Classroom 5' 'ROOM' 63.4 'S'
Room 2 'LCT Classroom 4' 'ROOM' 73.0 'S'; Room 2 'LCT Classroom 3' 'ROOM' 81.9 'S'

# 3F
Room 3 'Male CR (LCT 3F)' 'RESTROOM' 5.2 'N'
Room 3 'LCT Classroom 21' 'ROOM' 17.6 'N'; Room 3 'LCT Classroom 22' 'ROOM' 27.3 'N'
Room 3 'LCT Classroom 9' 'ROOM' 45.0 'N'; Room 3 'LCT Classroom 10' 'ROOM' 53.7 'N'
Room 3 'LCT Classroom 11' 'ROOM' 63.5 'N'; Room 3 'LCT Classroom 12' 'ROOM' 72.8 'N'
Room 3 'Female CR (LCT 3F)' 'RESTROOM' 83.5 'N'
Room 3 'LCT Classroom 20' 'ROOM' 8.5 'S'; Room 3 'LCT Classroom 19' 'ROOM' 17.6 'S'; Room 3 'LCT Classroom 18' 'ROOM' 27.3 'S'
Room 3 'Prefect of Discipline Office (LCT 3F)' 'OFFICE' 38.3 'S'
Room 3 'LCT Classroom 17' 'ROOM' 45.0 'S'; Room 3 'LCT Classroom 16' 'ROOM' 53.7 'S'
Room 3 'LCT Classroom 15' 'ROOM' 63.5 'S'; Room 3 'LCT Classroom 14' 'ROOM' 72.8 'S'
Room 3 'LCT Classroom 13' 'ROOM' 82.2 'S'

# 4F (science laboratories)
Room 4 'Male CR (LCT 4F)' 'RESTROOM' 6.2 'N'; Room 4 'Instrumentation Room (LCT 4F)' 'ROOM' 11.0 'N'
Room 4 'Biology Laboratory (LCT 4F)' 'LAB' 25.4 'N'
Room 4 'LCT Classroom 23' 'ROOM' 45.1 'N'; Room 4 'LCT Classroom 24' 'ROOM' 54.5 'N'
Room 4 'LCT Classroom 25' 'ROOM' 63.1 'N'; Room 4 'LCT Classroom 26' 'ROOM' 71.8 'N'
Room 4 'Female CR (LCT 4F)' 'RESTROOM' 83.0 'N'
Room 4 'Chemistry Laboratory (LCT 4F)' 'LAB' 9.9 'S'; Room 4 'Physics Laboratory (LCT 4F)' 'LAB' 24.9 'S'
Room 4 'Prefect of Discipline Office (LCT 4F)' 'OFFICE' 37.6 'S'
Room 4 'LCT Classroom 27' 'ROOM' 45.1 'S'; Room 4 'LCT Classroom 28' 'ROOM' 54.5 'S'
Room 4 'LCT Classroom 29' 'ROOM' 63.1 'S'; Room 4 'LCT Classroom 30' 'ROOM' 71.8 'S'
Room 4 'LCT Classroom 31' 'ROOM' 81.0 'S'

# 5F (owner): the library at the west end, a hall with a stage at the east end
Room 5 'High School Library (LCT 5F)' 'LIBRARY' 18.0 'C'
Room 5 'Hall (LCT 5F)' 'FACILITY' 58.5 'C'; Room 5 'Stage (LCT 5F)' 'FACILITY' 81.0 'C'

# 5th floor mezzanine (plan "Fifth Floor Mezzanine Plan")
Room 6 'High School Library Mezzanine (LCT)' 'LIBRARY' 20.0 'N'
Room 6 'Light and Sound Control Room (LCT)' 'ROOM' 42.0 'S'

# ------------------------------------------------------------------ geometry
$Side = @{ N = -4.0; S = 5.0; C = 0.0 }
function MapPoint([double]$u, [double]$v) {
  $t = $u / $Length
  if (-not $WestIsGrid1) { $t = 1.0 - $t }
  $x = $X1 + $t * ($X12 - $X1)
  $y = $YMid + ($x - 1040.0) * (-$Slope) + $v * $UnitsPerMetreV
  return @($x, $y)
}

$nodes = New-Object System.Collections.ArrayList
$edges = New-Object System.Collections.ArrayList
$script:nextNode = 2000
$script:nextEdge = 2000

function Add-Node($floorId, [string]$name, [string]$type, [double]$u, [double]$v, $desc = $null) {
  $pt = MapPoint $u $v
  $n = [pscustomobject]@{ id = $script:nextNode; floor = $floorId; name = $name; type = $type; u = $u; v = $v
    desc = $desc; x = [int][math]::Round($pt[0]); y = [int][math]::Round($pt[1]) }
  $script:nextNode++
  [void]$nodes.Add($n); return $n
}
function Add-Edge($a, $b, [string]$type = 'WALK', $m = $null) {
  if ($null -eq $m) {
    $m = [math]::Max(1.0, [math]::Round([math]::Sqrt(($a.u - $b.u) * ($a.u - $b.u) + ($a.v - $b.v) * ($a.v - $b.v)), 1))
  }
  $acc = if ($type -eq 'STAIRS') { 'FALSE' } else { 'TRUE' }
  [void]$edges.Add([pscustomobject]@{ id = $script:nextEdge; a = $a.id; b = $b.id; m = [double]$m; type = $type; acc = $acc })
  $script:nextEdge++
}

$vertical = @{}   # "key|floor" -> stairs or elevator node
foreach ($f in $Floors) {
  $tag = $f.tag; $fid = $f.id; $num = $f.num
  $list = if ($Rooms.ContainsKey($num)) { @($Rooms[$num]) } else { @() }
  $short = $tag.Substring(4)
  $mezz = $num -eq 6

  # main hallway: a junction at every room and at the stairs, lobby and elevators
  $us = New-Object System.Collections.ArrayList
  if ($mezz) {
    foreach ($u in $WestStairU, 27.0, $MainStairU, $LobbyU, 42.0) { [void]$us.Add([double]$u) }
  } else {
    foreach ($u in $WestStairU, $MainStairU, $LobbyU, $EastStairU) { [void]$us.Add([double]$u) }
    if ($num -eq 5) { [void]$us.Add(27.0); [void]$us.Add(42.0) }
  }
  foreach ($r in $list) { [void]$us.Add([math]::Round($r.u, 1)) }
  $hallU = @($us | Sort-Object -Unique)
  $hall = @()
  foreach ($u in $hallU) { $hall += Add-Node $fid "$tag hallway $([math]::Round($u)) m" 'JUNCTION' $u 0 }
  for ($i = 1; $i -lt $hall.Count; $i++) { Add-Edge $hall[$i - 1] $hall[$i] }
  $hallAt = @{}; for ($i = 0; $i -lt $hall.Count; $i++) { $hallAt[[double]$hallU[$i]] = $hall[$i] }

  # stairs and elevators
  $s = Add-Node $fid "LCT west fire exit stairs ($short)" 'STAIRS' $WestStairU -5; Add-Edge $s $hallAt[$WestStairU]
  $vertical["west|$num"] = $s
  $s = Add-Node $fid "LCT main stairs ($short)" 'STAIRS' $MainStairU 6; Add-Edge $s $hallAt[$MainStairU]
  $vertical["main|$num"] = $s
  if (-not $mezz) {
    $s = Add-Node $fid "LCT east fire exit stairs ($short)" 'STAIRS' $EastStairU -5; Add-Edge $s $hallAt[$EastStairU]
    $vertical["east|$num"] = $s
    $e = Add-Node $fid "LCT elevators ($short)" 'ELEVATOR' $ElevatorU -5; Add-Edge $e $hallAt[$LobbyU]
    $vertical["elevators|$num"] = $e
  }
  # the library stairs (5F library to the mezzanine) and the stairs up to the control room
  if ($num -ge 5) {
    $s = Add-Node $fid "LCT library stairs ($short)" 'STAIRS' 27.0 -3; Add-Edge $s $hallAt[27.0]
    $vertical["library|$num"] = $s
    $s = Add-Node $fid "LCT control room stairs ($short)" 'STAIRS' 42.0 -3; Add-Edge $s $hallAt[42.0]
    $vertical["control|$num"] = $s
  }

  # rooms
  foreach ($r in $list) {
    $room = Add-Node $fid $r.name $r.type $r.u $Side[$r.side]
    Add-Edge $room $hallAt[[double][math]::Round($r.u, 1)]
  }
  if ($num -eq 1) { $script:lobbyGF = $hallAt[$LobbyU] }
}
foreach ($n in $nodes) {
  switch ($n.name) {
    'Canteen (LCT GF)' { $n.desc = 'The dining room beside the kitchen, at the west end of the ground floor.' }
    'High School Library (LCT 5F)' { $n.desc = 'West end of the 5th floor. Stairs inside lead up to the library mezzanine.' }
    'Hall (LCT 5F)' { $n.desc = 'The hall at the east end of the 5th floor, with the stage at its far end.' }
  }
}

# stairs and elevators between floors (stairs: not step-free; the elevators stop at the 5th floor)
for ($n = 1; $n -lt 6; $n++) {
  Add-Edge $vertical["west|$n"] $vertical["west|$($n + 1)"] 'STAIRS' $FlightM
  Add-Edge $vertical["main|$n"] $vertical["main|$($n + 1)"] 'STAIRS' $FlightM
}
for ($n = 1; $n -lt 5; $n++) {
  Add-Edge $vertical["east|$n"] $vertical["east|$($n + 1)"] 'STAIRS' $FlightM
  Add-Edge $vertical["elevators|$n"] $vertical["elevators|$($n + 1)"] 'ELEVATOR' $LiftM
}
Add-Edge $vertical['library|5'] $vertical['library|6'] 'STAIRS' 8.0
Add-Edge $vertical['control|5'] $vertical['control|6'] 'STAIRS' 8.0

# the campus door (node 29) at the lobby, on the 6650 side; the walk from node 10 to it is measured again
$door = MapPoint $LobbyU 10.5
$doorX = [int][math]::Round($door[0]); $doorY = [int][math]::Round($door[1])
$door29 = [pscustomobject]@{ id = 29; u = $LobbyU; v = 10.5 }
Add-Edge $door29 $script:lobbyGF
$walk10to29 = [math]::Max(1.0, [math]::Round([math]::Sqrt([math]::Pow(($doorX - 1040) * 0.248, 2) + [math]::Pow(($doorY - 268) * 0.233, 2)), 1))

# ------------------------------------------------------------------ SQL
function Q($s) { if ($null -eq $s) { 'NULL' } else { "'" + ($s -replace "'", "''") + "'" } }
function Num($d) { ([double]$d).ToString([Globalization.CultureInfo]::InvariantCulture) }
$sb = New-Object System.Text.StringBuilder
function L($s) { [void]$sb.AppendLine($s) }
L '-- Dr. Lucio C. Tan Building (LCT): ground floor to the 5th floor mezzanine: offices of the basic education'
L '-- department, classrooms 1-31, science and computer laboratories, the canteen, the high school library, the hall'
L '-- with its stage, three stairwells and the elevators.'
L '-- GENERATED by sql/tools/lucio-tan.ps1 from the architectural floor plans. Edit the script and run it again'
L '-- rather than editing this file. Positions are estimates (see the script for the assumptions).'
L '-- Safe to run more than once; keeps the usage log. Run after 06_tan_yan_kee.sql.'
L ''
L 'USE uenav;'
L ''
L "UPDATE building SET description = 'Basic education: offices and canteen on the ground floor, classrooms and laboratories on 2F to 4F, the high school library and a hall with a stage on 5F.' WHERE building_id = 4;"
L '-- the campus door, at the lobby'
L "UPDATE node SET x = $doorX, y = $doorY WHERE node_id = 29;"
L "UPDATE edge SET distance_m = $(Num $walk10to29) WHERE (from_node_id = 10 AND to_node_id = 29) OR (from_node_id = 29 AND to_node_id = 10);"
L ''
L 'INSERT INTO floor (floor_id, building_id, floor_number, floor_name) VALUES'
L ((($Floors | ForEach-Object { "  ($($_.id), 4, $($_.num), '$($_.name)')" }) -join ",`n"))
L 'ON DUPLICATE KEY UPDATE building_id = VALUES(building_id), floor_number = VALUES(floor_number), floor_name = VALUES(floor_name);'
L ''
L '-- every walkway inside the building (and its link to the door) is rebuilt below'
L 'DELETE FROM edge WHERE from_node_id IN (SELECT node_id FROM (SELECT node_id FROM node WHERE building_id = 4 AND floor_id IS NOT NULL) b)'
L '                  OR to_node_id IN (SELECT node_id FROM (SELECT node_id FROM node WHERE building_id = 4 AND floor_id IS NOT NULL) b);'
$ids = ($nodes | ForEach-Object { $_.id }) -join ', '
L '-- rows from an older run of this script that are no longer made'
L "DELETE FROM node WHERE building_id = 4 AND floor_id IS NOT NULL AND node_id NOT IN ($ids)"
L '  AND node_id NOT IN (SELECT from_node_id FROM usage_log) AND node_id NOT IN (SELECT to_node_id FROM usage_log);'
L ''
L 'INSERT INTO node (node_id, building_id, floor_id, name, node_type, x, y, description) VALUES'
L ((($nodes | ForEach-Object { "  ($($_.id), 4, $($_.floor), $(Q $_.name), '$($_.type)', $($_.x), $($_.y), $(Q $_.desc))" }) -join ",`n"))
L 'ON DUPLICATE KEY UPDATE building_id = VALUES(building_id), floor_id = VALUES(floor_id), name = VALUES(name),'
L '  node_type = VALUES(node_type), x = VALUES(x), y = VALUES(y),'
L '  description = IFNULL(node.description, VALUES(description));'
L ''
L 'INSERT INTO edge (edge_id, from_node_id, to_node_id, distance_m, edge_type, is_accessible) VALUES'
L ((($edges | ForEach-Object { "  ($($_.id), $($_.a), $($_.b), $(Num $_.m), '$($_.type)', $($_.acc))" }) -join ",`n"))
L 'ON DUPLICATE KEY UPDATE from_node_id = VALUES(from_node_id), to_node_id = VALUES(to_node_id),'
L '  distance_m = VALUES(distance_m), edge_type = VALUES(edge_type), is_accessible = VALUES(is_accessible);'

$out = Join-Path $PSScriptRoot '..\07_lucio_tan.sql'
[IO.File]::WriteAllText([IO.Path]::GetFullPath($out), $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
$searchable = @($nodes | Where-Object { 'JUNCTION', 'STAIRS', 'ELEVATOR' -notcontains $_.type }).Count
Write-Host ("Wrote {0}: {1} places ({2} searchable), {3} walkways" -f (Resolve-Path $out), $nodes.Count, $searchable, $edges.Count)
