# Generates sql/06_tan_yan_kee.sql: the Tan Yan Kee Academic Building (TYK), ground floor to 10th floor, from the
# emergency-exit and evacuation plans posted on each floor (owner's photos, Nov 2026) and the building directory.
#
# Run from the project folder:   .\sql\tools\tan-yan-kee.ps1
# Then load it:                   mysql -u root -p uenav < sql\06_tan_yan_kee.sql   (after 05_campus_layout.sql)
#
# What the owner confirmed (2026-11-15)
# - The "CFAD building" is TYK's angled west wing (its rooms are TYK 200-205, 300-305, ...). It is part of TYK now.
# - 10 floors: Ground floor, then 2F to 10F (rooms TYK 2xx to TYK 10xx).
# - Anyone may use the elevators: the central bank (3 cars, beside stair 1) and the west bank at the wing.
# - The plan titled WORKSHOP is the 6th floor (library, the CFA workshop wing and the Hall of Fame Gallery).
# - The ground floor is a canteen in the middle of the building, entered from the grass field or through the CFAD
#   wing; the floors above are reached by the stairs and elevators (no offices on the ground floor).
# - A bridge on the 4th floor leads to the 3rd floor of the Engineering building, so EN 3F can be reached by the
#   TYK elevators. It runs from TYK's east end over the HRM building to EN's south wing (seen on the photo).
#
# How positions work
# - The main building is a long bar, drawn along the campus map's TYK outline (OpenStreetMap), slightly turned
#   (6.3 degrees, rising to the east). A place on it is given by its plan x (px, measured on the 2F plan photo,
#   2000 px wide: west lobby 720, stair 1 1395, east end 1950) and t, map units north of the main hallway.
# - The west wing is a turned rectangle: a = map units from its north-west end (stair 4) towards the lobby,
#   b = map units across it, positive towards its north-east side (rooms x00, x02, x04).
# - Distances are metres measured on the map (about 0.24 m per map unit). One floor of stairs counts 15 m (about
#   the time it takes to climb it), one floor by elevator 6 m, so routes take the elevator for more than a floor
#   or two and the stairs for short trips.

$ErrorActionPreference = "Stop"
$BarX = 925.7; $BarY = 698.0          # map point of the main hallway at stair 1 (plan x 1395)
$PxRef = 1395
$UnitsPerPx = 0.3193
$AlongX = 0.9939; $AlongY = -0.1101   # along the bar, towards its east end
$NorthX = -0.1101; $NorthY = -0.9939  # across the bar, towards the field
$WingX0 = 603.5; $WingY0 = 666.5      # middle of the west wing's north-west end
$WaX = 0.818; $WaY = 0.579            # along the wing, towards the lobby
$WbX = 0.573; $WbY = -0.820           # across the wing, towards its north-east side
$MetresPerUnitX = 0.248; $MetresPerUnitY = 0.233
$FlightM = 15.0
$LiftM = 6.0
$BridgeM = 48.0                       # length of the bridge to the Engineering building, measured on the photo

# floors: database id, number, name, tag used in place names, the west wing (rooms / hall only / none) and the
# east block (true / false). On 5F both wings are roof decks.
$Floors = @(
  @{ id = 5;  num = 1;  name = 'Ground floor'; tag = 'TYK GF';  wing = 'hall';  east = $true },
  @{ id = 6;  num = 2;  name = '2nd floor';    tag = 'TYK 2F';  wing = 'rooms'; east = $true },
  @{ id = 7;  num = 3;  name = '3rd floor';    tag = 'TYK 3F';  wing = 'rooms'; east = $true },
  @{ id = 8;  num = 4;  name = '4th floor';    tag = 'TYK 4F';  wing = 'rooms'; east = $true },
  @{ id = 9;  num = 5;  name = '5th floor';    tag = 'TYK 5F';  wing = 'none';  east = $false },
  @{ id = 10; num = 6;  name = '6th floor';    tag = 'TYK 6F';  wing = 'hall';  east = $true },
  @{ id = 11; num = 7;  name = '7th floor';    tag = 'TYK 7F';  wing = 'rooms'; east = $true },
  @{ id = 12; num = 8;  name = '8th floor';    tag = 'TYK 8F';  wing = 'rooms'; east = $true },
  @{ id = 13; num = 9;  name = '9th floor';    tag = 'TYK 9F';  wing = 'rooms'; east = $true },
  @{ id = 14; num = 10; name = '10th floor';   tag = 'TYK 10F'; wing = 'hall';  east = $true }
)

# Rooms per floor: name, type, where, position.
#   N = north of the main hallway (the zigzag rooms), S = south of it, C = in the hallway's open space,
#   L = north of the hallway by the lobby, E = east block, north side (off its second hallway): position = plan x.
#   WN / WS = west wing, north-east / south-west side: position = a.
$Rooms = @{}
function Room($floorNum, $name, $type, $where, $pos) {
  if (-not $Rooms.ContainsKey($floorNum)) { $Rooms[$floorNum] = New-Object System.Collections.ArrayList }
  [void]$Rooms[$floorNum].Add([pscustomobject]@{ name = $name; type = $type; where = $where; pos = [double]$pos })
}
function WingRooms($floorNum, $first) {
  Room $floorNum "TYK $($first)" 'ROOM' 'WN' 26; Room $floorNum "TYK $($first + 2)" 'ROOM' 'WN' 60
  Room $floorNum "TYK $($first + 4)" 'ROOM' 'WN' 94
  Room $floorNum "TYK $($first + 1)" 'ROOM' 'WS' 22; Room $floorNum "TYK $($first + 3)" 'ROOM' 'WS' 56
  Room $floorNum "TYK $($first + 5)" 'ROOM' 'WS' 90
}
function ComfortRooms($floorNum, $tag) {
  Room $floorNum "Female CR ($tag)" 'RESTROOM' 'S' 1140; Room $floorNum "Male CR ($tag)" 'RESTROOM' 'S' 1330
}

# Ground floor (owner): the canteen in the middle; stairs and elevators lead up
Room 1 'TYK Canteen' 'CANTEEN' 'C' 1150

# 2F (plan "2F TYK BUILDING, TYK 206")
WingRooms 2 200
Room 2 'TYK 206' 'ROOM' 'N' 795; Room 2 'TYK 208' 'ROOM' 'N' 895; Room 2 'TYK 210' 'ROOM' 'N' 1000
Room 2 'TYK 212' 'ROOM' 'N' 1120; Room 2 'Common Meeting Room' 'ROOM' 'L' 1215
Room 2 'TYK 207' 'ROOM' 'S' 830; Room 2 'TYK 209' 'ROOM' 'S' 935; Room 2 'TYK 211' 'ROOM' 'S' 1040
ComfortRooms 2 'TYK 2F'
Room 2 'TYK 213' 'ROOM' 'S' 1430; Room 2 'TYK 214' 'ROOM' 'S' 1537; Room 2 'TYK 215' 'ROOM' 'S' 1645
Room 2 'TYK 217' 'ROOM' 'S' 1755; Room 2 'TYK 219' 'ROOM' 'S' 1865
Room 2 'TYK 216' 'ROOM' 'E' 1645; Room 2 'TYK 218' 'ROOM' 'E' 1755; Room 2 'TYK 220' 'ROOM' 'E' 1865

# 3F (plan "3F TYK BUILDING, COMPUTER LAB 3B"; College of Arts and Sciences offices)
WingRooms 3 300
Room 3 'Student Council Office' 'OFFICE' 'N' 795
Room 3 "Department Chairs' Office (CAS)" 'OFFICE' 'N' 895
Room 3 "College Secretary's Office (CAS)" 'OFFICE' 'N' 1000
Room 3 "Associate Dean's Office (CAS)" 'OFFICE' 'N' 1120
Room 3 "Dean's Office (College of Arts and Sciences)" 'OFFICE' 'L' 1215
Room 3 'Computer Lab 3A' 'LAB' 'S' 860; Room 3 'Computer Lab 3B' 'LAB' 'S' 1010
ComfortRooms 3 'TYK 3F'
Room 3 'Multimedia Room 3A' 'ROOM' 'S' 1430; Room 3 'Multimedia Room 3B' 'ROOM' 'S' 1537
Room 3 'TYK 307' 'ROOM' 'S' 1645; Room 3 'TYK 309' 'ROOM' 'S' 1755; Room 3 'TYK 311' 'ROOM' 'S' 1865
Room 3 'TYK 306' 'ROOM' 'E' 1645; Room 3 'TYK 308' 'ROOM' 'E' 1755; Room 3 'TYK 310' 'ROOM' 'E' 1865

# 4F (plan "4F TYK BUILDING, TYK 409")
WingRooms 4 400
Room 4 'TYK 406' 'ROOM' 'N' 795; Room 4 'TYK 407' 'ROOM' 'N' 895
Room 4 'CAS Faculty Room (TYK 4F)' 'OFFICE' 'N' 1050
Room 4 'Computer Lab 4A' 'LAB' 'S' 860; Room 4 'Computer Lab 4B' 'LAB' 'S' 1010
ComfortRooms 4 'TYK 4F'
Room 4 'TYK 408' 'ROOM' 'S' 1430; Room 4 'TYK 409' 'ROOM' 'S' 1537; Room 4 'TYK 411' 'ROOM' 'S' 1645
Room 4 'TYK 413' 'ROOM' 'S' 1755; Room 4 'TYK 415' 'ROOM' 'S' 1865
Room 4 'TYK 410' 'ROOM' 'E' 1645; Room 4 'TYK 412' 'ROOM' 'E' 1755; Room 4 'TYK 414' 'ROOM' 'E' 1865

# 5F (plan "5F TYK BUILDING, TYK 500"; College of Fine Arts offices; both wings are roof decks)
Room 5 'CFA Faculty Room' 'OFFICE' 'N' 795; Room 5 'TYK 500' 'ROOM' 'N' 895; Room 5 'Dark Room' 'ROOM' 'N' 955
Room 5 "College Secretary's Office (CFA)" 'OFFICE' 'N' 1010
Room 5 "Associate Dean's Office (CFA)" 'OFFICE' 'N' 1110
Room 5 "Dean's Office (College of Fine Arts)" 'OFFICE' 'L' 1215
Room 5 'TYK 501' 'ROOM' 'S' 830; Room 5 'TYK 502' 'ROOM' 'S' 935; Room 5 'TYK 503' 'ROOM' 'S' 1040
ComfortRooms 5 'TYK 5F'
Room 5 'TYK 504' 'ROOM' 'S' 1430; Room 5 'TYK 505' 'ROOM' 'S' 1537

# 6F (plans "6F TYK BUILDING LIBRARY" and "WORKSHOP")
Room 6 'Hall of Fame Gallery' 'FACILITY' 'WN' 50; Room 6 'Main Gallery' 'FACILITY' 'WS' 60
Room 6 'Filipiniana Section (Library)' 'ROOM' 'C' 820; Room 6 'Reference Section (Library)' 'ROOM' 'C' 950
Room 6 'Multimedia Room (Library)' 'ROOM' 'N' 1000; Room 6 'General Reading Area (Library)' 'ROOM' 'N' 1150
Room 6 'Circulation Section (Library)' 'ROOM' 'S' 1110
Room 6 'Benjamin C. Chua Jr. Library' 'LIBRARY' 'C' 1280
Room 6 'CR (TYK 6F)' 'RESTROOM' 'S' 1530
Room 6 'Architectural Materials Sample Room' 'ROOM' 'S' 1630; Room 6 'Studio (CFA)' 'ROOM' 'S' 1755
Room 6 'Video Editing Room' 'ROOM' 'S' 1845
Room 6 'Art and Design Production Area' 'ROOM' 'E' 1660; Room 6 'Tool Room (CFA)' 'ROOM' 'E' 1780
Room 6 'Workshop (CFA)' 'ROOM' 'E' 1870

# 7F (plan "TYK 702")
WingRooms 7 700
Room 7 'Library Reading Area (TYK 7F)' 'LIBRARY' 'C' 950
Room 7 'Accreditation Room (TYK 7F)' 'ROOM' 'L' 1185
ComfortRooms 7 'TYK 7F'
Room 7 'TYK 706' 'ROOM' 'S' 1430; Room 7 'TYK 707' 'ROOM' 'S' 1537; Room 7 'TYK 709' 'ROOM' 'S' 1645
Room 7 'TYK 711' 'ROOM' 'S' 1755; Room 7 'TYK 713' 'ROOM' 'S' 1865
Room 7 'TYK 708' 'ROOM' 'E' 1645; Room 7 'TYK 710' 'ROOM' 'E' 1755; Room 7 'TYK 712' 'ROOM' 'E' 1865

# 8F and 9F (plans "TYK 803" and "TYK 910")
foreach ($n in 8, 9) {
  $h = $n * 100
  WingRooms $n $h
  Room $n "TYK $($h + 6)" 'ROOM' 'N' 795; Room $n "TYK $($h + 8)" 'ROOM' 'N' 895; Room $n "TYK $($h + 10)" 'ROOM' 'N' 1000
  Room $n "Faculty Room (TYK $($n)F)" 'OFFICE' 'L' 1170
  Room $n "TYK $($h + 7)" 'ROOM' 'S' 830; Room $n "TYK $($h + 9)" 'ROOM' 'S' 935; Room $n "TYK $($h + 11)" 'ROOM' 'S' 1040
  ComfortRooms $n "TYK $($n)F"
  Room $n "TYK $($h + 12)" 'ROOM' 'S' 1430; Room $n "TYK $($h + 13)" 'ROOM' 'S' 1537; Room $n "TYK $($h + 15)" 'ROOM' 'S' 1645
  Room $n "TYK $($h + 17)" 'ROOM' 'S' 1755; Room $n "TYK $($h + 19)" 'ROOM' 'S' 1865
  Room $n "TYK $($h + 14)" 'ROOM' 'E' 1645; Room $n "TYK $($h + 16)" 'ROOM' 'E' 1755; Room $n "TYK $($h + 18)" 'ROOM' 'E' 1865
}

# 10F (plan "TYK 1014"; the west wing is "future academic classrooms")
Room 10 'TYK 1000' 'ROOM' 'N' 795; Room 10 'TYK 1002' 'ROOM' 'N' 895; Room 10 'TYK 1004' 'ROOM' 'N' 1000
Room 10 'TYK 1006' 'ROOM' 'N' 1120; Room 10 'Study Area (TYK 10F)' 'ROOM' 'L' 1215
Room 10 'TYK 1001' 'ROOM' 'S' 830; Room 10 'TYK 1003' 'ROOM' 'S' 935; Room 10 'TYK 1005' 'ROOM' 'S' 1040
ComfortRooms 10 'TYK 10F'
Room 10 'TYK 1007' 'ROOM' 'S' 1430; Room 10 'TYK 1008' 'ROOM' 'S' 1537; Room 10 'TYK 1009' 'ROOM' 'S' 1645
Room 10 'TYK 1011' 'ROOM' 'S' 1755; Room 10 'TYK 1013' 'ROOM' 'S' 1865
Room 10 'TYK 1010' 'ROOM' 'E' 1645; Room 10 'TYK 1012' 'ROOM' 'E' 1755; Room 10 'TYK 1014' 'ROOM' 'E' 1865

# ------------------------------------------------------------------ geometry
$NorthOffset = @{ N = 34.0; S = -25.0; C = 8.0; L = 25.0; E = 34.0 }
function BarPoint([double]$px, [double]$t) {
  $along = ($px - $PxRef) * $UnitsPerPx
  return @(($BarX + $along * $AlongX + $t * $NorthX), ($BarY + $along * $AlongY + $t * $NorthY))
}
function WingPoint([double]$a, [double]$b) { return @(($WingX0 + $a * $WaX + $b * $WbX), ($WingY0 + $a * $WaY + $b * $WbY)) }

$nodes = New-Object System.Collections.ArrayList
$edges = New-Object System.Collections.ArrayList
$script:nextNode = 1000
$script:nextEdge = 1000

function Add-Node($floorId, $name, $type, $pt, $desc = $null, $building = 3) {
  $n = [pscustomobject]@{ id = $script:nextNode; building = $building; floor = $floorId; name = $name; type = $type; desc = $desc
    fx = [double]$pt[0]; fy = [double]$pt[1]; x = [int][math]::Round($pt[0]); y = [int][math]::Round($pt[1]) }
  $script:nextNode++
  [void]$nodes.Add($n); return $n
}
function Metres($ax, $ay, $bx, $by) {
  $dx = ($ax - $bx) * $MetresPerUnitX; $dy = ($ay - $by) * $MetresPerUnitY
  return [math]::Max(1.0, [math]::Round([math]::Sqrt($dx * $dx + $dy * $dy), 1))
}
function Add-Edge($a, $b, $type = 'WALK', $m = $null) {
  if ($null -eq $m) { $m = Metres $a.fx $a.fy $b.fx $b.fy }
  $acc = if ($type -eq 'STAIRS') { 'FALSE' } else { 'TRUE' }
  [void]$edges.Add([pscustomobject]@{ id = $script:nextEdge; a = $a.id; b = $b.id; m = [double]$m; type = $type; acc = $acc })
  $script:nextEdge++
}
function Nearest($list, $pt) {
  $best = $null; $bd = [double]::MaxValue
  foreach ($n in $list) { $d = ($n.fx - $pt[0]) * ($n.fx - $pt[0]) + ($n.fy - $pt[1]) * ($n.fy - $pt[1]); if ($d -lt $bd) { $bd = $d; $best = $n } }
  return $best
}

$vertical = @{}   # "key|floor" -> stairs or elevator node
foreach ($f in $Floors) {
  $tag = $f.tag; $fid = $f.id
  $desc = $null
  $list = if ($Rooms.ContainsKey($f.num)) { @($Rooms[$f.num]) } else { @() }

  # main hallway: one junction at every room, from the west lobby to the east end
  $pxs = New-Object System.Collections.ArrayList
  foreach ($p in 720, 1395) { [void]$pxs.Add([double]$p) }
  if ($f.east) { foreach ($p in 1590, 1935) { [void]$pxs.Add([double]$p) } }
  if ($f.num -eq 1) { [void]$pxs.Add(1300.0) }
  foreach ($r in $list) { if ('N', 'S', 'C', 'L' -contains $r.where) { [void]$pxs.Add([math]::Round($r.pos)) } }
  $hallPx = @($pxs | Sort-Object -Unique)
  $hall = @()
  foreach ($p in $hallPx) {
    $m = [math]::Round(($p - 720) * $UnitsPerPx * 0.243)
    $hall += Add-Node $fid "$tag hallway $m m" 'JUNCTION' (BarPoint $p 0) $desc
  }
  for ($i = 1; $i -lt $hall.Count; $i++) { Add-Edge $hall[$i - 1] $hall[$i] }
  $hallAt = @{}; for ($i = 0; $i -lt $hall.Count; $i++) { $hallAt[[double]$hallPx[$i]] = $hall[$i] }

  # east block: a second hallway on the north side of the open well, joined at both ends
  $north = @()
  if ($f.east) {
    $npx = @(1590.0) + @($list | Where-Object { $_.where -eq 'E' } | ForEach-Object { $_.pos }) + @(1935.0)
    foreach ($p in $npx) { $north += Add-Node $fid "$tag east hallway $([math]::Round(($p - 720) * $UnitsPerPx * 0.243)) m" 'JUNCTION' (BarPoint $p 14) $desc }
    for ($i = 1; $i -lt $north.Count; $i++) { Add-Edge $north[$i - 1] $north[$i] }
    Add-Edge $north[0] $hallAt[1590.0]; Add-Edge $north[$north.Count - 1] $hallAt[1935.0]
  }

  # central lobby: stair 1 and the central elevators
  $lobby = Add-Node $fid "$tag lobby" 'JUNCTION' (BarPoint 1395 14) $desc
  Add-Edge $lobby $hallAt[1395.0]
  $s1 = Add-Node $fid "TYK stair 1 ($($tag.Substring(4)))" 'STAIRS' (BarPoint 1395 40); Add-Edge $s1 $lobby
  $e1 = Add-Node $fid "TYK central elevators ($($tag.Substring(4)))" 'ELEVATOR' (BarPoint 1480 30); Add-Edge $e1 $lobby
  $vertical["stair 1|$($f.num)"] = $s1; $vertical["central elevators|$($f.num)"] = $e1

  # west lobby: stair 3, the west elevators and the way into the wing
  $west = Add-Node $fid "$tag west lobby" 'JUNCTION' (BarPoint 720 -14) $desc
  Add-Edge $west $hallAt[720.0]
  $s3 = Add-Node $fid "TYK stair 3 ($($tag.Substring(4)))" 'STAIRS' (BarPoint 745 -26); Add-Edge $s3 $west
  $e2 = Add-Node $fid "TYK west elevators ($($tag.Substring(4)))" 'ELEVATOR' (WingPoint 112 34); Add-Edge $e2 $west
  $vertical["stair 3|$($f.num)"] = $s3; $vertical["west elevators|$($f.num)"] = $e2

  # stair 2 (east end) and stair 4 (wing tip): on 5F they only pass the roof decks
  $s2 = Add-Node $fid "TYK stair 2 ($($tag.Substring(4)))" 'STAIRS' (BarPoint 1950 7)
  if ($f.east) { Add-Edge $s2 $hallAt[1935.0]; Add-Edge $s2 $north[$north.Count - 1] }
  $s4 = Add-Node $fid "TYK stair 4 ($($tag.Substring(4)))" 'STAIRS' (WingPoint -8 10)
  $vertical["stair 2|$($f.num)"] = $s2; $vertical["stair 4|$($f.num)"] = $s4

  # west wing: two hallways around the open well, joined at both ends
  $wingNE = @(); $wingSW = @()
  if ($f.wing -ne 'none') {
    $nwEnd = Add-Node $fid "$tag wing hallway (north-west end)" 'JUNCTION' (WingPoint 4 0) $desc
    $seEnd = Add-Node $fid "$tag wing hallway (lobby end)" 'JUNCTION' (WingPoint 115 0) $desc
    foreach ($a in 10, 26, 60, 94, 112) { $wingNE += Add-Node $fid "$tag wing hallway, north-east side $a" 'JUNCTION' (WingPoint $a 10) $desc }
    foreach ($a in 10, 22, 56, 90, 112) { $wingSW += Add-Node $fid "$tag wing hallway, south-west side $a" 'JUNCTION' (WingPoint $a -10) $desc }
    Add-Edge $nwEnd $wingNE[0]; Add-Edge $nwEnd $wingSW[0]
    for ($i = 1; $i -lt 5; $i++) { Add-Edge $wingNE[$i - 1] $wingNE[$i]; Add-Edge $wingSW[$i - 1] $wingSW[$i] }
    Add-Edge $wingNE[4] $seEnd; Add-Edge $wingSW[4] $seEnd
    Add-Edge $seEnd $west
    Add-Edge $s4 $nwEnd
    $vertical["wing door|$($f.num)"] = $nwEnd
  }

  # rooms
  foreach ($r in $list) {
    if ($r.where -eq 'WN') {
      $pt = WingPoint $r.pos 30; $room = Add-Node $fid $r.name $r.type $pt $desc; Add-Edge $room (Nearest $wingNE $pt)
    } elseif ($r.where -eq 'WS') {
      $pt = WingPoint $r.pos -30; $room = Add-Node $fid $r.name $r.type $pt $desc; Add-Edge $room (Nearest $wingSW $pt)
    } elseif ($r.where -eq 'E') {
      $pt = BarPoint $r.pos $NorthOffset['E']; $room = Add-Node $fid $r.name $r.type $pt $desc; Add-Edge $room (Nearest $north $pt)
    } else {
      $pt = BarPoint $r.pos $NorthOffset[$r.where]; $room = Add-Node $fid $r.name $r.type $pt $desc
      Add-Edge $room $hallAt[[double][math]::Round($r.pos)]
    }
  }
  if ($f.num -eq 1) { $script:groundEntrance = $hallAt[1300.0] }
  if ($f.num -eq 4) { $script:bridgeHall = $hallAt[1935.0] }
}
foreach ($n in $nodes) {
  if ($n.name -eq 'TYK Canteen') { $n.desc = 'In the middle of the ground floor. Enter from the grass field or through the CFAD wing.' }
}

# the 4th floor bridge to the Engineering building's 3rd floor (EN 3F, floor id 4, ends at its south wing hallway)
$enSql = Get-Content -Raw (Join-Path $PSScriptRoot '..\04_engineering_building.sql')
$enMatch = [regex]::Match($enSql, "\((\d+), 1, 4, 'EN 3F south wing hallway -8', 'JUNCTION', (\d+), (\d+)")
if (-not $enMatch.Success) { throw 'EN 3F south wing hallway -8 is missing from 04_engineering_building.sql' }
$enHall = [pscustomobject]@{ id = [int]$enMatch.Groups[1].Value; fx = [double]$enMatch.Groups[2].Value; fy = [double]$enMatch.Groups[3].Value }
$bridgeTyk = Add-Node 8 'Bridge to Engineering (TYK 4F)' 'JUNCTION' (BarPoint 1975 -4)
$bridgeEn = Add-Node 4 'Bridge to Tan Yan Kee (EN 3F)' 'JUNCTION' @(1290.0, 560.0) $null 1
Add-Edge $bridgeTyk $script:bridgeHall
Add-Edge $bridgeTyk $bridgeEn 'WALK' $BridgeM
Add-Edge $bridgeEn $enHall

# stairs and elevators between floors (stairs: not step-free)
foreach ($key in 'stair 1', 'stair 2', 'stair 3', 'stair 4') {
  for ($n = 1; $n -lt 10; $n++) { Add-Edge $vertical["$key|$n"] $vertical["$key|$($n + 1)"] 'STAIRS' $FlightM }
}
foreach ($key in 'central elevators', 'west elevators') {
  for ($n = 1; $n -lt 10; $n++) { Add-Edge $vertical["$key|$n"] $vertical["$key|$($n + 1)"] 'ELEVATOR' $LiftM }
}

# the doors: main door (33) under the 2F canopy on the field side, the CFAD wing door (34) at the wing's tip
$MainDoor = @(891.0, 661.0)
$WingDoor = @(598.0, 672.0)
$door33 = [pscustomobject]@{ id = 33; fx = $MainDoor[0]; fy = $MainDoor[1] }
$door34 = [pscustomobject]@{ id = 34; fx = $WingDoor[0]; fy = $WingDoor[1] }
Add-Edge $door33 $script:groundEntrance
Add-Edge $door34 $vertical["wing door|1"]
$walk15to33 = Metres 850 620 $MainDoor[0] $MainDoor[1]

# ------------------------------------------------------------------ SQL
function Q($s) { if ($null -eq $s) { 'NULL' } else { "'" + ($s -replace "'", "''") + "'" } }
function Num($d) { ([double]$d).ToString([Globalization.CultureInfo]::InvariantCulture) }
$sb = New-Object System.Text.StringBuilder
function L($s) { [void]$sb.AppendLine($s) }
L '-- Tan Yan Kee Academic Building (TYK): ground floor (canteen) to 10th floor, rooms, offices, comfort rooms, the'
L '-- library, four stairwells, two elevator banks and the 4F bridge to Engineering 3F. "CFAD" is its west wing.'
L '-- GENERATED by sql/tools/tan-yan-kee.ps1 from the posted floor plans and the building directory. Edit the script and'
L '-- run it again rather than editing this file. Positions are estimates.'
L '-- Safe to run more than once; keeps the usage log. Run after 05_campus_layout.sql (and again whenever 04 or 05 is'
L '-- run: 04 removes the bridge end on Engineering 3F).'
L '-- Then copy GET /api/snapshot into uenav-mobile/src/main/resources/campus-snapshot.json before building the APK.'
L ''
L 'USE uenav;'
L ''
L '-- the CFAD building is the west wing of Tan Yan Kee (owner, 2026-11-15): its door joins TYK, building 7 goes'
L "UPDATE node SET building_id = 3, name = 'CFAD Building (Tan Yan Kee west wing)' WHERE node_id = 34;"
L 'DELETE FROM building WHERE building_id = 7 AND NOT EXISTS (SELECT 1 FROM node WHERE node.building_id = 7)'
L '  AND NOT EXISTS (SELECT 1 FROM floor WHERE floor.building_id = 7);'
L "UPDATE building SET map_x = 850, map_y = 705, description = 'Ten floors. The CFAD wing is its angled west end. Canteen on the ground floor; a bridge on the 4th floor leads to the Engineering building (3rd floor).' WHERE building_id = 3;"
L '-- main door under the 2F canopy, on the field side'
L "UPDATE node SET x = $([int]$MainDoor[0]), y = $([int]$MainDoor[1]) WHERE node_id = 33;"
L "UPDATE edge SET distance_m = $(Num $walk15to33) WHERE (from_node_id = 15 AND to_node_id = 33) OR (from_node_id = 33 AND to_node_id = 15);"
L ''
L 'INSERT INTO floor (floor_id, building_id, floor_number, floor_name) VALUES'
L ((($Floors | ForEach-Object { "  ($($_.id), 3, $($_.num), '$($_.name)')" }) -join ",`n"))
L 'ON DUPLICATE KEY UPDATE building_id = VALUES(building_id), floor_number = VALUES(floor_number), floor_name = VALUES(floor_name);'
L ''
L '-- every walkway inside the building (and its links to the doors) is rebuilt below'
L 'DELETE FROM edge WHERE from_node_id IN (SELECT node_id FROM (SELECT node_id FROM node WHERE building_id = 3 AND floor_id IS NOT NULL) b)'
L '                  OR to_node_id IN (SELECT node_id FROM (SELECT node_id FROM node WHERE building_id = 3 AND floor_id IS NOT NULL) b);'
$ids = ($nodes | ForEach-Object { $_.id }) -join ', '
L '-- rows from an older run of this script that are no longer made'
L "DELETE FROM node WHERE building_id = 3 AND floor_id IS NOT NULL AND node_id NOT IN ($ids)"
L '  AND node_id NOT IN (SELECT from_node_id FROM usage_log) AND node_id NOT IN (SELECT to_node_id FROM usage_log);'
L ''
L '-- an earlier run marked the ground floor as estimated; the owner described it since'
L "UPDATE node SET description = NULL WHERE description = 'Position estimated: there is no ground floor plan yet.';"
L 'INSERT INTO node (node_id, building_id, floor_id, name, node_type, x, y, description) VALUES'
L ((($nodes | ForEach-Object { "  ($($_.id), $($_.building), $($_.floor), $(Q $_.name), '$($_.type)', $($_.x), $($_.y), $(Q $_.desc))" }) -join ",`n"))
L 'ON DUPLICATE KEY UPDATE building_id = VALUES(building_id), floor_id = VALUES(floor_id), name = VALUES(name),'
L '  node_type = VALUES(node_type), x = VALUES(x), y = VALUES(y),'
L '  description = IFNULL(node.description, VALUES(description));'
L ''
L 'INSERT INTO edge (edge_id, from_node_id, to_node_id, distance_m, edge_type, is_accessible) VALUES'
L ((($edges | ForEach-Object { "  ($($_.id), $($_.a), $($_.b), $(Num $_.m), '$($_.type)', $($_.acc))" }) -join ",`n"))
L 'ON DUPLICATE KEY UPDATE from_node_id = VALUES(from_node_id), to_node_id = VALUES(to_node_id),'
L '  distance_m = VALUES(distance_m), edge_type = VALUES(edge_type), is_accessible = VALUES(is_accessible);'

$out = Join-Path $PSScriptRoot '..\06_tan_yan_kee.sql'
[IO.File]::WriteAllText([IO.Path]::GetFullPath($out), $sb.ToString(), (New-Object System.Text.UTF8Encoding($false)))
$searchable = @($nodes | Where-Object { 'JUNCTION', 'STAIRS', 'ELEVATOR' -notcontains $_.type }).Count
Write-Host ("Wrote {0}: {1} places ({2} searchable), {3} walkways" -f (Resolve-Path $out), $nodes.Count, $searchable, $edges.Count)
