package com.project.blue_command.model

enum class TacticalCommand(val code: Int, val label: String) {
    HURRY_UP(1, "Hurry Up"),
    STOP(2, "Stop"),
    FREEZE(3, "Freeze"),
    COVER_THIS_AREA(4, "Cover This Area"),
    ENEMY(5, "Enemy"),
    SNIPER(6, "Sniper"),
    PISTOL(7, "Pistol"),
    RIFLE(8, "Rifle"),
    SHOTGUN(9, "Shotgun"),
    VEHICLE(10, "Vehicle"),
    DOOR(11, "Door"),
    WINDOW(12, "Window")
}