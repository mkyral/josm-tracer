#josm-tracer

This is an updated version of the original [JOSM Tracer plugin](http://wiki.openstreetmap.org/wiki/JOSM/Plugins/Tracer) originally written by Jan Bilak, Petr Dlouhý (and based on Lakewalker from Rodney Kinney and Brent Easton).

The original plugin traces ways (buildings and other areas) from Czech cadastral map. It needs a local .Net (Mono) tracing server. Server needs to fetch a bitmap images from the Cadastral server and it took some time.

Currently, there is a new source [RUIAN](http://wiki.openstreetmap.org/wiki/RUIAN) available. It contains already digitalized buildings and parcels and even some additional data like type of building/parcel, number of building levels, flats, associated address.

**Advantages:** Quick tracing, additional attributes. No bitmap download, just small JSON text file with building geometry and its attributes.
**Disadvantage:** Does not cover whole Czech Republic (but cadastral map has not full coverage as well).


##Changes
The original Tracer stays almost untouched (I added settings only) and could be still used in areas covered by Cadastral maps but not covered by RUIAN.

For RUIAN I've created a new mode, available in **More tools/Tracer - RUIAN** (shortcut *Ctrl+T*)

###Main changes:

* Data from RUIAN are used
* Additional information are added to the traced building (building=*, building:levels=, building:flats, start_date...)
* Configuration added - user could specify custom server URL and adjust position of the traced building if necessary.
* I like Tracer2 ability to update existing buildings to a new geometry, so I adopted improved Tracer2 ConnectWays class.
* I prevent plugin to traced already traced building again - caused duplicities
* I tried to eliminate duplicity nodes that come from garages blocks tracing (still not perfect)

##Future plans:

* Final solution for duplicity nodes
* In case that there is more JOSM building types available, let user to choose the correct one
* Implement reconnecting of previously connected buildings
* Implement solution for overlapping buildings
* Add new RUIAN mode, this time for Landuse


##Screenshots
**Trace mode enabled**

![](http://www.kyralovi.cz/tmp/josm/RUIANtracer.png)

**Building traced**

![](http://www.kyralovi.cz/tmp/josm/RUIANtracer_traced.png)

**New plugin settings**

![](http://www.kyralovi.cz/tmp/josm/RUIANtracer_settings.png)