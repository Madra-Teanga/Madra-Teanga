# Socrú Forbartha

## Réamhriachtanais

Tá an tionscadal seo tógtha le **ClojureDart**. Beidh ort na rudaí seo a leanas a bheith agat:

1.  **Clojure**: Lean an [treoir suiteála oifigiúil](https://clojure.org/guides/install_clojure).
2.  **Flutter**: Lean an [treoir suiteála Flutter](https://docs.flutter.dev/get-started/install).
3.  **ClojureDart**: Lean an [treoir socraithe ClojureDart](https://github.com/Tensegritics/ClojureDart/blob/main/doc/docs/01-getting-started.md).

## Tús a Chur Leis

1.  **Clónáil an stór**:
    ```bash
    git clone https://github.com/Madra-Teanga/Madra-Teanga.git
    cd Madra-Teanga
    ```

2.  **Suiteáil spleáchais**:
    ```bash
    flutter pub get
    ```

3.  **Rith an feidhmchlár**:
    ```bash
    clj -M:cljd flutter run
    ```
    Má tá tú ag rith an t-aip macOS, beidh ort `clj -M:cljd flutter` a úsáid ina ionad.

## Cumraíocht aipe dheisce macOS

### Suiteáil xcode tríd App Store

Má fhaigheann tú earráid cosúil leis seo: `xcrun: error: unable to find utility "xcodebuild", not a developer tool or in PATH`, rith:
```bash
sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
```

### Suiteáil CocoaPods

```bash
gem install cocoapods
```

Má tá tú ag úsáid `ruby` agus `gem` an córais, beidh ort é sin a rith le sudo. Is fearr `ruby` agus `gem` a shuiteáil le brew.

Áfach, má shuiteálann tú iad le brew, beidh ort na rudaí seo a leanas a chur le d'athróg PATH:
- `/opt/homebrew/opt/ruby/bin`
- `/opt/homebrew/lib/ruby/gems/4.0.0/bin` nó cibé áit a bhfuil an comhad dénártha `pod` ar do chóras.
