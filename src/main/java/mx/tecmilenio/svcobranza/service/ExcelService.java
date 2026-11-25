package mx.tecmilenio.svcobranza.service;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import mx.tecmilenio.svcobranza.AppException;
import mx.tecmilenio.svcobranza.model.Balance;
import mx.tecmilenio.svcobranza.model.Movimiento;
import mx.tecmilenio.svcobranza.model.Pendiente;
import mx.tecmilenio.svcobranza.model.ReporteUsuario;
import mx.tecmilenio.svcobranza.ui.Formatos;

/** RF-BAL-05: exporta los reportes a archivos de Excel (.xlsx). */
public class ExcelService {

    public void exportarBalance(Balance balance, Path destino) {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            CellStyle negrita = negrita(libro);
            Sheet hoja = libro.createSheet("Balance");
            int fila = 0;
            fila = texto(hoja, fila, negrita, "Balance del " + Formatos.fecha(balance.desde()) + " al "
                    + Formatos.fecha(balance.hasta()));
            fila++;
            fila = fila(hoja, fila, negrita, "Total de ingresos", Formatos.moneda(balance.totalIngresos()), null);
            fila = fila(hoja, fila, negrita, "Total de egresos", Formatos.moneda(balance.totalEgresos()), null);
            fila = fila(hoja, fila, negrita, "Saldo", Formatos.moneda(balance.saldo()), null);
            fila = texto(hoja, fila, null, balance.egresosSuperanIngresos()
                    ? "ATENCIÓN: los egresos superan a los ingresos del periodo."
                    : "Los egresos no superan a los ingresos del periodo.");
            fila++;
            fila = seccion(hoja, fila, negrita, "Ingresos por categoría", balance.ingresosPorCategoria(),
                    balance.porcentajesIngresos());
            seccion(hoja, fila + 1, negrita, "Egresos por categoría", balance.egresosPorCategoria(),
                    balance.porcentajesEgresos());
            ajustar(hoja, 3);
            guardar(libro, destino);
        } catch (IOException e) {
            throw new AppException("No se pudo crear el archivo de Excel", e);
        }
    }

    public void exportarReporteUsuario(ReporteUsuario reporte, Path destino) {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            CellStyle negrita = negrita(libro);
            Sheet hoja = libro.createSheet("Usuario");
            int fila = texto(hoja, 0, negrita, "Reporte de " + reporte.usuario());
            fila = fila(hoja, fila, negrita, "Total pagado", Formatos.moneda(reporte.totalPagado()), null);
            fila = fila(hoja, fila, negrita, "Total adeudado", Formatos.moneda(reporte.totalAdeudado()), null);
            fila++;
            fila = encabezado(hoja, fila, negrita, "Fecha", "Concepto", "Categoría", "Forma de pago", "Monto");
            for (Movimiento m : reporte.movimientos()) {
                Row r = hoja.createRow(fila++);
                r.createCell(0).setCellValue(Formatos.fecha(m.fecha()));
                r.createCell(1).setCellValue(m.concepto());
                r.createCell(2).setCellValue(m.categoria());
                r.createCell(3).setCellValue(m.formaPago() == null ? "" : m.formaPago().toString());
                r.createCell(4).setCellValue(Formatos.moneda(m.monto()));
            }
            fila++;
            fila = texto(hoja, fila, negrita, "Pendientes");
            fila = encabezado(hoja, fila, negrita, "Fecha límite", "Concepto", "Categoría", "Estado", "Monto");
            for (Pendiente p : reporte.pendientes()) {
                Row r = hoja.createRow(fila++);
                r.createCell(0).setCellValue(Formatos.fecha(p.fechaLimite()));
                r.createCell(1).setCellValue(p.concepto());
                r.createCell(2).setCellValue(p.categoria());
                r.createCell(3).setCellValue(p.pagado() ? "Pagado" : "Pendiente");
                r.createCell(4).setCellValue(Formatos.moneda(p.monto()));
            }
            ajustar(hoja, 5);
            guardar(libro, destino);
        } catch (IOException e) {
            throw new AppException("No se pudo crear el archivo de Excel", e);
        }
    }

    private static int seccion(Sheet hoja, int fila, CellStyle negrita, String titulo,
            Map<String, BigDecimal> montos, Map<String, BigDecimal> porcentajes) {
        fila = texto(hoja, fila, negrita, titulo);
        fila = encabezado(hoja, fila, negrita, "Categoría", "Monto", "Porcentaje");
        for (Map.Entry<String, BigDecimal> e : montos.entrySet()) {
            BigDecimal pct = porcentajes.get(e.getKey());
            fila = fila(hoja, fila, null, e.getKey(), Formatos.moneda(e.getValue()),
                    pct == null ? "" : pct.toPlainString() + " %");
        }
        return fila;
    }

    private static int texto(Sheet hoja, int fila, CellStyle estilo, String texto) {
        Row r = hoja.createRow(fila);
        r.createCell(0).setCellValue(texto);
        if (estilo != null) {
            r.getCell(0).setCellStyle(estilo);
        }
        return fila + 1;
    }

    private static int fila(Sheet hoja, int fila, CellStyle estiloEtiqueta, String a, String b, String c) {
        Row r = hoja.createRow(fila);
        r.createCell(0).setCellValue(a);
        if (estiloEtiqueta != null) {
            r.getCell(0).setCellStyle(estiloEtiqueta);
        }
        r.createCell(1).setCellValue(b);
        if (c != null) {
            r.createCell(2).setCellValue(c);
        }
        return fila + 1;
    }

    private static int encabezado(Sheet hoja, int fila, CellStyle negrita, String... titulos) {
        Row r = hoja.createRow(fila);
        for (int i = 0; i < titulos.length; i++) {
            r.createCell(i).setCellValue(titulos[i]);
            r.getCell(i).setCellStyle(negrita);
        }
        return fila + 1;
    }

    private static CellStyle negrita(XSSFWorkbook libro) {
        Font fuente = libro.createFont();
        fuente.setBold(true);
        CellStyle estilo = libro.createCellStyle();
        estilo.setFont(fuente);
        return estilo;
    }

    private static void ajustar(Sheet hoja, int columnas) {
        for (int i = 0; i < columnas; i++) {
            hoja.setColumnWidth(i, 22 * 256);
        }
    }

    private static void guardar(XSSFWorkbook libro, Path destino) throws IOException {
        try (OutputStream salida = Files.newOutputStream(destino)) {
            libro.write(salida);
        }
    }
}
