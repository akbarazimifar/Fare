/*
 *     SearchLineFragment.kt Created by Yamin Siahmargooei at 2021/7/14
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

package com.github.yamin8000.fare.search.line

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.github.yamin8000.fare.R
import com.github.yamin8000.fare.databinding.FragmentSearchLineBinding
import com.github.yamin8000.fare.model.CityJoined
import com.github.yamin8000.fare.model.Line
import com.github.yamin8000.fare.ui.fragment.BaseFragment
import com.github.yamin8000.fare.ui.recyclerview.adapters.EmptyAdapter
import com.github.yamin8000.fare.ui.recyclerview.adapters.LoadingAdapter
import com.github.yamin8000.fare.util.CONSTANTS.CITY_ID
import com.github.yamin8000.fare.util.CONSTANTS.DESTINATION
import com.github.yamin8000.fare.util.CONSTANTS.FEEDBACK
import com.github.yamin8000.fare.util.CONSTANTS.LINE_CODE
import com.github.yamin8000.fare.util.CONSTANTS.ORIGIN
import com.github.yamin8000.fare.util.Utility.handleCrash
import com.github.yamin8000.fare.util.Utility.hideKeyboard
import com.github.yamin8000.fare.util.helpers.ErrorHelper.netError
import com.github.yamin8000.fare.util.helpers.ErrorHelper.snack
import com.github.yamin8000.fare.web.Services
import com.github.yamin8000.fare.web.WEB
import com.github.yamin8000.fare.web.WEB.Companion.async
import com.github.yamin8000.fare.web.WEB.Companion.eqQuery
import com.github.yamin8000.fare.web.WEB.Companion.likeQuery
import com.google.android.material.snackbar.Snackbar

class SearchLineFragment :
    BaseFragment<FragmentSearchLineBinding>({ FragmentSearchLineBinding.inflate(it) }) {
    
    private var isFirstTime = true
    
    private val web : WEB by lazy(LazyThreadSafetyMode.NONE) { WEB() }
    
    private val loadingAdapter : LoadingAdapter by lazy(LazyThreadSafetyMode.NONE) {
        LoadingAdapter(R.layout.shimmer_city_search)
    }
    
    private val emptyAdapter : EmptyAdapter by lazy(LazyThreadSafetyMode.NONE) { EmptyAdapter() }
    
    private var searchParams = mutableMapOf<String, String>()
    
    private var currentCityId = ""
    
    private var cityModel : CityJoined? = null
    
    override fun onViewCreated(view : View, savedInstanceState : Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            val cityId = arguments?.getString(CITY_ID) ?: ""
            if (cityId.isNotEmpty()) {
                currentCityId = cityId
                searchParams[CITY_ID] = cityId
                
                getCityLines()
                getCityInfo(cityId)
                searchFilterClearButtonListener()
                handleMenu(cityId)
                fabClickListener()
            } else netError()
        } catch (exception : Exception) {
            handleCrash(exception)
        }
    }
    
    private fun fabClickListener() {
        binding.cityLinesFab.setOnClickListener {
            val safeContext = context
            safeContext?.let {
                var manager = binding.cityLineList.layoutManager
                val drawable : Drawable?
                if (manager is LinearLayoutManager) {
                    manager = StaggeredGridLayoutManager(2, RecyclerView.VERTICAL)
                    drawable = ContextCompat.getDrawable(it, R.drawable.ic_list_grid)
                } else {
                    manager = LinearLayoutManager(it)
                    drawable = ContextCompat.getDrawable(it, R.drawable.ic_list)
                }
                binding.cityLineList.layoutManager = manager
                binding.cityLinesFab.setImageDrawable(drawable)
            }
        }
    }
    
    private fun handleMenu(cityId : String) {
        binding.searchCityLinesToolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.search_city_line_menu_reference -> {
                    val bundle = bundleOf(CITY_ID to cityId)
                    findNavController().navigate(R.id.action_searchLineFragment_to_cityLinesInfoModal, bundle)
                }
                R.id.search_city_line_menu_report -> cityDataErrorReport()
            }
            true
        }
    }
    
    private fun cityDataErrorReport() {
        if (cityModel != null) {
            val safeCity = cityModel
            val cityLongName = "${safeCity?.id}-${safeCity?.name}-${safeCity?.county?.name}-${safeCity?.state?.name}"
            val feedbackTemplate = getString(R.string.line_error_feedback_template, cityLongName)
            val bundle = bundleOf(FEEDBACK to feedbackTemplate)
            findNavController().navigate(R.id.action_searchLineFragment_to_feedbackFragment, bundle)
        } else snack(getString(R.string.please_wait))
    }
    
    private fun searchFilterClearButtonListener() {
        binding.lineSearchFilterClear.setOnClickListener {
            it.isEnabled = false
            
            binding.lineOriginAuto.text.clear()
            binding.lineCodeAuto.text.clear()
            binding.lineDestinationAuto.text.clear()
            
            searchParams.clear()
            searchParams[CITY_ID] = currentCityId
            
            getCityLines()
        }
    }
    
    private fun getCityInfo(cityId : String) {
        val service = web.getService(Services.CityService::class.java)
        service.searchCity(cityId = cityId.eqQuery()).async(this, { list ->
            if (list != null && list.isNotEmpty()) {
                val cityInfo = list.first()
                binding.cityLinesToolbarTitle.text = getString(R.string.line_city_name_template,
                                                               cityInfo.name)
                this.cityModel = cityInfo
            }
        }) { netError() }
    }
    
    private fun getCityLines() {
        hideKeyboard()
        
        binding.cityLineList.adapter = loadingAdapter
        /**
         * use of **?.** safe call is intentional
         *
         * to pass null value query params for retrofit
         *
         * if that parameter doesn't exist
         */
        val cityIdQuery = searchParams[CITY_ID]?.eqQuery()
        val lineCodeQuery = searchParams[LINE_CODE]?.likeQuery()
        val originQuery = searchParams[ORIGIN]?.likeQuery()
        val destQuery = searchParams[DESTINATION]?.likeQuery()
        
        val service = web.getService(Services.LineService::class.java)
        service.getCityLines(cityId = cityIdQuery, lineCode = lineCodeQuery, origin = originQuery,
                             destination = destQuery).async(this, { list ->
            if (list != null && list.isNotEmpty()) populateCityLinesList(list)
            else binding.cityLineList.adapter = emptyAdapter
        }) { netError() }
    }
    
    private fun populateCityLinesList(list : List<Line>) {
        val adapter = SearchLineAdapter(list) { _, _ -> }
        binding.cityLineList.adapter = adapter
        adapter.notifyDataSetChanged()
        
        if (context != null) {
            val layoutManager = if (list.size <= 2) LinearLayoutManager(context)
            else StaggeredGridLayoutManager(2, RecyclerView.VERTICAL)
            binding.cityLineList.layoutManager = layoutManager
        }
        
        if (isFirstTime) {
            isFirstTime = false
            handleAutoCompletes(list)
            handleCustomProperties(list)
        }
    }
    
    private fun handleCustomProperties(list : List<Line>) {
        val hasCustomProperties = list.any { it.hasCustomProperty }
        if (hasCustomProperties) snack(getString(R.string.taxi_meter_city_notice), Snackbar.LENGTH_LONG)
    }
    
    private fun handleAutoCompletes(list : List<Line>) {
        isFirstTime = false
        searchFilterHandler()
        
        val codes = list.filter { !it.code.isNullOrBlank() }.map { it.code }.toSet().toList()
        val origins = list.filter { !it.origin.isNullOrBlank() }.map { it.origin }.toSet().toList()
        val destinations = list.filter { !it.destination.isNullOrBlank() }.map { it.destination }.toSet()
            .toList()
        
        populateAutoComplete(codes, binding.lineCodeAuto)
        populateAutoComplete(origins, binding.lineOriginAuto)
        populateAutoComplete(destinations, binding.lineDestinationAuto)
    }
    
    private fun <T> populateAutoComplete(list : List<T>, autoCompleteTextView : AutoCompleteTextView) {
        val safeContext = context
        if (safeContext != null) {
            val adapter = ArrayAdapter(safeContext, R.layout.dropdown_item, list)
            autoCompleteTextView.setAdapter(adapter)
        }
    }
    
    private fun searchFilterHandler() {
        binding.lineCodeInput.setStartIconOnClickListener { codeFilterHandler() }
        binding.lineCodeAuto.setOnItemClickListener { _, _, _, _ -> codeFilterHandler() }
        binding.lineCodeAuto.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                codeFilterHandler()
                binding.lineCodeAuto.dismissDropDown()
            }
            true
        }
        
        binding.lineOriginInput.setStartIconOnClickListener { originFilterHandler() }
        binding.lineOriginAuto.setOnItemClickListener { _, _, _, _ -> originFilterHandler() }
        binding.lineOriginAuto.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                originFilterHandler()
                binding.lineOriginAuto.dismissDropDown()
            }
            true
        }
        
        binding.lineDestinationInput.setStartIconOnClickListener { destinationFilterHandler() }
        binding.lineDestinationAuto.setOnItemClickListener { _, _, _, _ -> destinationFilterHandler() }
        binding.lineDestinationAuto.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                destinationFilterHandler()
                binding.lineDestinationAuto.dismissDropDown()
            }
            true
        }
    }
    
    private fun codeFilterHandler() {
        binding.lineSearchFilterClear.isEnabled = true
        searchParams[LINE_CODE] = binding.lineCodeAuto.text.toString()
        getCityLines()
    }
    
    private fun originFilterHandler() {
        binding.lineSearchFilterClear.isEnabled = true
        searchParams[ORIGIN] = binding.lineOriginAuto.text.toString()
        getCityLines()
    }
    
    private fun destinationFilterHandler() {
        binding.lineSearchFilterClear.isEnabled = true
        searchParams[DESTINATION] = binding.lineDestinationAuto.text.toString()
        getCityLines()
    }
}