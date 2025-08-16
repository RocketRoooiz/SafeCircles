package com.ccslay.safecircles

import com.ccslay.safecircles.zone.LocationCircle

data class User(
    var email: String = "",
    var phoneNumber: String = "",
    var name: String = "",
    var homeCircle: LocationCircle,
    var savedCircles: ArrayList<LocationCircle>,
    var friends: ArrayList<User>
)

