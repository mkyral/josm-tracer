<?php
require("config.php");
$lat=$_REQUEST['lat'];
$lon=$_REQUEST['lon'];
if ( !is_numeric($lat) or !is_numeric($lon) ) die;
header('Content-Type: application/json');

$data = array();

$data["coordinates"] = array( "lat" => "$lat", "lon" => "$lon");
$data["source"] = "cuzk:ruian";

$query="
  select keys,
  st_asgeojson(st_transform(local_simplify_polygon((st_dump(hranice)).geom),4326)) as geom
  from ruian.landuse_view l
  where st_contains(l.hranice,st_transform(st_geomfromtext('POINT(".$lon." ".$lat.")',4326),900913))
  limit 1";

$result=pg_query($CONNECT,$query);
$error= pg_last_error($CONNECT);
if (pg_num_rows($result) > 0)
{
  $data["keys"] = pg_result($result,0,"keys");
  $data["geometry"] = json_decode(pg_result($result,0,"geom"),true)['coordinates'][0];
}
  else
{
  $data["geometry"] = array();
  $data["keys"] = array();
}

echo json_encode($data);

?>
