# SimpleCompass

SimpleCompass is a small plugin for [Spigot](https://www.spigotmc.org) Minecraft servers. It allows to display one (or two) compass in the action bar / boss bar area. Players can easily change their settings with a single command, then click on the wanted option(s). Options are: Use always the compass, never or in only on elytra / vehicle.

## How to install

There is no dependencies, simply drop the jar file into your plugin directory, then restart (or reload) your server. All configuration parameters are explained in this [config.yml](https://github.com/arboriginal/SimpleCompass/blob/master/src/main/resources/config.yml).

You can download the last release here: [SimpleCompass.jar](https://github.com/arboriginal/SimpleCompass/releases).

## Permissions

All permissions are listed with a short description in this [plugin.yml](https://github.com/arboriginal/SimpleCompass/blob/master/src/main/resources/plugin.yml#L41).

## Commands

* **/scompass** visual interface (clickable book)
* **/scompass-toggle** to quickly toggle on/off your compass(es)
* **/scompass-option** will show the menu to choose where and when display the compass
* **/sctrack** to tracker a position, coordinates or a player (see below)
* **/scompass-reload** will reload the configuration

You can use **/scompass-option** with arguments directly if you prefer (`/scompass-option <option> <type>`).

## Trackers addons

To use the command **sctrack**, you need at least one tracker addon. You can find them here:

* [CoordsTracker](https://github.com/arboriginal/SCT-CoordsTracker): Allows to track specific coordinates.
* [DeathPosTracker](https://github.com/arboriginal/SCT-DeathPosTracker): Allows to track the last death position.
* [PapiPositionTracker](https://github.com/arboriginal/SCT-PapiPositionTracker): Allows to track positions defined using PlaholderAPI placeholders.
* [PlayerTracker](https://github.com/arboriginal/SCT-PlayerTracker): Allows to track players positions.
* [PositionTracker](https://github.com/arboriginal/SCT-PositionTracker): Allows to track static positions.

Have a look at the [Spigot plugin thread](https://www.spigotmc.org/threads/simplecompass.351093/), maybe other addons (made by other developers) will be listed.
