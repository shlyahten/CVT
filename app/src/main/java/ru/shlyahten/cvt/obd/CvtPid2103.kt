package ru.shlyahten.cvt.obd

/**
 * PID `2103` (Mitsubishi CVT): zero-based index of the raw count **N** in the data bytes that follow
 * the response header `61 03` (after ISO-TP merge). Matches [PIDs.csv] equations and a verified ELM log (info.md / Carscanner).
 */
const val CVT_2103_TEMP_COUNT_BYTE_INDEX = 13
