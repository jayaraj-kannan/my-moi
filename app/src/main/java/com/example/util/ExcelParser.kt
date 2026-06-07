package com.example.util

import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

data class ExcelSheet(
    val name: String,
    val sheetId: String,
    val fileIndex: Int
)

data class ExcelWorkbook(
    val sheets: List<ExcelSheet>,
    val sharedStrings: List<String>,
    val sheetData: Map<String, List<List<String>>>
)

object ExcelParser {
    fun parseXlsx(inputStream: InputStream): ExcelWorkbook {
        val sheets = mutableListOf<ExcelSheet>()
        val sharedStrings = mutableListOf<String>()
        val sheetFiles = mutableMapOf<String, ByteArray>()
        var workbookBytes: ByteArray? = null
        var sharedStringsBytes: ByteArray? = null

        try {
            val zip = ZipInputStream(inputStream)
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name == "xl/workbook.xml") {
                    workbookBytes = zip.readBytes()
                } else if (name == "xl/sharedStrings.xml") {
                    sharedStringsBytes = zip.readBytes()
                } else if (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml")) {
                    sheetFiles[name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Parse shared strings
        if (sharedStringsBytes != null) {
            try {
                val dbFactory = DocumentBuilderFactory.newInstance()
                val dBuilder = dbFactory.newDocumentBuilder()
                val doc = dBuilder.parse(sharedStringsBytes.inputStream())
                val siList = doc.getElementsByTagName("t")
                for (i in 0 until siList.length) {
                    val element = siList.item(i) as Element
                    sharedStrings.add(element.textContent ?: "")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Parse workbook for sheet definitions
        if (workbookBytes != null) {
            try {
                val dbFactory = DocumentBuilderFactory.newInstance()
                val dBuilder = dbFactory.newDocumentBuilder()
                val doc = dBuilder.parse(workbookBytes.inputStream())
                val sheetList = doc.getElementsByTagName("sheet")
                for (i in 0 until sheetList.length) {
                    val element = sheetList.item(i) as Element
                    val sheetName = element.getAttribute("name")
                    val sheetId = element.getAttribute("sheetId")
                    sheets.add(ExcelSheet(sheetName, sheetId, i + 1))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Parse worksheets
        val sheetData = mutableMapOf<String, List<List<String>>>()
        for (sheet in sheets) {
            val fileName = "xl/worksheets/sheet${sheet.sheetId}.xml"
            val sheetBytes = sheetFiles[fileName] ?: sheetFiles["xl/worksheets/sheet${sheet.fileIndex}.xml"]
            if (sheetBytes != null) {
                try {
                    val dbFactory = DocumentBuilderFactory.newInstance()
                    val dBuilder = dbFactory.newDocumentBuilder()
                    val doc = dBuilder.parse(sheetBytes.inputStream())
                    
                    val rowList = doc.getElementsByTagName("row")
                    val sheetRows = mutableListOf<List<String>>()
                    
                    for (r in 0 until rowList.length) {
                        val rowEl = rowList.item(r) as Element
                        val cellList = rowEl.getElementsByTagName("c")
                        val rowVals = mutableListOf<String>()
                        
                        var currentColIdx = 0
                        for (c in 0 until cellList.length) {
                            val cellEl = cellList.item(c) as Element
                            val ref = cellEl.getAttribute("r")
                            val type = cellEl.getAttribute("t")
                            
                            val vNodeList = cellEl.getElementsByTagName("v")
                            val rawVal = if (vNodeList.length > 0) {
                                vNodeList.item(0).textContent ?: ""
                            } else ""
                            
                            val finalVal = when {
                                type == "s" && rawVal.isNotEmpty() -> {
                                    val idx = rawVal.toIntOrNull()
                                    if (idx != null && idx in sharedStrings.indices) {
                                        sharedStrings[idx]
                                    } else {
                                        rawVal
                                    }
                                }
                                type == "inlineStr" || type == "str" -> {
                                    val tNodeList = cellEl.getElementsByTagName("t")
                                    if (tNodeList.length > 0) {
                                        tNodeList.item(0).textContent ?: rawVal
                                    } else {
                                        rawVal
                                    }
                                }
                                else -> rawVal
                            }
                            
                            if (ref.isNotEmpty()) {
                                val colIdx = excelRefToColIndex(ref)
                                while (currentColIdx < colIdx) {
                                    rowVals.add("")
                                    currentColIdx++
                                }
                                rowVals.add(finalVal)
                                currentColIdx++
                            } else {
                                rowVals.add(finalVal)
                                currentColIdx++
                            }
                        }
                        sheetRows.add(rowVals)
                    }
                    sheetData[sheet.name] = sheetRows
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        return ExcelWorkbook(sheets, sharedStrings, sheetData)
    }

    private fun excelRefToColIndex(ref: String): Int {
        val letters = ref.takeWhile { it.isLetter() }.uppercase()
        var index = 0
        for (char in letters) {
            index = index * 26 + (char - 'A' + 1)
        }
        return index - 1
    }
}
