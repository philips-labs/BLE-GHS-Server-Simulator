package com.philips.btserver.observations

// From IEEE-11073-10101 Partition 129 codes
enum class MdcActivityCode(val value: Int, val description : String) {
    MDC_HF_ACT_SKI					(8455154, "Skiing"),     //Person is skiing.</p>
    MDC_HF_ACT_RUN					(8455155, "Running"),     //Person is running.</p>
    MDC_HF_ACT_BIKE					(8455156, "Cycling"),     //Person is cycling.</p>
    MDC_HF_ACT_STAIR				(8455157,"Stairs"),     //Person is climbing stairs.</p>
    MDC_HF_ACT_ROW					(8455158, "Rowing"),     //Person is rowing.</p>
    MDC_HF_ACT_HOME					(8455159, "House work"),     //Person is engaged in general yard or house work.</p>
    MDC_HF_ACT_WORK					(8455160, "Work"),     //Person is doing their job.</p>
    MDC_HF_ACT_WALK					(8455161, "Walking"),     //Person is walking.</p>
    MDC_HF_ACT_EXERCISE_BIKE		(8455162, "Stationary bike"),     //Person is using exercise bike.</p>
    MDC_HF_ACT_GOLF					(8455163, "Golf"),     //Person is walking.</p>
    MDC_HF_ACT_HIKE					(8455164, "Hiking"),     //Person is walking.</p>
    MDC_HF_ACT_SWIM					(8455165, "Swimming"),     //Person is swimming.</p>
    MDC_HF_ACT_AEROBICS				(8455166, "Aerobics"),     //Person is doing aerobics exercise.</p>
    MDC_HF_ACT_DUMBBELL				(8455167, "Dumbbell"),     //Person is using dumbbell.</p>
    MDC_HF_ACT_WEIGHT				(8455168, "Weight training"),     //Person is doing weight training.</p>
    MDC_HF_ACT_BAND				    (8455169, "Elastic bands"),     //Person is doing elastic band exercise.</p>
    MDC_HF_ACT_STRETCH				(8455170, "Stretching"),     //Person is stretching.</p>
    MDC_HF_ACT_YOGA					(8455171, "Yoga"),     //Person is doing yoga..</p>
    MDC_HF_ACT_WATER_WALK		    (8455172, "Water walking")     //Person is doing water walking.</p>
}