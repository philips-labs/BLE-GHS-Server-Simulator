/*
 * Copyright (c) Koninklijke Philips N.V. 2021.
 * All rights reserved.
 */
package com.philips.btserverapp

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.philips.btserver.generichealthservice.ObservationEmitter
import com.philips.btserver.databinding.FragmentExperimentalBinding

class ExperimentalFragment : Fragment() {

    private var _binding: FragmentExperimentalBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = FragmentExperimentalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        binding.checkboxShortTypeCodes.setOnClickListener { ObservationEmitter.useShortTypeCodes = binding.checkboxShortTypeCodes.isChecked }
        binding.choiceOmitFixedLengthTypes.setOnClickListener { ObservationEmitter.omitFixedLengthTypes = binding.choiceOmitFixedLengthTypes.isChecked }
        binding.choiceOmitHandleTLV.setOnClickListener { ObservationEmitter.omitHandleTLV = binding.choiceOmitHandleTLV.isChecked }
        binding.choiceOmitUnitCode.setOnClickListener { ObservationEmitter.omitUnitCode = binding.choiceOmitUnitCode.isChecked }
        binding.checkboxMergeObs.setOnClickListener { ObservationEmitter.mergeObservations = binding.checkboxMergeObs.isChecked }
        binding.choiceObsArrayType.setOnClickListener { ObservationEmitter.enableObservationArrayType = binding.choiceObsArrayType.isChecked }
        binding.checkboxMergeObs.isChecked = ObservationEmitter.mergeObservations
    }

}