package com.ccslay.safecircles
import java.time.LocalDateTime
import org.osmdroid.util.GeoPoint

data class Reports(
    var reporter: User?,
    var location: GeoPoint?,
    var imageUrl: String?,
    var dateTime: LocalDateTime?,
    var disasterType: String?
)
