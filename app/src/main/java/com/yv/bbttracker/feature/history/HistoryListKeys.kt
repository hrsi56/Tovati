package com.yv.bbttracker.feature.history

/**
 * LazyColumn keys must be unique across every kind of row in the history screen.
 * Database ids are generated independently for cycles, observations, and measurements,
 * so the numeric value alone is not a safe cross-section key.
 */
internal object HistoryListKeys {
    const val HEADER = "history:header"
    const val EMPTY = "history:empty"
    const val BACKTEST = "history:backtest"
    const val SIGNS_TOGGLE = "history:signs:toggle"
    const val SIGNS_EMPTY = "history:signs:empty"
    const val MEASUREMENTS_TOGGLE = "history:measurements:toggle"
    const val MEASUREMENTS_EMPTY = "history:measurements:empty"
    const val BOTTOM_SPACER = "history:bottom-spacer"

    fun cycle(id: Long): String = "history:cycle:$id"

    fun observation(epochDay: Long): String = "history:observation:$epochDay"

    fun measurement(id: Long): String = "history:measurement:$id"
}
