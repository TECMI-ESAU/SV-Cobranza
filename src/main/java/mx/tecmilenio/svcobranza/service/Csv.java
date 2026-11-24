package mx.tecmilenio.svcobranza.service;

import java.util.ArrayList;
import java.util.List;

/** Lectura y escritura minima de CSV (separado por comas, campos entre comillas). */
final class Csv {

    private Csv() {
    }

    static String linea(List<String> campos) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < campos.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            String valor = campos.get(i) == null ? "" : campos.get(i);
            sb.append('"').append(valor.replace("\"", "\"\"")).append('"');
        }
        return sb.append('\n').toString();
    }

    /** Divide el texto en registros; respeta comillas, comas y saltos de linea dentro de un campo. */
    static List<List<String>> leer(String texto) {
        List<List<String>> registros = new ArrayList<>();
        List<String> campos = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean entreComillas = false;
        boolean hayContenido = false;
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (entreComillas) {
                if (c == '"') {
                    if (i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                        campo.append('"');
                        i++;
                    } else {
                        entreComillas = false;
                    }
                } else {
                    campo.append(c);
                }
            } else if (c == '"') {
                entreComillas = true;
                hayContenido = true;
            } else if (c == ',') {
                campos.add(campo.toString());
                campo.setLength(0);
                hayContenido = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < texto.length() && texto.charAt(i + 1) == '\n') {
                    i++;
                }
                if (hayContenido) {
                    campos.add(campo.toString());
                    registros.add(campos);
                }
                campos = new ArrayList<>();
                campo.setLength(0);
                hayContenido = false;
            } else {
                campo.append(c);
                hayContenido = true;
            }
        }
        if (hayContenido) {
            campos.add(campo.toString());
            registros.add(campos);
        }
        return registros;
    }
}
