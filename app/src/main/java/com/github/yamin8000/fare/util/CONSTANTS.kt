/*
 *     CONSTANTS.kt Created by Yamin Siahmargooei at 2021/7/1
 *     Fare: find Iran's cities taxi fares
 *     This file is part of Fare.
 *     Copyright (C) 2021  Yamin Siahmargooei
 *
 *     Fare is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Fare is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Fare.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.yamin8000.fare.util

object CONSTANTS {
    
    //general
    const val LOG_TAG = "<--> "
    const val LICENSE = "license"
    //const val STATE = "state"
    const val DATE = "date"
    lateinit var APP_PACKAGE : String
    //shared preferences names
    lateinit var LICENSE_PREFS : String
    lateinit var FEEDBACK_PREFS : String
    //lateinit var STATE_PREFS : String
    //params
    const val STATE_ID = "state_id"
    const val COUNTY_ID = "county_id"
    const val CITY_ID = "city_id"
    const val ORIGIN = "origin"
    const val DESTINATION = "destination"
    const val LINE_CODE = "code"
    const val FEEDBACK = "feedback"
}