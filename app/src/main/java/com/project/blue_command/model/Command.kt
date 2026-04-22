package com.project.blue_command.model
import com.project.blue_command.R

enum class TacticalCommand(val code: Int, val label: String, val iconRes: Int) {
    HURRY_UP(1, "Hurry Up", R.drawable.hurry_up),
    STOP(2, "Stop", R.drawable.stop),
    FREEZE(3, "Freeze", R.drawable.freeze),
    COVER_THIS_AREA(4, "Cover This Area", R.drawable.cover_this_area),
    ENEMY(5, "Enemy", R.drawable.enemy),
    SNIPER(6, "Sniper", R.drawable.sniper),
    PISTOL(7, "Pistol", R.drawable.pistol),
    RIFLE(8, "Rifle", R.drawable.rifle),
    SHOTGUN(9, "Shotgun", R.drawable.shotgun),
    VEHICLE(10, "Vehicle", R.drawable.vehicle),
    DOOR(11, "Door", R.drawable.door),
    WINDOW(12, "Window", R.drawable.window)
}