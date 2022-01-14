/*
 *     Line.kt Created by Yamin Siahmargooei at 2021/7/14
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

package com.github.yamin8000.fare.model

import com.github.yamin8000.fare.util.CONSTANTS.CITY_ID
import com.google.gson.annotations.SerializedName

data class Line(
    val id: String,
    val code: String?,
    val origin: String?,
    val destination: String?,
    @SerializedName("has_custom_property_name") val hasCustomProperty: Boolean,
    @SerializedName(CITY_ID) val cityId: Int?,
    val price: List<Price>?
)