package com.laundrypos.print

import android.content.Context
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.SunmiPrinterService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface ReceiptPrinter {
    val ready: StateFlow<Boolean>
    fun print(lines: List<PrintLine>): Boolean
}

class SunmiPrinter(private val context: Context) : ReceiptPrinter {
    @Volatile
    private var service: SunmiPrinterService? = null
    private val _ready = MutableStateFlow(false)
    override val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val callback = object : InnerPrinterCallback() {
        override fun onConnected(printer: SunmiPrinterService) {
            service = printer
            _ready.value = runCatching { InnerPrinterManager.getInstance().hasPrinter(printer) }.getOrDefault(false)
        }

        override fun onDisconnected() {
            service = null
            _ready.value = false
        }
    }

    fun connect() {
        runCatching { InnerPrinterManager.getInstance().bindService(context, callback) }
    }

    @Synchronized
    override fun print(lines: List<PrintLine>): Boolean {
        val printer = service ?: return false
        return runCatching {
            printer.printerInit(null)
            lines.forEach { line ->
                when (line) {
                    is PrintLine.Text -> {
                        printer.setAlignment(line.align.ordinal, null)
                        printer.setFontSize(line.size.points, null)
                        if (line.bold) printer.sendRAWData(BOLD_ON, null)
                        printer.printText(line.text + "\n", null)
                        if (line.bold) printer.sendRAWData(BOLD_OFF, null)
                    }
                    PrintLine.Feed -> printer.lineWrap(1, null)
                }
            }
            printer.setAlignment(Align.LEFT.ordinal, null)
            printer.setFontSize(TextSize.NORMAL.points, null)
            printer.lineWrap(4, null)
            runCatching { printer.cutPaper(null) }
            true
        }.getOrDefault(false)
    }

    private companion object {
        val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
        val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    }
}
