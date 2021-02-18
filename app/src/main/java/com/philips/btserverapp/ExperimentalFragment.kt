package com.philips.btserverapp

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.welie.btserver.R
import com.philips.btserver.generichealthservice.ObservationEmitter
import kotlinx.android.synthetic.main.fragment_experimental.*
import kotlinx.android.synthetic.main.fragment_observations.*

/**
 * A simple [Fragment] subclass.
 * Use the [ExperimentalFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ExperimentalFragment : Fragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_experimental, container, false)
    }

    override fun onResume() {
        super.onResume()
        checkboxShortTypeCodes?.setOnClickListener { ObservationEmitter.shortTypeCodes(checkboxShortTypeCodes.isChecked) }
        choiceOmitFixedLengthTypes?.setOnClickListener { ObservationEmitter.omitFixedLengthTypes(choiceOmitFixedLengthTypes.isChecked) }
        choiceOmitHandleTLV?.setOnClickListener { ObservationEmitter.omitHandleTLV(choiceOmitHandleTLV.isChecked) }
        choiceOmitUnitCode?.setOnClickListener { ObservationEmitter.omitUnitCode(choiceOmitUnitCode.isChecked) }
        choiceObsArrayType?.setOnClickListener { ObservationEmitter.enableObservationArrayType(choiceObsArrayType.isChecked) }
    }

}