package mx.tecmilenio.svcobranza.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mx.tecmilenio.svcobranza.model.Balance;
import mx.tecmilenio.svcobranza.model.EstadoPendiente;
import mx.tecmilenio.svcobranza.model.FormaPago;
import mx.tecmilenio.svcobranza.model.Movimiento;
import mx.tecmilenio.svcobranza.model.Pendiente;
import mx.tecmilenio.svcobranza.model.ReporteUsuario;
import mx.tecmilenio.svcobranza.model.TipoMovimiento;

class ExcelServiceTest {

    private final ExcelService excel = new ExcelService();

    private static Sheet abrir(Path archivo, XSSFWorkbook[] libro) throws IOException {
        try (InputStream in = Files.newInputStream(archivo)) {
            libro[0] = new XSSFWorkbook(in);
        }
        return libro[0].getSheetAt(0);
    }

    private static String texto(Sheet hoja, int fila, int columna) {
        Row r = hoja.getRow(fila);
        return r == null || r.getCell(columna) == null ? "" : r.getCell(columna).getStringCellValue();
    }

    private static Balance balanceDePrueba(String ingresos, String egresos) {
        Map<String, BigDecimal> i = new LinkedHashMap<>();
        i.put("Tiendita", new BigDecimal(ingresos));
        Map<String, BigDecimal> e = new LinkedHashMap<>();
        e.put("Luz", new BigDecimal(egresos));
        return new Balance(LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 29), i, e);
    }

    @Test
    void exportaElBalanceConTotalesYSaldo(@TempDir Path carpeta) throws IOException {
        Path archivo = carpeta.resolve("balance.xlsx");

        excel.exportarBalance(balanceDePrueba("1000", "400"), archivo);

        XSSFWorkbook[] libro = new XSSFWorkbook[1];
        try {
            Sheet hoja = abrir(archivo, libro);
            assertEquals("Balance", hoja.getSheetName());
            assertTrue(texto(hoja, 0, 0).contains("23/11/2026"));
            assertTrue(texto(hoja, 0, 0).contains("29/11/2026"));
            assertEquals("Total de ingresos", texto(hoja, 2, 0));
            assertTrue(texto(hoja, 2, 1).contains("1,000.00"));
            assertEquals("Saldo", texto(hoja, 4, 0));
            assertTrue(texto(hoja, 4, 1).contains("600.00"));
        } finally {
            libro[0].close();
        }
    }

    @Test
    void elBalanceIncluyeElDesgloseYElPorcentajeDeCadaCategoria(@TempDir Path carpeta) throws IOException {
        Path archivo = carpeta.resolve("balance.xlsx");
        excel.exportarBalance(balanceDePrueba("1000", "400"), archivo);

        XSSFWorkbook[] libro = new XSSFWorkbook[1];
        try {
            Sheet hoja = abrir(archivo, libro);
            boolean tienePorcentaje = false;
            boolean tieneCategoria = false;
            for (Row fila : hoja) {
                if ("Tiendita".equals(texto(hoja, fila.getRowNum(), 0))) {
                    tieneCategoria = true;
                    tienePorcentaje = texto(hoja, fila.getRowNum(), 2).contains("100.00");
                }
            }
            assertTrue(tieneCategoria);
            assertTrue(tienePorcentaje);
        } finally {
            libro[0].close();
        }
    }

    @Test
    void elBalanceAvisaCuandoLosEgresosSuperanALosIngresos(@TempDir Path carpeta) throws IOException {
        Path archivo = carpeta.resolve("balance.xlsx");
        excel.exportarBalance(balanceDePrueba("100", "250"), archivo);

        XSSFWorkbook[] libro = new XSSFWorkbook[1];
        try {
            Sheet hoja = abrir(archivo, libro);
            assertTrue(texto(hoja, 5, 0).startsWith("ATENCIÓN"));
        } finally {
            libro[0].close();
        }
    }

    @Test
    void exportaElReporteDeUnUsuarioConSusPagosYPendientes(@TempDir Path carpeta) throws IOException {
        Movimiento pago = new Movimiento(1, TipoMovimiento.INGRESO, LocalDate.of(2026, 11, 20), new BigDecimal("400"),
                "Pago mensual", "Pago mensual", "Ana Prueba", FormaPago.TRANSFERENCIA, false);
        Pendiente pendiente = new Pendiente(1, 1, "Ana Prueba", "Experiencia", LocalDate.of(2026, 12, 1),
                new BigDecimal("600"), EstadoPendiente.PENDIENTE, null, null);
        ReporteUsuario reporte = new ReporteUsuario("Ana Prueba", List.of(pago), List.of(pendiente));
        Path archivo = carpeta.resolve("usuario.xlsx");

        excel.exportarReporteUsuario(reporte, archivo);

        XSSFWorkbook[] libro = new XSSFWorkbook[1];
        try {
            Sheet hoja = abrir(archivo, libro);
            assertEquals("Reporte de Ana Prueba", texto(hoja, 0, 0));
            assertTrue(texto(hoja, 1, 1).contains("400.00"), "total pagado");
            assertTrue(texto(hoja, 2, 1).contains("600.00"), "total adeudado");
            boolean pagoEncontrado = false;
            boolean pendienteEncontrado = false;
            for (Row fila : hoja) {
                int n = fila.getRowNum();
                pagoEncontrado |= "Pago mensual".equals(texto(hoja, n, 1)) && "Transferencia".equals(texto(hoja, n, 3));
                pendienteEncontrado |= "Experiencia".equals(texto(hoja, n, 1)) && "Pendiente".equals(texto(hoja, n, 3));
            }
            assertTrue(pagoEncontrado);
            assertTrue(pendienteEncontrado);
        } finally {
            libro[0].close();
        }
    }

    @Test
    void elReporteDeUnUsuarioSinDatosSeExportaSinErrores(@TempDir Path carpeta) {
        Path archivo = carpeta.resolve("vacio.xlsx");
        excel.exportarReporteUsuario(new ReporteUsuario("Nadie Prueba", List.of(), List.of()), archivo);
        assertTrue(Files.exists(archivo));
    }
}
