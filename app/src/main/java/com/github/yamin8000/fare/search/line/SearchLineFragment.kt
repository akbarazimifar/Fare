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
import android.os.Parcelable
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
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
import com.github.yamin8000.fare.util.CONSTANTS.CHOOSING_DEFAULT_CITY
import com.github.yamin8000.fare.util.CONSTANTS.CITY_ID
import com.github.yamin8000.fare.util.CONSTANTS.DESTINATION
import com.github.yamin8000.fare.util.CONSTANTS.FEEDBACK
import com.github.yamin8000.fare.util.CONSTANTS.GENERAL_PREFS
import com.github.yamin8000.fare.util.CONSTANTS.LIMIT
import com.github.yamin8000.fare.util.CONSTANTS.LINE_CODE
import com.github.yamin8000.fare.util.CONSTANTS.ORIGIN
import com.github.yamin8000.fare.util.CONSTANTS.ROW_LIMIT
import com.github.yamin8000.fare.util.SharedPrefs
import com.github.yamin8000.fare.util.Utility.handleCrash
import com.github.yamin8000.fare.util.Utility.hideKeyboard
import com.github.yamin8000.fare.util.helpers.ErrorHelper.netError
import com.github.yamin8000.fare.util.helpers.ErrorHelper.snack
import com.github.yamin8000.fare.web.APIs
import com.github.yamin8000.fare.web.WEB
import com.github.yamin8000.fare.web.WEB.Companion.async
import com.github.yamin8000.fare.web.WEB.Companion.eqQuery
import com.github.yamin8000.fare.web.WEB.Companion.likeQuery
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SearchLineFragment :
    BaseFragment<FragmentSearchLineBinding>({ FragmentSearchLineBinding.inflate(it) }) {
    
    private var isFirstTime = true
    
    private val web : WEB by lazy(LazyThreadSafetyMode.NONE) { WEB() }
    
    private val loadingAdapter : LoadingAdapter by lazy(LazyThreadSafetyMode.NONE) { LoadingAdapter(8) }
    
    private val emptyAdapter : EmptyAdapter by lazy(LazyThreadSafetyMode.NONE) { EmptyAdapter() }
    
    private var searchParams = mutableMapOf<String, String>()
    
    private var currentCityId = ""
    
    private var cityModel : CityJoined? = null
    
    private val searchLineAdapter = SearchLineAdapter()
    
    private var rowLimit = ROW_LIMIT
    
    private var lastRowSize = ROW_LIMIT
    
    private var recyclerViewState : Parcelable? = null
    
    private var scrollSnackbar : Snackbar? = null
    
    private val backScope = CoroutineScope(Dispatchers.Default)
    
    private val ioScope = CoroutineScope(Dispatchers.IO)
    
    override fun onViewCreated(view : View, savedInstanceState : Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            handleDefaultCityChoosing()
            
            val cityId = arguments?.getString(CITY_ID) ?: ""
            if (cityId.isNotEmpty()) {
                currentCityId = cityId
                searchParams[CITY_ID] = cityId
                searchParams[LIMIT] = "$rowLimit"
                
                lifecycleScope.launch { getCityLines() }
                lifecycleScope.launch { getCityInfo(cityId) }
                lifecycleScope.launch { handleMenu(cityId) }
            } else netError()
        } catch (exception : Exception) {
            handleCrash(exception)
        }
    }
    
    /**
     * Handle default city choosing
     *
     * show message if user is choosing a default city,
     * and this city is selected as the default city
     *
     */
    private fun handleDefaultCityChoosing() {
        val isChoosingDefaultCity = arguments?.getBoolean(CHOOSING_DEFAULT_CITY) ?: false
        if (isChoosingDefaultCity) {
            snack(getString(R.string.city_set_as_current_city), Snackbar.LENGTH_LONG)
        }
    }
    
    /**
     * List scroll handler
     *
     * create new request each time user scroll to end of the list
     */
    private fun listScrollHandler() {
        binding.cityLineList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView : RecyclerView, newState : Int) {
                super.onScrollStateChanged(recyclerView, newState)
                val isScrollingToEnd = !recyclerView.canScrollVertically(1)
                val isScrollEnded = newState == RecyclerView.SCROLL_STATE_IDLE
                val isAllDataFetched = lastRowSize >= rowLimit
                if (isScrollingToEnd && isScrollEnded && isAllDataFetched) {
                    rowLimit += ROW_LIMIT
                    searchParams[LIMIT] = "$rowLimit"
                    recyclerViewState = recyclerView.layoutManager?.onSaveInstanceState()
                    getCityLines()
                    scrollSnackbar = snack(getString(R.string.please_wait))
                }
            }
        })
    }
    
    /**
     * FAB click listener,
     * this fab is used for changing between list layout manager or grid/stagger layout manager
     *
     */
    private fun fabClickListener() {
        binding.cityLinesFab.setOnClickListener {
            context?.let {
                var manager = binding.cityLineList.layoutManager
                val drawable : Drawable?
                /**
                 * First visible items,
                 * array has two params because span count is 2,
                 * this is very error-prone if span count is changed
                 */
                val firstVisibleItems = intArrayOf(0, 0)
                if (manager is LinearLayoutManager) {
                    firstVisibleItems[0] = manager.findFirstVisibleItemPosition()
                    manager = StaggeredGridLayoutManager(2, RecyclerView.VERTICAL)
                    drawable = ContextCompat.getDrawable(it, R.drawable.ic_list_grid)
                } else {
                    (manager as StaggeredGridLayoutManager).findFirstVisibleItemPositions(firstVisibleItems)
                    manager = LinearLayoutManager(it)
                    drawable = ContextCompat.getDrawable(it, R.drawable.ic_list)
                }
                binding.cityLineList.layoutManager = manager
                binding.cityLinesFab.setImageDrawable(drawable)
                binding.cityLineList.scrollToPosition(firstVisibleItems[0])
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
                R.id.search_city_line_menu_my_city -> setCityAsMyCity(cityId)
            }
            true
        }
    }
    
    private fun setCityAsMyCity(cityId : String) {
        context?.let {
            ioScope.launch {
                val sharedPrefs = SharedPrefs(it, GENERAL_PREFS)
                sharedPrefs.write(CITY_ID, cityId)
            }
        }
        snack(getString(R.string.city_set_as_current_city), Snackbar.LENGTH_LONG)
    }
    
    /**
     * report city line data error,
     * using feedback fragment
     *
     */
    private fun cityDataErrorReport() {
        cityModel?.let {
            val cityLongName = "${it.id}-${it.name}-${it.county.name}-${it.state.name}"
            val feedbackTemplate = getString(R.string.line_error_feedback_template, cityLongName)
            val bundle = bundleOf(FEEDBACK to feedbackTemplate)
            findNavController().navigate(R.id.action_searchLineFragment_to_feedbackFragment, bundle)
        }
    }
    
    /**
     * Search filter clear button listener,
     * this button clear input/filters and make a new search
     *
     */
    private fun searchFilterClearButtonListener() {
        binding.lineSearchFilterClear.setOnClickListener {
            it.isEnabled = false
            
            binding.lineOriginAuto.text.clear()
            binding.lineCodeAuto.text.clear()
            binding.lineDestinationAuto.text.clear()
            
            searchParams.clear()
            searchParams[CITY_ID] = currentCityId
            searchParams[LIMIT] = "$rowLimit"
            
            getCityLines()
        }
    }
    
    private fun getCityInfo(cityId : String) {
        val service = web.getAPI<APIs.CityAPI>()
        service.searchCity(cityId = cityId.eqQuery()).async(this, { list ->
            if (list.isNotEmpty()) {
                val cityInfo = list.first()
                binding.cityLinesToolbarTitle.text = getString(R.string.line_city_name_template,
                                                               cityInfo.name)
                this.cityModel = cityInfo
            }
        }) { netError() }
    }
    
    /**
     * Get current city lines form server
     *
     */
    private fun getCityLines() {
        hideKeyboard()
        if (isFirstTime) binding.cityLineList.adapter = loadingAdapter
        
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
        val limitQuery = searchParams[LIMIT]
        
        val service = web.getAPI<APIs.LineAPI>()
        service.getCityLines(cityId = cityIdQuery, lineCode = lineCodeQuery, origin = originQuery,
                             destination = destQuery, limit = limitQuery).async(this, { list ->
            if (list.isNotEmpty()) {
                populateCityLinesList(list)
                lastRowSize = list.size
            } else {
                snack(getString(R.string.data_empty))
                binding.cityLineList.adapter = emptyAdapter
            }
            scrollSnackbar?.dismiss()
        }) {
            scrollSnackbar?.dismiss()
            netError()
        }
    }
    
    /**
     * Populate city lines list to recycler view
     *
     * @param list list of city lines
     */
    private fun populateCityLinesList(list : List<Line>) {
        searchLineAdapter.submitList(list)
        
        handleLayoutManager(list.size)
        if (isFirstTime) {
            isFirstTime = false
            handleCustomProperties(list)
            listScrollHandler()
            fabClickListener()
            searchFilterClearButtonListener()
        }
        binding.cityLineList.adapter = searchLineAdapter
        lifecycleScope.launch { handleAutoCompletes(list) }
    }
    
    /**
     * Handle layout manager,
     * change layout manager based on data size
     *
     * @param listSize size of the list
     */
    private fun handleLayoutManager(listSize : Int) {
        if (context != null && recyclerViewState == null) {
            val layoutManager = if (listSize <= 2) LinearLayoutManager(context)
            else StaggeredGridLayoutManager(2, RecyclerView.VERTICAL)
            binding.cityLineList.layoutManager = layoutManager
        }
        binding.cityLineList.layoutManager?.onRestoreInstanceState(recyclerViewState)
    }
    
    /**
     * Handle custom properties,
     * search if this city has custom property like taxi meter
     *
     * @param list list of city lines
     */
    private fun handleCustomProperties(list : List<Line>) {
        val hasCustomProperties = list.any { it.hasCustomProperty }
        if (hasCustomProperties) snack(getString(R.string.taxi_meter_city_notice), Snackbar.LENGTH_LONG)
    }
    
    /**
     * Handle auto completes,
     * prepare data for autocompletes
     *
     * @param list list of city lines
     */
    private fun handleAutoCompletes(list : List<Line>) = backScope.launch {
        searchFilterHandler()
        
        
        val codes = list.asSequence().filter { !it.code.isNullOrBlank() }.map { it.code }.toSet().toList()
        val origins = list.asSequence().filter { !it.origin.isNullOrBlank() }.asSequence().map { it.origin }
            .toSet().toList()
        val destinations = list.asSequence().filter { !it.destination.isNullOrBlank() }.asSequence()
            .map { it.destination }.toSet().toList()
        
        lifecycleScope.launch {
            populateAutoComplete(codes, binding.lineCodeAuto)
            populateAutoComplete(origins, binding.lineOriginAuto)
            populateAutoComplete(destinations, binding.lineDestinationAuto)
        }
    }
    
    /**
     * Populate auto complete,
     * add data to auto complete
     *
     * @param T data type
     * @param list list of data
     * @param autoCompleteTextView autocomplete that's going to be filled
     */
    private fun <T> populateAutoComplete(list : List<T>, autoCompleteTextView : AutoCompleteTextView) {
        context?.let {
            val adapter = ArrayAdapter(it, R.layout.dropdown_item, list)
            autoCompleteTextView.setAdapter(adapter)
        }
    }
    
    private fun searchFilterHandler() {
        binding.lineCodeInput.setStartIconOnClickListener {
            filterHandler(LINE_CODE, binding.lineCodeAuto.text.toString())
        }
        binding.lineCodeAuto.setOnItemClickListener { _, _, _, _ ->
            filterHandler(LINE_CODE, binding.lineCodeAuto.text.toString())
        }
        binding.lineCodeAuto.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                filterHandler(LINE_CODE, binding.lineCodeAuto.text.toString())
                binding.lineCodeAuto.dismissDropDown()
            }
            true
        }
        
        binding.lineOriginInput.setStartIconOnClickListener {
            filterHandler(ORIGIN, binding.lineOriginAuto.text.toString())
        }
        binding.lineOriginAuto.setOnItemClickListener { _, _, _, _ ->
            filterHandler(ORIGIN, binding.lineOriginAuto.text.toString())
        }
        binding.lineOriginAuto.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                filterHandler(ORIGIN, binding.lineOriginAuto.text.toString())
                binding.lineOriginAuto.dismissDropDown()
            }
            true
        }
        
        binding.lineDestinationInput.setStartIconOnClickListener {
            filterHandler(DESTINATION, binding.lineDestinationAuto.text.toString())
        }
        binding.lineDestinationAuto.setOnItemClickListener { _, _, _, _ ->
            filterHandler(DESTINATION, binding.lineDestinationAuto.text.toString())
        }
        binding.lineDestinationAuto.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                filterHandler(DESTINATION, binding.lineDestinationAuto.text.toString())
                binding.lineDestinationAuto.dismissDropDown()
            }
            true
        }
    }
    
    /**
     * Filter handler, add given filter to params and start a new search
     *
     * @param paramConstant filter parameter name
     * @param searchParam filter parameter content
     */
    private fun filterHandler(paramConstant : String, searchParam : String) {
        binding.lineSearchFilterClear.isEnabled = true
        searchParams[paramConstant] = searchParam
        getCityLines()
    }
}