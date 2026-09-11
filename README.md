# ABODI DEX

A native Android APK/DEX analysis application designed for authorized analysis of APK/DEX files.

## Included in this revision
- Dark RTL interface inspired by the reference workflow.
- APK picker through Android Storage Access Framework.
- Real DEX parsing with dexlib2.
- String Pool indexing.
- String XREF indexing.
- Method-call and field-reference XREF indexing.
- Class and method explorers.
- Bytecode explorer with instruction addresses.
- Search across hits, methods, classes and XREF targets.
- App name: ABODI DEX.

## Build in Termux

```bash
pkg update -y
pkg install openjdk-17 gradle unzip -y
unzip abodi_dex_project.zip
cd abodi_dex
gradle assembleDebug
```

APK output:

`app/build/outputs/apk/debug/app-debug.apk`

The analyzer is intended for APK/DEX files you are authorized to inspect. It does not implement license bypassing or protection removal.
