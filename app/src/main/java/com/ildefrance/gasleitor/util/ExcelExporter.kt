package com.ildefrance.gasleitor.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.ildefrance.gasleitor.data.model.Reading
import com.ildefrance.gasleitor.data.model.ReadingCycle
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object ExcelExporter {

    fun export(context: Context, cycle: ReadingCycle, readings: List<Reading>): File {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Leitura de Gás")

        // ── Styles ──────────────────────────────────────────────────────────
        val headerFont = workbook.createFont().apply {
            bold = true
            fontHeightInPoints = 12
            color = IndexedColors.WHITE.index
        }
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.DARK_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            setFont(headerFont)
            alignment = HorizontalAlignment.CENTER
            borderBottom = BorderStyle.THIN
        }

        val titleFont = workbook.createFont().apply {
            bold = true
            fontHeightInPoints = 14
        }
        val titleStyle = workbook.createCellStyle().apply {
            setFont(titleFont)
            alignment = HorizontalAlignment.CENTER
        }

        val evenStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.LIGHT_CORNFLOWER_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }
        val oddStyle = workbook.createCellStyle()

        val numberFormat = workbook.createDataFormat()
        val numStyle = workbook.createCellStyle().apply {
            dataFormat = numberFormat.getFormat("0.000")
        }
        val numEvenStyle = workbook.createCellStyle().apply {
            dataFormat = numberFormat.getFormat("0.000")
            fillForegroundColor = IndexedColors.LIGHT_CORNFLOWER_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }

        val monthNames = listOf("", "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
            "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")

        // ── Title rows ──────────────────────────────────────────────────────
        var rowIndex = 0

        sheet.createRow(rowIndex++).apply {
            val cell = createCell(0)
            cell.setCellValue("Cond. Ile de France – Leitura de Gás")
            cell.cellStyle = titleStyle
        }
        sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 3))

        sheet.createRow(rowIndex++).apply {
            val cell = createCell(0)
            cell.setCellValue("Competência: ${monthNames[cycle.month]}/${cycle.year}")
            cell.cellStyle = titleStyle
        }
        sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(1, 1, 0, 3))

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
        sheet.createRow(rowIndex++).apply {
            val cell = createCell(0)
            cycle.finishedAt?.let {
                cell.setCellValue("Finalizado em: ${sdf.format(Date(it))}")
            } ?: cell.setCellValue("Gerado em: ${sdf.format(Date())}")
        }
        sheet.addMergedRegion(org.apache.poi.ss.util.CellRangeAddress(2, 2, 0, 3))

        rowIndex++ // blank row

        // ── Header row ──────────────────────────────────────────────────────
        sheet.createRow(rowIndex++).apply {
            listOf("Andar", "Apartamento", "Leitura (m³)", "Status").forEachIndexed { col, title ->
                createCell(col).apply {
                    setCellValue(title)
                    cellStyle = headerStyle
                }
            }
        }

        // ── Data rows ───────────────────────────────────────────────────────
        val readingMap = readings.associateBy { it.apartment }
        val allApts = ApartmentHelper.getAllApartments().reversed()
        var dataRow = 0

        allApts.forEach { (apt, floor) ->
            val reading = readingMap[apt]
            val isEven = (dataRow % 2 == 0)
            val baseStyle = if (isEven) evenStyle else oddStyle
            val numCellStyle = if (isEven) numEvenStyle else numStyle

            sheet.createRow(rowIndex++).apply {
                createCell(0).apply {
                    setCellValue("${floor}º")
                    cellStyle = baseStyle
                }
                createCell(1).apply {
                    setCellValue("Apto $apt")
                    cellStyle = baseStyle
                }
                createCell(2).apply {
                    if (reading != null) {
                        setCellValue(reading.value)
                        cellStyle = numCellStyle
                    } else {
                        setCellValue("—")
                        cellStyle = baseStyle
                    }
                }
                createCell(3).apply {
                    setCellValue(if (reading != null) "✓ Lido" else "✗ Pendente")
                    cellStyle = baseStyle
                }
            }
            dataRow++
        }

        // ── Summary row ─────────────────────────────────────────────────────
        rowIndex++ // blank
        sheet.createRow(rowIndex).apply {
            createCell(0).apply {
                setCellValue("Total lidos: ${readings.size} / ${ApartmentHelper.getTotalCount()}")
                val boldStyle = workbook.createCellStyle()
                boldStyle.setFont(workbook.createFont().apply { bold = true })
                cellStyle = boldStyle
            }
        }

        // ── Column widths ────────────────────────────────────────────────────
        sheet.setColumnWidth(0, 2500)
        sheet.setColumnWidth(1, 4000)
        sheet.setColumnWidth(2, 4500)
        sheet.setColumnWidth(3, 3500)

        // ── Write file ───────────────────────────────────────────────────────
        val fileName = "leitura_gas_${cycle.month.toString().padStart(2, '0')}_${cycle.year}.xlsx"
        val file = File(context.getExternalFilesDir(null), fileName)
        FileOutputStream(file).use { workbook.write(it) }
        workbook.close()

        return file
    }

    fun shareFile(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Leitura de Gás – Ile de France")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Exportar planilha via…"))
    }
}
