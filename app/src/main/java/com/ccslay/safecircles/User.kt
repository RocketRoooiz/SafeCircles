package com.ccslay.safecircles

data class User(
    var email: String = "",
    var phoneNumber: String = "",
    var name: String = "",
    var currentLocation: String = "",
    //var homeCircle: LocationCircles,
  //  var savedCircles: ArrayList<LocationCircles>,
    var friends: ArrayList<User>
)

