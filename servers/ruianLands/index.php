<?php
require("config.php");
$lat=$_REQUEST['lat'];
$lon=$_REQUEST['lon'];
if ( !is_numeric($lat) or !is_numeric($lon) ) die;
header('Content-Type: application/json');

$data = array();

$data["coordinates"] = array( "lat" => "$lat", "lon" => "$lon");
$data["source"] = "cuzk:ruian";

// land
$query="
  select s.id, a.nazev as druh_pozemku, b.nazev as zpusob_vyuziti, s.plati_od
  from rn_parcela s
      left outer join osmtables.druh_pozemku a on s.druh_pozemku_kod = a.kod
      left outer join osmtables.zpusob_vyuziti_pozemku b on s.zpusob_vyu_poz_kod = b.kod
  where st_contains(s.hranice,st_transform(st_geomfromtext('POINT(".$lon." ".$lat.")',4326),900913))
  and not s.deleted
  limit 1;
";

$result=pg_query($CONNECT,$query);
$error= pg_last_error($CONNECT);
if (pg_num_rows($result) > 0)
{
  $row = pg_fetch_array($result, 0);

  $data["parcela"] =
    array( "ruian_id" => $row["id"],
           "druh_pozemku" => $row["druh_pozemku"],
           "zpusob_vyuziti" => $row["zpusob_vyuziti"],
           "plati_od" => $row["plati_od"]
         );

  // -----------------
  // Land geometry
  $query="
    select st_asgeojson(st_transform(local_simplify_polygon(xhranice),4326)) as geom
    from
      (select id,(st_dump(hranice)).geom as xhranice
      from rn_parcela
      where id = ".$row["id"]."
        and not deleted) as foo
    limit 1
  ";
  $result = pg_query($CONNECT,$query);

  if (pg_num_rows($result) > 0)
  {
    $geom = pg_result($result,0,"geom");
    $geometry=json_decode($geom,true);
    $data["geometry"] = $geometry['coordinates'][0];
  }
  else
  {
    $data["geometry"] = array();
  }
} else
{
  $data["parcela"] = array();
  $data["geometry"] = array();
}

echo json_encode($data);

?>
