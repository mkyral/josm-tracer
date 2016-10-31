<?php
require("config.php");
$lat=$_REQUEST['lat'];
$lon=$_REQUEST['lon'];
$req=$_REQUEST['req'];

if ( !is_numeric($lat) or !is_numeric($lon) ) die;
header('Content-Type: application/json');

$data = array();

$data["coordinates"] = array( "lat" => "$lat", "lon" => "$lon");
$data["source"] = "cuzk:ruian";


// building
$query="
  select s.kod,
        CASE
          WHEN s.typ_kod = 1 THEN 'Číslo popisné'
          WHEN s.typ_kod = 2 THEN 'Číslo evidenční'
          WHEN s.typ_kod = 3 THEN 'bez č.p./č.e.'
          ELSE ''
        END cislo_typ,
        trim(both '{}' from s.cisla_domovni::text) cisla_domovni,
        am.cislo_orientacni_hodnota || coalesce(am.cislo_orientacni_pismeno, '') cislo_orientacni,
        am.kod as adresni_misto_kod,
        s.pocet_podlazi, a.nazev, s.plati_od, s.pocet_bytu, s.dokonceni,
        am.adrp_psc psc, ul.nazev ulice, c.nazev cast_obce,
        momc.nazev mestska_cast,
        ob.nazev obec, ok.nazev okres, vu.nazev kraj,
        s.zpusob_vyuziti_kod, a.osmtag_k, a.osmtag_v
  from rn_stavebni_objekt s
      left outer join osmtables.zpusob_vyuziti_objektu a on s.zpusob_vyuziti_kod = a.kod
      left outer join rn_adresni_misto am on am.stavobj_kod = s.kod and not am.deleted
      left outer join rn_ulice ul on am.ulice_kod = ul.kod and not ul.deleted
      left outer join rn_cast_obce c on c.kod = s.cobce_kod and not c.deleted
      left outer join rn_momc momc on momc.kod = s.momc_kod and not momc.deleted
      left outer join rn_obec ob on coalesce(ul.obec_kod, c.obec_kod)  = ob.kod and not ob.deleted
      left outer join rn_okres ok on ob.okres_kod = ok.kod and not ok.deleted
      left outer join rn_vusc vu on ok.vusc_kod = vu.kod and not vu.deleted
  where st_contains(s.hranice,st_transform(st_geomfromtext('POINT(".$lon." ".$lat.")',4326),900913))
  and not s.deleted
  limit 1;
  ";
$result = pg_query($CONNECT,$query);

if (pg_num_rows($result) > 0)
{
  $row = pg_fetch_array($result, 0);


  $data["stavebni_objekt"] =
    array( "ruian_id" => $row["kod"],
            "cislo_domovni" => $row["cisla_domovni"],
            "cislo_domovni_typ" => $row["cislo_typ"],
            "cislo_orientacni" => $row["cislo_orientacni"],
            "adresni_misto_kod" => $row["adresni_misto_kod"],
            "ulice" => $row["ulice"],
            "cast_obce" => $row["cast_obce"],
            "mestska_cast" => $row["mestska_cast"],
            "obec" => $row["obec"],
            "okres" => $row["okres"],
            "kraj" => $row["kraj"],
            "psc" => $row["psc"],
            "pocet_podlazi" => $row["pocet_podlazi"],
            "zpusob_vyuziti_kod" => $row["zpusob_vyuziti_kod"],
            "zpusob_vyuziti_key" => $row["osmtag_k"],
            "zpusob_vyuziti_val" => $row["osmtag_v"],
            "pocet_bytu" => $row["pocet_bytu"],
            "dokonceni" => $row["dokonceni"],
            "plati_od" => $row["plati_od"]
            );

  // -----------------
  // Building geometry
  if ( "$req" == "full" )
  { // export complete building including inners
    $query="
      select st_asgeojson(st_transform(local_less_simplify_polygon((st_dumprings(xhranice)).geom),4326)) as geom
      from
        (select kod,(st_dump(hranice)).geom as xhranice
        from rn_stavebni_objekt
        where st_contains(hranice,st_transform(st_geomfromtext('POINT(".$lon." ".$lat.")',4326),900913))
          and not deleted) as foo
      where st_contains(xhranice,st_transform(st_geomfromtext('POINT(".$lon." ".$lat.")',4326),900913))
    ";
  } else
  { // Export only outer way
    $query="
      select st_asgeojson(st_transform(local_simplify_polygon(xhranice),4326)) as geom
      from
        (select kod,(st_dump(hranice)).geom as xhranice
        from rn_stavebni_objekt
        where st_contains(hranice,st_transform(st_geomfromtext('POINT(".$lon." ".$lat.")',4326),900913))
          and not deleted) as foo
      where st_contains(xhranice,st_transform(st_geomfromtext('POINT(".$lon." ".$lat.")',4326),900913))
      limit 1
    ";
  }
  $result = pg_query($CONNECT,$query);

  if (pg_num_rows($result) == 1 && "$req" != "full")
  {

    $geom = pg_result($result,0,"geom");
    $geometry=json_decode($geom,true);
    $data["geometry"] = $geometry['coordinates'][0];
  }
  else if (pg_num_rows($result) > 1 || "$req" == "full")
  {
    $geom = array();
    $inners = array();
    for ($i = 0; $i < pg_num_rows($result); $i++)
    {
      $row = pg_result($result,$i,"geom");
      $geometry=json_decode($row,true);
      if ($i == 0)
      {
        $geom['outer'] =$geometry['coordinates'][0];
      } else if ($geometry['coordinates'][0][0] == $geom['outer'][0])
      {
        // Ignore dulicated outer ways on the same position
        break;
      } else
      {
        array_push($inners, $geometry['coordinates'][0]);
      }
    }
      $geom['inners'] = $inners;
      $data["geometry"] = $geom;
  } else
  {
    $data["geometry"] = array();
  }
  // -----------------
  // Addresses
  $query="
    select am.kod,
          am.cislo_domovni,
          am.cislo_orientacni_hodnota || coalesce(am.cislo_orientacni_pismeno, '') cislo_orientacni,
          ul.nazev ulice
    from rn_adresni_misto am
        left outer join rn_ulice ul on am.ulice_kod = ul.kod
    where am.stavobj_kod = ".$data["stavebni_objekt"]["ruian_id"]."
    and not am.deleted
    order by st_distance( (st_transform(am.definicni_bod,4326))::geography,
                          (st_setsrid(st_makepoint(".$lon.",".$lat."),4326))::geography)
  ";

  $result=pg_query($CONNECT,$query);
  $error= pg_last_error($CONNECT);
  if (pg_num_rows($result) > 1)
  {
    $am = array();
    for ($i = 0; $i < pg_num_rows($result); $i++)
    {
      $row = pg_fetch_array($result, $i);
      array_push($am,
                  array("ruian_id" => $row["kod"],
                        "cislo_domovni" => $row["cislo_domovni"],
                        "cislo_orientacni" => $row["cislo_orientacni"],
                        "ulice" => $row["ulice"]));
    }
      $data["adresni_mista"] = $am;
  }
  else
  {
  //   echo "error: $error\n";
    $data["adresni_mista"] = array();
  }

} else
{
  $data["stavebni_objekt"] = array();
  $data["geometry"] = array();
  $data["adresni_mista"] = array();
}

echo json_encode($data);

?>
