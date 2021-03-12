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
import com.philips.btserver.R
import com.philips.btserver.generichealthservice.ObservationEmitter
import kotlinx.android.synthetic.main.fragment_experimental.*

class ExperimentalFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_experimental, container, false)
    }

    override fun onResume() {
        super.onResume()
        checkboxShortTypeCodes?.setOnClickListener { ObservationEmitter.useShortTypeCodes = checkboxShortTypeCodes.isChecked }
        choiceOmitFixedLengthTypes?.setOnClickListener { ObservationEmitter.omitFixedLengthTypes = choiceOmitFixedLengthTypes.isChecked }
        choiceOmitHandleTLV?.setOnClickListener { ObservationEmitter.omitHandleTLV = choiceOmitHandleTLV.isChecked }
        choiceOmitUnitCode?.setOnClickListener { ObservationEmitter.omitUnitCode = choiceOmitUnitCode.isChecked }
        choiceObsArrayType?.setOnClickListener { ObservationEmitter.enableObservationArrayType = choiceObsArrayType.isChecked }
    }

}