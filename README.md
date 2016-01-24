#josm-tracer

This is an updated version of the original [JOSM Tracer plugin](http://wiki.openstreetmap.org/wiki/JOSM/Plugins/Tracer) originally written by Jan Bilak, Petr Dlouhý (and based on Lakewalker from Rodney Kinney and Brent Easton).

The original plugin traces ways (buildings and other areas) from Czech cadastral map. It needs a local .Net (Mono) tracing server. Server needs to fetch a bitmap images from the Cadastral server and it took some time.

Currently, there is a new source [RUIAN](http://wiki.openstreetmap.org/wiki/RUIAN) available. It contains already digitalized buildings and parcels and even some additional data like type of building/parcel, number of building levels, flats, associated address.

**Advantages:** Quick tracing, additional attributes. No bitmap download, just small JSON text file with building geometry and its attributes.
**Disadvantage:** Does not cover whole Czech Republic (but cadastral map has not full coverage as well).


##Changes description
Tracer plugin was modularized, connecting to existing polygons was improved (thanks [Martin Švec](https://github.com/Maatts) for his big help).

Currently, following modules are available:

* Classic - the original bitmap tracer module
* RUIAN - buildings from ruian (via [poloha.net](http://www.poloha.net))
* LPIS - landuse from Czech Department of Agriculture
* RUIAN Lands - an special and experimental module for landuses from RUIAN (via [poloha.net](http://www.poloha.net))

On plugin configuration page you can activate only modules you want to use.

## Work with Tracer
Tracer tool is available in menu ```More tools/Tracer - RUIAN``` and under key ```T``` When activated, tracer will initialize last used module. By pressing key ```T``` you can switch to next enabled module. Activated module is indicated on cursor.

![](https://raw.githubusercontent.com/mkyral/josm-tracer/development/doc/img/cursor_mode_standard.png) RUIAN module is activated

The letter 'R' is used to indicate RUIAN (building) module, LP is for LPIS, RL is for RUAN - Lands and no letter means original (classic) module.

To start tracing just choose correct module and click into the feature you want to trace. You need to load appropriate tms/wms layer to see, where to click. All are available in JOSM by default.

| Name |Description|
|--|--|
| **CZ / Český RÚIAN budovy** | RUIAN (buildings)|
| **CZ / Český pLPIS** | LPIS|
| **CZ / Český RÚIAN parcely** | RUIAN Lands|
| **CZ / Český CUZK:KM** | CZ bitmap cadastral map|


In standard mode, when user clicks inside feature (e.g.: building), plugin will ask server for details of feature, create new OSM object or update existing and connect object to near features or clip them if needed.

However, this behavior can be changed via modification keys. Mouse click + Ctrl causes that new OSM object is created, but existing object is not updated and near objects are not clipped or connected to newly traced object. Pressing of modificator key changes mouse cursor.

When user clicks while shift is pressed, now object is not created, but tags on existing object are updated.

|Cursor|Mode description|
|--|--|
|![](https://raw.githubusercontent.com/mkyral/josm-tracer/development/doc/img/cursor_mode_standard.png)|Standard mode|
|![](https://raw.githubusercontent.com/mkyral/josm-tracer/development/doc/img/cursor_mode_new.png)|Only create object|
|![](https://raw.githubusercontent.com/mkyral/josm-tracer/development/doc/img/cursor_mode_paste_tags_only.png)|Only paste tags, do not update object geometry|



##TODO:
* Allows retracing of multipolygons (mainly for Lpis) module


##Screenshots
**Trace mode enabled**

![](http://www.kyralovi.cz/tmp/josm/RUIANtracer.png)

**Building traced**

![](http://www.kyralovi.cz/tmp/josm/RUIANtracer_traced.png)

**New plugin settings**

![](http://www.kyralovi.cz/tmp/josm/RUIANtracer_settings.png)