package com.philips.btserver.userdataservice

data class UserData(
    val userIndex: Int,
    var firstName: String = "First",
    var lastName: String = "Last",
    var version: Int = 0
)
