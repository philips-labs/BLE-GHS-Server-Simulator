package com.philips.btserver.observations

// From IEEE-11073-10101 Partition 129 codes
enum class MdcActivityCode(val value: Int) {
    MDC_HF_ACT_SKI					(8455154),     //Person is skiing.</p>
    MDC_HF_ACT_RUN					(8455155),     //Person is running.</p>
    MDC_HF_ACT_BIKE					(8455156),     //Person is cycling.</p>
    MDC_HF_ACT_STAIR				(8455157),     //Person is climbing stairs.</p>
    MDC_HF_ACT_ROW					(8455158),     //Person is rowing.</p>
    MDC_HF_ACT_HOME					(8455159),     //Person is engaged in general yard or house work.</p>
    MDC_HF_ACT_WORK					(8455160),     //Person is doing their job.</p>
    MDC_HF_ACT_WALK					(8455161),     //Person is walking.</p>
    MDC_HF_ACT_EXERCISE_BIKE		(8455162),     //Person is using exercise bike.</p>
    MDC_HF_ACT_GOLF					(8455163),     //Person is walking.</p>
    MDC_HF_ACT_HIKE					(8455164),     //Person is walking.</p>
    MDC_HF_ACT_SWIM					(8455165),     //Person is swimming.</p>
    MDC_HF_ACT_AEROBICS				(8455166),     //Person is doing aerobics exercise.</p>
    MDC_HF_ACT_DUMBBELL				(8455167),     //Person is using dumbbell.</p>
    MDC_HF_ACT_WEIGHT				(8455168),     //Person is doing weight training.</p>
    MDC_HF_ACT_BAND				    (8455169),     //Person is doing elastic band exercise.</p>
    MDC_HF_ACT_STRETCH				(8455170),     //Person is stretching.</p>
    MDC_HF_ACT_YOGA					(8455171),     //Person is doing yoga..</p>
    MDC_HF_ACT_WATER_WALK		    (8455172)     //Person is doing water walking.</p>
}