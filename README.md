# SV-Cobranza

Sistema de cobranza y control financiero de escritorio, desarrollado en Java para una asociacion civil. Sustituye la bitacora en papel y el Excel por un registro unico de ingresos, egresos, pagos pendientes, balance y respaldo.

> Proyecto integrador del certificado Desarrollo de Software con Java (Tecmilenio). Los datos del repositorio son ficticios.

## Estado

En desarrollo (Fase 3: ejecucion). El README completo se entrega en la Fase 4.

## Tecnologias

Java 17, Maven, Swing, H2 embebida (cifrada), Apache POI, JUnit 5 y GitHub Actions.

## Compilar y empaquetar

```powershell
mvn test                                  # compila y corre las pruebas
powershell -ExecutionPolicy Bypass -File .\empaquetar.ps1
```

El segundo comando genera `dist\SV-Cobranza\SV-Cobranza.exe` con Java incluido (requiere JDK 17+ con `jpackage`). Los datos se guardan en `~\SV-Cobranza-datos`.
